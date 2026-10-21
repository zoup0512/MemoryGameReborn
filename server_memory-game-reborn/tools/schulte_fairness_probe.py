#!/usr/bin/env python3
"""Production smoke: reuses the intermission verification guest; writes practice history.

Checks negotiated preparation, reconnect, stale/duplicate ready, official clocks,
finite Schulte scores (if selected), and a legacy match_start without capability.
Never prints credentials. Does not alter configuration or other users' matches.
"""
import argparse
import json
import time

import websocket
from intermission_probe import create_guest


class Probe:
    def __init__(self, url, token):
        self.url = url
        self.token = token
        self.ws = None
        self.match_id = None
        self.connect()

    def connect(self):
        if self.ws is not None:
            self.ws.close()
        self.ws = websocket.create_connection(self.url, timeout=20)
        self.send("auth", {"token": self.token})
        self.frame("auth_ok", 20)

    def send(self, kind, data=None):
        self.ws.send(json.dumps({"v": 1, "t": kind, "d": data or {}}))

    def receive(self, timeout):
        self.ws.settimeout(timeout)
        message = self.ws.recv()
        if not message:
            raise RuntimeError("WebSocket closed unexpectedly")
        frame = json.loads(message)
        if frame.get("t") == "error":
            raise RuntimeError("Server rejected probe command: code=%s" % frame.get("d", {}).get("code"))
        return frame

    def frame(self, kind, timeout=120):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            try:
                message = self.receive(min(2, max(0.01, deadline - time.monotonic())))
            except websocket.WebSocketTimeoutException:
                continue
            if message.get("t") == kind:
                return message.get("d") or {}
        raise AssertionError("Timed out waiting for " + kind)

    def quiet(self, seconds, forbidden):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            try:
                message = self.receive(min(1, max(0.01, deadline - time.monotonic())))
            except websocket.WebSocketTimeoutException:
                continue
            assert message.get("t") not in forbidden, "Unexpected " + message.get("t", "frame")

    def request_match(self):
        self.send("bot_match_request", {"difficulty": "BRONZE"})
        found = self.frame("match_found", 20)
        assert found["mode"] == "PRACTICE" and found["opponent"]["isBot"]
        self.match_id = found["matchId"]
        return found

    def resign(self):
        if self.match_id is None:
            return
        self.send("resign")
        end = self.frame("match_end", 30)
        print("probe resigned match=%s result=%s" % (self.match_id, end["result"]), flush=True)
        self.match_id = None

    def negotiated(self):
        found = self.request_match()
        print("negotiated match=%s plan=%s" % (self.match_id, [r["gameId"] for r in found["rounds"]]), flush=True)
        self.send("match_start", {"prepareRounds": True})
        previous_score = 0
        schulte = False
        for number, planned in enumerate(found["rounds"], 1):
            prep = self.frame("round_start", 20)
            assert prep.get("preparing") is True and prep["round"] == number
            assert prep["matchId"] == self.match_id and prep["gameId"] == planned["gameId"]
            if number == 1:
                self.connect()
                replay = self.frame("round_start", 20)
                assert replay.get("preparing") is True and replay["round"] == number
                self.send("round_ready", {"matchId": "stale-probe-match", "round": number})
                self.send("round_ready", {"matchId": self.match_id, "round": number + 1})
            self.quiet(8 if number == 1 else 3, {"opponent_score", "round_start", "round_end", "match_end"})
            self.send("round_ready", {"matchId": self.match_id, "round": number})
            start = self.frame("round_start", 20)
            started = time.monotonic()
            assert not start.get("preparing") and start["round"] == number
            assert start["durationMs"] == planned["durationMs"]
            assert start["gameId"] == planned["gameId"]
            self.send("round_ready", {"matchId": self.match_id, "round": number})
            self.send("round_complete", {"round": number, "score": 0})
            self.quiet(1, {"round_start", "round_end", "match_end"})
            end = self.frame("round_end", planned["durationMs"] / 1000 + 20)
            elapsed = time.monotonic() - started
            assert end["round"] == number
            assert planned["durationMs"] / 1000 - 2 <= elapsed <= planned["durationMs"] / 1000 + 10
            delta = end["oppScore"] - previous_score
            if planned["gameId"] == "22":
                schulte = True
                assert 0 <= delta <= 2352
            previous_score = end["oppScore"]
            if number < len(found["rounds"]):
                assert 0 < end["nextRoundStartsAt"] - end["serverNow"] <= 2000
            print("round=%s game=%s preparation=quiet official_duration=%sms observed=%.2fs bot_round_score=%s" %
                  (number, planned["gameId"], start["durationMs"], elapsed, delta), flush=True)
        settled = self.frame("match_end", 30)
        assert settled["eloDelta"] == 0 and settled["coinDelta"] == 0
        print("negotiated settled match=%s result=%s schulte_seen=%s" %
              (self.match_id, settled["result"], schulte), flush=True)
        self.match_id = None
        return schulte

    def legacy(self):
        self.request_match()
        self.send("match_start")
        start = self.frame("round_start", 20)
        assert not start.get("preparing")
        print("legacy formal start OK match=%s game=%s" % (self.match_id, start["gameId"]), flush=True)
        self.resign()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="https://www.razor123.site")
    parser.add_argument("--ws", default="wss://www.razor123.site/ws")
    args = parser.parse_args()
    guest = create_guest(args.base)
    print("verification guest userId=%s" % guest["user"]["userId"], flush=True)
    probe = Probe(args.ws, guest["accessToken"])
    try:
        schulte_seen = probe.negotiated()
        probe.legacy()
        print("PROBE PASS; schulte_live_round=%s" % schulte_seen, flush=True)
    finally:
        try:
            probe.resign()
        finally:
            probe.ws.close()


if __name__ == "__main__":
    main()
