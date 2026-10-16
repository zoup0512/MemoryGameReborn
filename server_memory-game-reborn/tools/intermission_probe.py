#!/usr/bin/env python3
"""Verify the practice round intermission on a deployed PK server.

Plays one practice (vs bot) match as a verification guest and measures what
the client cannot observe locally:

  - when round_end arrives relative to the gameplay deadline
    (round_start + durationMs; the old engine only ended the round after the
    round-grace expiry, i.e. ~10s later)
  - the announced intermission (nextRoundStartsAt - serverNow), which must
    match pk.timing.practice-round-intermission-ms for PRACTICE rooms
  - the observed gap between round_end and the next round_start
  - that the new round_end fields are present and the round advances

The guest identity is fixed by default so repeated runs reuse one
verification account instead of scattering guests; POST /auth/guest is
idempotent per installationId + guestSecret. A 401/403/409 answer means the
installation id is already taken with a different secret — pass a fresh
PK_PROBE_INSTALLATION (and secret) to mint another verification guest.

Usage:
  python intermission_probe.py [--base https://host] [--ws wss://host/ws]
                               [--difficulty BRONZE]

Environment overrides: PK_PROBE_BASE, PK_PROBE_WS, PK_PROBE_INSTALLATION,
PK_PROBE_SECRET, PK_PROBE_DIFFICULTY.

Needs the websocket-client package (import name `websocket`).
"""
import argparse
import json
import os
import ssl
import time
import urllib.request

import websocket

# A proxy would silently redirect the probe away from the target box.
for _var in ("HTTP_PROXY", "HTTPS_PROXY", "http_proxy", "https_proxy"):
    os.environ.pop(_var, None)


def create_guest(base):
    body = json.dumps({
        "installationId": os.environ.get("PK_PROBE_INSTALLATION",
                                         "probe-intermission-20261016"),
        # Fixed 256-bit probe secret; paired with the installation id above it
        # identifies the one long-lived verification guest.
        "guestSecret": os.environ.get("PK_PROBE_SECRET",
                                      "0123456789abcdef" * 4),
        "deviceName": "intermission probe",
    }).encode()
    request = urllib.request.Request(base + "/api/v2/auth/guest", data=body,
                                     headers={"Content-Type": "application/json"},
                                     method="POST")
    try:
        with urllib.request.urlopen(request, timeout=20,
                                    context=ssl.create_default_context()) as response:
            return json.loads(response.read())
    except urllib.error.HTTPError as error:
        raise SystemExit(
            "guest creation rejected (%s); the installation id is already bound "
            "to another secret — pass PK_PROBE_INSTALLATION/PK_PROBE_SECRET to "
            "use a different verification guest" % error.code)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default=os.environ.get("PK_PROBE_BASE",
                                                         "https://www.razor123.site"))
    parser.add_argument("--ws", default=os.environ.get("PK_PROBE_WS",
                                                       "wss://www.razor123.site/ws"))
    parser.add_argument("--difficulty", default=os.environ.get("PK_PROBE_DIFFICULTY", "BRONZE"))
    options = parser.parse_args()

    guest = create_guest(options.base)
    print("verification guest userId=%s type=%s"
          % (guest["user"]["userId"], guest["user"]["accountType"]))

    connection = websocket.create_connection(options.ws, timeout=20)
    connection.settimeout(None)
    connection.send(json.dumps({"v": 1, "t": "auth",
                                "d": {"token": guest["accessToken"]}}))

    def frame(want, timeout=120.0):
        deadline = time.time() + timeout
        connection.settimeout(2)
        while time.time() < deadline:
            try:
                message = connection.recv()
            except websocket.WebSocketTimeoutException:
                continue
            data = json.loads(message)
            if data.get("t") == want:
                return data.get("d") or {}, time.time()
            if data.get("t") == "error":
                raise SystemExit("server error: %s" % data.get("d"))
        raise SystemExit("timeout waiting %s" % want)

    def send(kind, payload=None):
        connection.send(json.dumps({"v": 1, "t": kind, "d": payload or {}}))

    profile, _ = frame("auth_ok")
    print("auth ok: userId=%s balance=%s" % (profile.get("userId"), profile.get("balance")))

    send("bot_match_request", {"difficulty": options.difficulty})
    found, _ = frame("match_found")
    print("match mode=%s vs %s tier=%s" % (found["mode"], found["opponent"]["nickname"],
                                           found["opponent"].get("tier")))
    send("match_start")
    frame("opponent_start")
    start, t_start = frame("round_start")
    duration = start["durationMs"]
    print("round 1 game=%s durationMs=%d" % (start["gameId"], duration))

    # Finish the player's side early: the round end must then depend on the
    # bot completing at the gameplay deadline, not on the player's message.
    for level in range(1, 4):
        send("level_win", {"round": start["round"], "level": level, "score": 0})
        time.sleep(0.25)
    send("round_complete", {"round": start["round"], "score": 3})
    print("player reported complete at %.2fs into the round" % (time.time() - t_start))

    end, t_end = frame("round_end")
    deadline = t_start + duration / 1000.0
    print("round_end arrived %.2fs after the gameplay deadline" % (t_end - deadline))
    print("  fields: nextRoundStartsAt=%s serverNow=%s nextGameId=%s"
          % (end.get("nextRoundStartsAt"), end.get("serverNow"), end.get("nextGameId")))
    print("  announced intermission = %d ms"
          % ((end.get("nextRoundStartsAt") or 0) - (end.get("serverNow") or 0)))

    start2, t_start2 = frame("round_start")
    print("round 2 started %.2fs after round_end (game=%s durationMs=%s)"
          % (t_start2 - t_end, start2["gameId"], start2.get("durationMs")))

    send("resign")
    settled, _ = frame("match_end", timeout=60)
    print("resigned cleanly: result=%s scores=%s:%s"
          % (settled["result"], settled["myScore"], settled["oppScore"]))
    connection.close()
    print("PROBE DONE")


if __name__ == "__main__":
    main()
