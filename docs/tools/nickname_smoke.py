"""Verify fail-closed nickname behavior on a disposable loopback server.

Run without external moderation credentials; no submitted nickname is sent to a
third party. Requires Python 3 and websocket-client. Never prints credentials.
"""
import argparse
from contextlib import closing
import json
import re
import secrets
import urllib.error
import urllib.parse
import urllib.request


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:18081")
    options = parser.parse_args()
    base = options.base_url.rstrip("/")
    if urllib.parse.urlparse(base).hostname not in ("127.0.0.1", "localhost", "::1"):
        parser.error("Only disposable loopback servers are permitted")
    urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))
    import websocket
    checks = []

    def request(method, path, body=None, token=None, expected=200):
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        data = None if body is None else json.dumps(body).encode("utf-8")
        req = urllib.request.Request(base + "/api/v2" + path, data, headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=20) as response:
                status, raw = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, raw = error.code, error.read()
        result = json.loads(raw) if raw else {}
        allowed = (expected,) if isinstance(expected, int) else expected
        assert status in allowed, f"{method} {path}: unexpected status {status}, code {result.get('code')}"
        return result

    identity = {"installationId": "nickname-smoke-" + secrets.token_hex(12),
                "guestSecret": secrets.token_hex(32), "deviceName": "Nickname acceptance fixture"}
    guest = request("POST", "/auth/guest", identity, expected=(200, 201))
    token, user = guest["accessToken"], guest["user"]
    nickname = user["nickname"]
    assert 2 <= len(nickname) <= 16 and not re.fullmatch(r"Player\d*", nickname, re.I)
    assert user["nextNicknameChangeAt"] == 0
    recovered = request("POST", "/auth/guest", dict(identity, nickname="DoNotOverwrite"))
    assert recovered["user"]["userId"] == user["userId"]
    assert recovered["user"]["nickname"] == nickname
    token = recovered["accessToken"]
    checks.append("safe default persists across guest recovery without accepting a replacement")

    for invalid in ("", "a", "x" * 17, "昵称\u200b", "昵称\n文字"):
        result = request("PATCH", "/me/profile", {"nickname": invalid}, token, expected=400)
        assert result["code"] == "INVALID_NICKNAME"
    checks.append("invalid length, zero-width and control characters are rejected")

    denied = request("PATCH", "/me/profile", {"nickname": "CustomNickname", "avatarValue": "avatar_2"},
                     token, expected=503)
    assert denied["code"] == "NICKNAME_MODERATION_UNAVAILABLE"
    unchanged = request("GET", "/me", token=token)
    assert unchanged["nickname"] == nickname and unchanged["avatarValue"] == user["avatarValue"]
    assert unchanged["nextNicknameChangeAt"] == 0
    checks.append("missing moderation blocks rename atomically without consuming cooldown")

    for avatar in ("avatar_2", "avatar_12"):
        saved = request("PATCH", "/me/profile", {"nickname": nickname, "avatarValue": avatar}, token)
        assert saved["nickname"] == nickname and saved["avatarValue"] == avatar
    invalid_avatar = request("PATCH", "/me/profile", {"avatarValue": "https://example.com/avatar.png"},
                             token, expected=400)
    assert invalid_avatar["code"] == "INVALID_AVATAR"
    assert request("GET", "/me", token=token)["avatarValue"] == "avatar_12"
    checks.append("preset avatars save without moderation; custom URLs are rejected")

    ws_url = base.replace("http://", "ws://", 1).replace("https://", "wss://", 1) + "/ws"
    legacy_uuid = "nickname-legacy-" + secrets.token_hex(12)
    with closing(websocket.create_connection(ws_url, timeout=10)) as connection:
        connection.send(json.dumps({"v": 1, "t": "register", "d": {"uuid": legacy_uuid}}))
        registered = json.loads(connection.recv())
        assert registered["t"] == "auth_ok", "Legacy safe-default registration failed"
        legacy_user = registered["d"]
    with closing(websocket.create_connection(ws_url, timeout=10)) as connection:
        connection.send(json.dumps({"v": 1, "t": "register", "d": {
            "uuid": legacy_uuid, "nickname": "DoNotOverwrite"}}))
        restored = json.loads(connection.recv())
        assert restored["t"] == "auth_ok"
        assert restored["d"]["userId"] == legacy_user["userId"]
        assert restored["d"]["nickname"] == legacy_user["nickname"]
    checks.append("legacy registration cannot overwrite an existing nickname")
    with closing(websocket.create_connection(ws_url, timeout=10)) as connection:
        connection.send(json.dumps({"v": 1, "t": "register", "d": {
            "uuid": "nickname-custom-" + secrets.token_hex(12), "nickname": "CustomNickname"}}))
        blocked = json.loads(connection.recv())
        assert blocked["t"] == "error" and blocked["d"]["code"] == 503
    checks.append("new legacy custom nickname cannot bypass unavailable moderation")
    print(json.dumps({"ok": True, "checks": checks}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
