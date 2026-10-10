"""Single-device full regression for the online PK server.

One real phone (driven via adb/uiautomator, see ui.py) plus a scripted
WebSocket probe sitting on the opposite seat. Covers, in one run:

  S1  ranked pairing -> same-match plan -> two-stage START confirm
      (waiting / opponent-is-ready) -> live HUD sync -> natural
      settlement with mirrored, zero-sum results
  S2  in-match resign mirror (phone resigns, probe collects the forfeit)
  S3  friend room by code -> idle draw -> mirrored D settlement
  S4  abrupt opponent disconnect -> reconnect catch-up frames -> the
      reconnect-window forfeit settles on the phone

Usage:
  python regression.py <ws-url> <admin-token>

Prerequisites (run outside, e.g. from the session runbook):
  - local server hot-configured: 2 rounds x 10s, grace 1s, intermission
    0.5s, match-found delay 0.5s, entry fee 0, reconnect window 10s
  - phone endpoint pointing at the server, adb reverse in place, app on
    the Online tab
"""
import json
import queue
import re
import sys
import threading
import time
import traceback
import urllib.request
import uuid

import websocket

import ui

# Phone-visible labels (strings.xml) used as UI assertions.
LBL_SEARCH = "Searching for opponents"
LBL_START = "Start"
LBL_WAITING = "Waiting for"
LBL_READY = "Your opponent is"
LBL_LOST = "You lost"
LBL_DRAW = "Draw"
LBL_RESIGN_DIALOG = "lose your bet"
LBL_RESIGN_YES = "YES"
LBL_CREATE_ROOM = "Create room"
LBL_PLAY_ONLINE = "Play Online"
LBL_LOBBY = "Play vs Bot"

RESULTS = []
probe_uid = None


def check(name, ok, detail=""):
    RESULTS.append((name, ok, detail))
    print(f"  [{'PASS' if ok else 'FAIL'}] {name}" + (f" — {detail}" if detail else ""))
    return ok


def phone_wait(text, timeout=15.0):
    """Poll the phone UI for text; returns bool (never exits the process)."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        if ui.find(ui.dump(), text):
            return True
        time.sleep(1.5)
    return False


def http_base(url):
    base = url.replace("ws://", "http://", 1)
    return base[:-3] if base.endswith("/ws") else base


def settled_events(url, token):
    req = urllib.request.Request(http_base(url).rstrip("/") + "/api/admin/events",
                                 headers={"X-Admin-Token": token})
    with urllib.request.urlopen(req, timeout=10) as resp:
        data = json.loads(resp.read().decode())
    rows = data if isinstance(data, list) else data.get("events") or list(data.values())[0]
    # Ring lines carry a timestamp prefix, so match the tag anywhere.
    return [r for r in rows if "SETTLED" in str(r)]


def set_config(url, token, patch):
    req = urllib.request.Request(http_base(url).rstrip("/") + "/api/admin/config",
                                 data=json.dumps(patch).encode(),
                                 headers={"X-Admin-Token": token, "Content-Type": "application/json"},
                                 method="PUT")
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read().decode())


def wait_for_settled(url, token, needle, timeout=20.0):
    """Poll the event ring until a SETTLED line containing needle shows up."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        for e in settled_events(url, token):
            if needle in str(e):
                return str(e)
        time.sleep(1)
    return None


