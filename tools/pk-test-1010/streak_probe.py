"""Wins N practice matches for the phone's account to build a streak.

Usage: python streak_probe.py <ws-url> <token> <matches>
"""
import json
import sys
import time

import websocket

URL, TOKEN, MATCHES = sys.argv[1], sys.argv[2], int(sys.argv[3])


def frame(ws, want, timeout=30.0):
    deadline = time.time() + timeout
    ws.settimeout(2)
    while time.time() < deadline:
        try:
            msg = ws.recv()
        except websocket.WebSocketTimeoutException:
            continue
        data = json.loads(msg)
        if data.get("t") == want:
            return data.get("d") or {}
        if data.get("t") == "error":
            print("server error:", data.get("d"))
            sys.exit(1)
    print("timeout waiting", want)
    sys.exit(2)


ws = websocket.create_connection(URL, timeout=10)
ws.send(json.dumps({"v": 1, "t": "auth", "d": {"token": TOKEN}}))
profile = frame(ws, "auth_ok")
print("auth ok: streak =", profile.get("practiceStreak"),
      "| tiers =", [(t["id"], t["elo"], t["winCoins"]) for t in profile.get("practiceTiers") or []])

for i in range(MATCHES):
    ws.send(json.dumps({"v": 1, "t": "bot_match_request", "d": {"difficulty": "BRONZE"}}))
    found = frame(ws, "match_found")
    print(f"match {i + 1}: mode={found['mode']} vs {found['opponent']['nickname']}"
          f" elo={found['opponent']['elo']} tier={found['opponent'].get('tier')}")
    ws.send(json.dumps({"v": 1, "t": "match_start", "d": {}}))
    frame(ws, "opponent_start")
    start = frame(ws, "round_start")
    game = start["gameId"]
    # Seven wins at 250ms cadence beat the weakened bronze bot every time.
    for level in range(1, 8):
        ws.send(json.dumps({"v": 1, "t": "level_win",
                            "d": {"round": start["round"], "level": level, "score": 0}}))
        time.sleep(0.25)
    ws.send(json.dumps({"v": 1, "t": "round_complete",
                        "d": {"round": start["round"], "score": 7}}))
    end = frame(ws, "match_end", timeout=60)
    print(f"  game={game} result={end['result']} coins={end['coinDelta']}"
          f" elo={end['elo']}({end['eloDelta']:+d}) streak={end['practiceStreak']}")

ws.close()
print("done")
