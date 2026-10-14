"""Verify the seeded legacy user's REST/WS migration on a disposable local server."""
import argparse
from contextlib import closing
import json
import secrets
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
import websocket


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:18082")
    parser.add_argument("--fixture", required=True)
    options = parser.parse_args()
    parsed = urllib.parse.urlparse(options.base_url)
    if parsed.hostname not in ("localhost", "127.0.0.1", "::1"):
        parser.error("Only loopback fixture servers are permitted")
    fixture = Path(options.fixture)
    if "account-migration-fixture" not in fixture.as_posix():
        parser.error("Only disposable account-migration-fixture credentials are permitted")
    saved = json.loads(fixture.read_text(encoding="utf-8"))
    urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))
    base = options.base_url.rstrip("/")

    def rest(path, body, expected=200):
        req = urllib.request.Request(base + "/api/v2" + path, json.dumps(body).encode(),
                                     {"Content-Type": "application/json"}, method="POST")
        try:
            with urllib.request.urlopen(req, timeout=15) as response:
                status, content = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, content = error.code, error.read()
        result = json.loads(content)
        assert status == expected, f"Unexpected status {status} for {path}"
        return result

    device = {"installationId": "migration-" + secrets.token_hex(12), "guestSecret": secrets.token_hex(32),
              "deviceName": "Legacy migration fixture"}
    result = rest("/auth/migrate", dict(device, legacyToken=saved["legacyToken"]))
    user = result["user"]
    assert (user["userId"], user["balance"], user["elo"], user["wins"], user["losses"], user["draws"]) == (91001, 321, 1450, 7, 3, 1)
    ws_url = base.replace("http://", "ws://", 1).replace("https://", "wss://", 1) + "/ws"
    with closing(websocket.create_connection(ws_url, timeout=8)) as connection:
        connection.send(json.dumps({"v": 1, "t": "auth", "d": {"token": result["accessToken"]}}))
        auth = json.loads(connection.recv())
        assert auth["t"] == "auth_ok" and auth["d"]["userId"] == 91001
        assert auth["d"]["token"] == result["accessToken"]
        connection.send(json.dumps({"v": 1, "t": "history_request", "d": {}}))
        history = json.loads(connection.recv())
        assert history["t"] == "history_response"
        assert history["d"]["matches"][0]["matchId"] == "legacy-fixture-match"
        assert history["d"]["matches"][0]["opponentUserId"] == -1
    with closing(websocket.create_connection(ws_url, timeout=8)) as connection:
        connection.send(json.dumps({"v": 1, "t": "auth", "d": {"token": saved["legacyToken"]}}))
        denied = json.loads(connection.recv())
        assert denied["t"] == "error" and denied["d"]["code"] == 401
    with closing(websocket.create_connection(ws_url, timeout=8)) as connection:
        connection.send(json.dumps({"v": 1, "t": "register", "d": {"uuid": saved["uuid"], "nickname": "Hijack"}}))
        denied = json.loads(connection.recv())
        assert denied["t"] == "error" and denied["d"]["code"] in (401, 403, 409)
    recovered = rest("/auth/guest", dict(device, nickname="NotRenamed"))
    assert recovered["user"]["userId"] == 91001 and recovered["user"]["balance"] == 321
    assert recovered["user"]["nickname"] == "LegacyFixture"
    print(json.dumps({"ok": True, "checks": ["legacy userId/assets/statistics retained", "WS history retained",
          "legacy token revoked", "UUID-only recovery denied", "guest-secret retry recovers migrated identity"]}, indent=2))


if __name__ == "__main__":
    main()