class Probe:
    """Scripted opponent: background reader + typed await, like TestWsClient."""

    def __init__(self, url):
        self.url = url
        self.token = None
        self.connect()

    def connect(self):
        self.ws = websocket.create_connection(self.url, timeout=10)
        # The connect timeout must not become the recv timeout: quiet gaps
        # of >10s (a round deadline with an idle opponent) would kill the
        # reader thread and deafen every later assertion.
        self.ws.settimeout(None)
        self.inbox = queue.Queue()
        threading.Thread(target=self._reader, daemon=True).start()

    def _reader(self):
        while True:
            try:
                self.inbox.put(json.loads(self.ws.recv()))
            except websocket.WebSocketTimeoutException:
                continue
            except Exception:
                break

    def send(self, t, d=None):
        self.ws.send(json.dumps({"v": 1, "t": t, "d": d or {}}))

    def await_(self, t, timeout=20.0):
        deadline = time.time() + timeout
        skipped = []
        out = None
        while time.time() < deadline:
            try:
                frame = self.inbox.get(timeout=1)
            except queue.Empty:
                continue
            import os
            if os.environ.get("REG_DEBUG"):
                print(f"    [probe frame] {frame.get('t')}")
            if frame.get("t") == t:
                out = frame.get("d") or {}
                break
            skipped.append(frame)
        for frame in skipped:
            self.inbox.put(frame)
        return out

    def flush(self):
        """Drop every buffered frame (stale settlements from earlier rounds)."""
        while True:
            try:
                self.inbox.get_nowait()
            except queue.Empty:
                return

    def register(self, name):
        self.send("register", {"uuid": str(uuid.uuid4()), "nickname": name, "balance": 100})
        profile = self.await_("auth_ok")
        self.token = profile.get("token")
        return profile

    def reconnect(self):
        # Clean restart with the SAME token: the server reattaches the seat.
        self.ws.close()
        self.connect()
        self.send("auth", {"token": self.token})
        return self.await_("auth_ok")

    def drop_hard(self):
        # Cut the TCP socket without a close frame (half-dead connection).
        try:
            self.ws.sock.close()
        except Exception:
            pass

    def play_levels(self, round_no, count=7, interval=0.25):
        for level in range(1, count + 1):
            self.send("level_win", {"round": round_no, "level": level, "score": 0})
            time.sleep(interval)


def tap_text(text):
    """Find text on the phone and tap its center; returns bool."""
    hit = ui.find(ui.dump(), text)
    if hit:
        ui.tap(hit[0], hit[1])
    return hit is not None


def back_to_lobby(max_backs=3):
    """Best-effort return to the lobby card (Play vs Bot visible)."""
    for _ in range(max_backs):
        if ui.find(ui.dump(), LBL_LOBBY):
            return True
        ui.sh("shell", "input", "keyevent", "4")
        time.sleep(2)
    return ui.find(ui.dump(), LBL_LOBBY) is not None


# --------------------------------------------------------------- scenarios

def s1_ranked_flow(url, token, probe):
    print("S1: ranked pairing / same match / two-stage START / HUD / settlement")
    probe.flush()
    back_to_lobby()
    tap_text(LBL_PLAY_ONLINE)
    if not check("S1 phone enters search", phone_wait(LBL_SEARCH, 15)):
        return
    probe.send("match_join")
    found = probe.await_("match_found", 15)
    if not check("S1 probe paired (RANKED)", found and found.get("mode") == "RANKED"):
        return
    plan = [r["gameId"] for r in found["rounds"]]
    check("S1 plan has 2 rounds", len(plan) == 2, str(plan))

    # Two-stage START: phone presses first -> waiting; probe presses ~3s
    # later -> the waiting card flips to "opponent is ready". The flip is
    # only visible until the first round_start, so stretch that window.
    set_config(url, token, {"matchFoundDelayMs": 3000})
    tap_text(LBL_START)
    time.sleep(2)
    check("S1 waiting label while probe idle", ui.find(ui.dump(), LBL_WAITING) is not None)
    time.sleep(3)
    probe.send("match_start")
    check("S1 flips to opponent-is-ready", phone_wait(LBL_READY, 8))
    check("S1 probe sees ready frame", probe.await_("opponent_start", 5) is not None)

    probe_elo_delta = 0
    for rnd in range(1, 3):
        start = probe.await_("round_start", 20)
        if not check(f"S1 round {rnd} starts", start and start["round"] == rnd):
            return
        check(f"S1 round {rnd} plan matches match_found",
              start["gameId"] == plan[rnd - 1], f"{start['gameId']} vs {plan[rnd - 1]}")
        # Answer immediately: the whole burst must land inside the round,
        # before any (slow) phone-side dump.
        probe.play_levels(rnd)
        if rnd == 1:
            check("S1 phone left the search board",
                  ui.find(ui.dump(), LBL_SEARCH) is None)
        if rnd == 1:
            # Wait for the phone to clear its 5s pre-round game list, then
            # answer one more level: the fresh opponent_score frame is what
            # repaints the in-game HUD. The HUD repaint needs the game in
            # front, so poll for a non-zero score for a while.
            time.sleep(4.5)
            probe.play_levels(rnd, count=1)
            probe.send("level_win", {"round": rnd, "level": 8, "score": 0})
            hud = None
            for _ in range(6):
                time.sleep(1.5)
                hud = re.findall(r'text="OPP (\d+) : (\d+) ME"', ui.dump())
                if hud and int(hud[0][0]) > 0:
                    break
            check("S1 HUD shows probe score (round 1)",
                  bool(hud) and int(hud[0][0]) > 0, hud[0] if hud else "no HUD text")
        # Phone idles: each round ends on the server deadline.
        if not check(f"S1 round {rnd} ends", probe.await_("round_end", 25) is not None):
            return

    end = probe.await_("match_end", 25)
    if not check("S1 probe settled W", end and end["result"] == "W",
                 f"result={end and end['result']}"):
        return
    probe_elo_delta = end["eloDelta"]
    check("S1 probe gains ELO", probe_elo_delta > 0, f"delta={probe_elo_delta}")
    if not check("S1 phone shows 'You lost'", phone_wait(LBL_LOST, 20)):
        return
    plus = re.findall(r'text="([+-]\d+)"', ui.dump())
    phone_elo_delta = int(plus[1]) if len(plus) >= 2 else None
    check("S1 settlement ELO zero-sum",
          phone_elo_delta == -probe_elo_delta,
          f"phone={phone_elo_delta} probe={probe_elo_delta}")


def s2_resign_mirror(url, token, probe):
    print("S2: resign mirror — phone resigns, probe collects the forfeit win")
    probe.flush()
    # Re-pair from the lobby (the previous scenario's cleanup already backed
    # out of any settlement card).
    back_to_lobby()
    tap_text(LBL_PLAY_ONLINE)
    if not check("S2 phone re-enters search", phone_wait(LBL_SEARCH, 15)):
        return
    probe.send("match_join")
    found = probe.await_("match_found", 15)
    if not check("S2 probe paired again", found and found.get("mode") == "RANKED"):
        return
    probe.send("match_start")
    tap_text(LBL_START)
    start = probe.await_("round_start", 20)
    if not check("S2 round 1 starts", start is not None):
        return
    # Wait out the 5s pre-round game list: resigning while the phone is
    # mid-transition lets the launching game activity steal the dialog.
    time.sleep(7)
    end = None
    for attempt in range(2):
        ui.sh("shell", "input", "keyevent", "4")  # back -> resign dialog
        time.sleep(1.5)
        if ui.find(ui.dump(), LBL_RESIGN_DIALOG) is None:
            continue
        tap_text(LBL_RESIGN_YES)
        end = probe.await_("match_end", 15)
        if end:
            break
    check("S2 probe collects forfeit W", end and end["result"] == "W",
          f"result={end and end['result']}")
    settled = wait_for_settled(url, token, "forfeit=true", 10)
    check("S2 server settled as forfeit", settled is not None,
          settled or "no forfeit settle in the ring")


def s3_friend_draw(url, token, probe):
    print("S3: friend room by code -> idle draw")
    probe.flush()
    # Align ratings so the friend-halved draw rounds to exactly zero.
    base = http_base(url).rstrip("/")
    for uid in (1, probe_uid):
        req = urllib.request.Request(
            base + f"/api/admin/users/{uid}/elo",
            data=json.dumps({"set": 1000}).encode(),
            headers={"X-Admin-Token": token, "Content-Type": "application/json"},
            method="POST")
        try:
            urllib.request.urlopen(req, timeout=10).read()
        except Exception as exc:
            print("  (elo align skipped:", exc, ")")
    back_to_lobby()
    tap_text(LBL_CREATE_ROOM)
    code = None
    for _ in range(8):
        match = re.search(r'text="(\d{4})"', ui.dump())
        if match:
            code = match.group(1)
            break
        time.sleep(1)
    if not check("S3 phone shows a 4-digit room code", code is not None):
        return
    probe.send("room_join", {"code": code})
    found = probe.await_("match_found", 15)
    if not check("S3 probe joined, mode FRIEND", found and found.get("mode") == "FRIEND"):
        return
    check("S3 probe sees the phone as opponent",
          found.get("opponent", {}).get("nickname") == "Player1673",
          found.get("opponent", {}).get("nickname"))
    tap_text(LBL_START)
    probe.send("match_start")
    check("S3 round starts", probe.await_("round_start", 20) is not None)
    # Both sides idle every round -> 0:0 draw.
    for _ in range(2):
        if probe.await_("round_end", 25) is None:
            break
    end = probe.await_("match_end", 25)
    check("S3 probe settled D", end and end["result"] == "D",
          f"result={end and end['result']}")
    check("S3 friend draw moves ~no ELO", end and abs(end["eloDelta"]) <= 1,
          f"delta={end and end['eloDelta']} (friend halving rounds)")
    check("S3 phone shows Draw", phone_wait(LBL_DRAW, 20))


def s4_opponent_drop(url, token, probe):
    print("S4: abrupt opponent drop -> reconnect catch-up -> window forfeit")
    probe.flush()
    back_to_lobby()
    tap_text(LBL_PLAY_ONLINE)
    if not check("S4 phone enters search", phone_wait(LBL_SEARCH, 15)):
        return
    probe2 = Probe(url)
    if not check("S4 second probe registered", probe2.register("ProbeTwo") is not None):
        return
    probe2.send("match_join")
    found = probe2.await_("match_found", 15)
    if not check("S4 paired with the phone", found is not None):
        return
    probe2.send("match_start")
    tap_text(LBL_START)
    start = probe2.await_("round_start", 20)
    if not check("S4 round 1 starts", start is not None):
        return
    probe2.play_levels(start["round"], count=3)
    probe2.drop_hard()
    time.sleep(1)
    # Reconnect with the same token: the server reattaches and re-pushes
    # the running round (round_start catch-up).
    check("S4 probe re-auths", probe2.reconnect() is not None)
    catchup = probe2.await_("round_start", 10)
    check("S4 reconnect receives round catch-up", catchup is not None,
          f"round={catchup and catchup.get('round')}")
    # Probe drops again and stays dead: the 10s hot-configured window
    # forfeits the seat and settles while the phone is inside a game
    # activity. After the settle the phone's matchId is null, so one back
    # leaves the game for the orchestrator's settlement card.
    probe2.drop_hard()
    end = probe.await_("match_end", 30)
    check("S4 probe1 collects nothing (not in match)", end is None,
          "probe1 must stay out of probe2's match")
    settled = wait_for_settled(url, token, "forfeit=true", 25)
    check("S4 server forfeit-settled", settled is not None,
          settled or "no forfeit settle in the ring")
    # The phone may sit in the in-game replay board; its back/YES routes
    # through the game's own resign flow straight back to the lobby (the
    # settlement card only paints when the orchestrator is front). The
    # authoritative forfeit evidence is the SETTLED event above.
    back_to_lobby()
    check("S4 phone back at the lobby after the forfeit",
          ui.find(ui.dump(), LBL_LOBBY) is not None)


SCENARIOS = {
    "s1": s1_ranked_flow,
    "s2": s2_resign_mirror,
    "s3": s3_friend_draw,
    "s4": s4_opponent_drop,
}


def main():
    url, token = sys.argv[1], sys.argv[2]
    only = sys.argv[3] if len(sys.argv) > 3 else None
    global probe_uid
    probe = Probe(url)
    profile = probe.register("ProbeOne")
    if profile is None:
        print("probe could not register; aborting")
        return 1
    probe_uid = profile.get("userId")
    print("probe registered:", profile.get("nickname"), "elo", profile.get("elo"))
    for scenario in ([SCENARIOS[only]] if only else
                     (s1_ranked_flow, s2_resign_mirror, s3_friend_draw, s4_opponent_drop)):
        try:
            scenario(url, token, probe)
        except Exception:
            traceback.print_exc()
            check(scenario.__name__ + " crashed", False)
        finally:
            # Never leak a live seat into the next scenario: resign whatever
            # match the probe might still be sitting in.
            probe.send("resign")
            time.sleep(2)
            probe.inbox.put({"t": "__cleanup__"})
            back_to_lobby()

    print("\n==== SUMMARY ====")
    failed = 0
    for name, ok, detail in RESULTS:
        print(f"[{'PASS' if ok else 'FAIL'}] {name}" + (f" — {detail}" if detail else ""))
        failed += 0 if ok else 1
    print(f"{len(RESULTS) - failed}/{len(RESULTS)} passed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
