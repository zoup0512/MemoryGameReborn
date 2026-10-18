#!/usr/bin/env python3
"""Two-phone regression flows for the online PK round (R8 release build).

Wraps dual.py primitives into the interleaved two-device scenarios:

  preflight   both devices: package version, locale, screen, launch, dump
  ranked      queue both -> pair -> dual START -> 5 rounds -> settlement
  friend      D1 creates a room, D2 joins by code -> full match
  resign      ranked match; D1 resigns in round 1 -> forfeit mirror
  reconnect   friend match; D1 loses network mid-round -> catch-up -> continues
  practice    D1 plays a bot match (round cards + countdown)
  tabs        mode tabs on one device: tap/swipe, per-mode stats block
  stats       read and print per-mode stats on one device
  offline     single device: back to main menu, open a game, challenge, charts

Every step prints a PASS/FAIL line and saves screenshot+dump evidence under
build/test-round-20261016/evidence/<flow>/.

Usage: python dual_round.py <flow> [device-serial-for-single-device-flows]
"""
import os
import re
import sys
import time

import dual

D1 = os.environ.get("DEV1", "25HUT20515012964")   # CDY-AN90, Android 10
D2 = os.environ.get("DEV2", "P7CDU17B22004602")   # BKL-AL20, Android 9
NAMES = {D1: "CDY-AN90", D2: "BKL-AL20"}

LBL_ONLINE = "在线"
LBL_RANKED_TAB = "排位赛"
LBL_PRACTICE_TAB = "练习场"
LBL_FRIEND_TAB = "亲友赛"
LBL_RANKED_PLAY = "在线对战"
LBL_BOT_PLAY = "人机对战"
LBL_CREATE = "创建房间"
LBL_JOIN = "加入房间"
LBL_SEARCH = "正在寻找对手"
LBL_CONNECTING = "连接中"
LBL_FOUND = "已找到对手"
LBL_START = ("开始", "Start", "Play")
LBL_WAITING = ("正在等待", "Waiting")
LBL_READY = ("你的对手", "Your opponent")
LBL_LOST = ("你输了", "You lost")
LBL_WIN = ("你赢了", "You won")
LBL_WIN_LONG = ("恭喜", "Congratulations")
LBL_DRAW = ("平局", "Draw")
LBL_COMPLETE = "对局结束"
LBL_ROUND_RESULTS = "回合结果"
LBL_EXIT = ("退出", "Exit")
LBL_RESIGN_DIALOG = ("失去押注", "lose your bet")
LBL_RESIGN_YES = ("好", "YES")
LBL_STATS_UNAVAILABLE = "此模式的战绩暂不可用"
LBL_NEXT = "下一步"
LBL_ROUND_CONFIRM = "正在确认本轮成绩"
LBL_NEXT_ROUND = "下一轮："
LBL_ROUND_STARTS_IN = "秒后进入下一轮准备"
LBL_SETTLING = "正在结算最终成绩"
ROUND_CARD = (LBL_ROUND_CONFIRM, LBL_NEXT_ROUND, LBL_ROUND_STARTS_IN, LBL_SETTLING)
SETTLE_CARD = (LBL_WIN[0], LBL_LOST[0], LBL_DRAW[0], "恭喜", LBL_COMPLETE, "你赢了")
STAT_LABELS = ("胜场", "负场", "平局", "胜率 %")
LBL_BOT_PICK = "选择你的对手"

ROUND_SECONDS = int(os.environ.get("ROUND_S", "85"))
ROUNDS = int(os.environ.get("ROUNDS", "5"))
TUTORIAL_HINT = "排位赛需要支付金币入场"

RESULTS = []


def check(name, ok, detail=""):
    RESULTS.append((name, ok, detail))
    print("  [%s] %s%s" % ("PASS" if ok else "FAIL", name,
                           (" — " + detail) if detail else ""), flush=True)
    return ok


def step(msg):
    print("\n%s %s" % (time.strftime("%H:%M:%S"), msg), flush=True)


def ev(dev, flow, tag):
    dual.screenshot(dev, tag, flow)
    dual.save_dump(dev, tag, flow)


def wait_for(dev, text, timeout=20.0):
    hit = dual.wait(dev, text, timeout)
    return hit


def tap_when(dev, text, timeout=15.0):
    hit = dual.wait(dev, text, timeout)
    if hit:
        dual.tap(dev, hit[0], hit[1])
        time.sleep(1.5)
    return hit is not None


def texts(dev):
    return [t for t, _, _ in dual.visible_texts(dual.dump(dev))]


def goto_main_menu(dev):
    """Bring the MAIN package to the front (the bottom bar is on the host activity)."""
    return dual.ensure_main_app(dev)


def goto_online(dev, tab=LBL_RANKED_TAB):
    """Open the Online tab (bottom bar item 4) and the requested mode tab."""
    if not goto_main_menu(dev):
        return False
    for _ in range(4):
        if dual.tap_bottom(dev, 3):
            time.sleep(3)
            if dual.wait(dev, LBL_RANKED_TAB, 8):
                return open_tab(dev, tab)
        # A sub-page without the bottom bar (history/top/ranking) sits on top.
        dual.key(dev, "back")
        time.sleep(2.5)
    return False


def open_tab(dev, tab):
    hit = dual.wait(dev, LBL_RANKED_TAB, 15)
    if not hit:
        return False
    xml = dual.dump(dev)
    hit = dual.find(xml, tab)
    if hit:
        dual.tap(dev, hit[0], hit[1])
        time.sleep(2.0)
        return True
    # already on the page? (tab label may render as a separate tab node)
    return dual.find(dual.dump(dev), tab) is not None


def await_any(dev, needles, timeout, poll=2.0):
    """Poll until one of the labels shows up; returns the hit label."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        xml = dual.dump(dev)
        for needle in needles:
            if dual.find(xml, needle):
                return needle
        time.sleep(poll)
    return None


def dismiss_tutorial(dev, flow):
    """The first ranked tap on a fresh install shows the 5-page tutorial."""
    for _ in range(10):
        xml = dual.dump(dev)
        if not dual.find(xml, (LBL_NEXT, TUTORIAL_HINT, "教程")):
            return True
        hit = dual.find(xml, (LBL_NEXT, LBL_START[0]))
        if hit:
            dual.tap(dev, hit[0], hit[1])
            time.sleep(1.8)
        else:
            return True
    return True


def prime_and_queue(dev, action_label, flow):
    """Open the tutorial (first tap), clear it, then queue for real."""
    tap_when(dev, action_label, 10)
    time.sleep(3)
    dismiss_tutorial(dev, flow)
    if dual.find(dual.dump(dev), (LBL_SEARCH, LBL_FOUND, "寻找")):
        return True
    tap_when(dev, action_label, 10)
    return True


def queue_both(action_label, flow):
    """Prime both tutorials first, then queue both inside the bot window."""
    for dev in (D1, D2):
        tap_when(dev, action_label, 10)
        time.sleep(2.0)
    for dev in (D1, D2):
        dismiss_tutorial(dev, flow)
    for dev in (D1, D2):
        if not dual.find(dual.dump(dev), (LBL_SEARCH, LBL_FOUND)):
            tap_when(dev, action_label, 10)
    return True


def read_mode_stats(dev):
    """Parse the (label,value) grid of the visible mode page.

    The grid renders the value ABOVE its caption (value row, then label row),
    so candidates are searched on both sides of the anchor."""
    xml = dual.dump(dev)
    if dual.find(xml, LBL_STATS_UNAVAILABLE):
        return {"unavailable": True}
    pool = dual.visible_texts(xml)
    out = {}
    for label in STAT_LABELS:
        anchors = [n for n in pool if n[0].strip() == label]
        found = None
        for ax, ay in [(a[1], a[2]) for a in anchors]:
            cands = [n for n in pool if 0 < abs(n[2] - ay) <= 150
                     and abs(n[1] - ax) <= 100 and re.match(r"^[\d—]+%?$", n[0].strip())]
            if cands:
                cands.sort(key=lambda n: abs(n[2] - ay))
                found = cands[0][0].strip()
                break
        out[label] = found
    return out


def type_code(dev, code):
    """Type a 4-digit room code into the join dialog."""
    xml = dual.dump(dev)
    field = dual.find(xml, "输入 4 位房间代码")
    if not field:
        return False
    dual.tap(dev, field[0], field[1])
    time.sleep(1.0)
    dual.sh(dev, "shell", "input", "text", code)
    time.sleep(0.8)
    xml = dual.dump(dev)
    hit = dual.find(xml, ("加入房间", "确定", "确认", "加入", "好"))
    if hit:
        dual.tap(dev, hit[0], hit[1])
        time.sleep(1.5)
        return True
    return False


def room_code(dev):
    xml = dual.dump(dev)
    m = re.search(r'content-desc="(\d{4})"', xml)
    if m:
        return m.group(1)
    for m in re.finditer(r'text="((?:\d\s+){3}\d)"', xml):
        digits = re.sub(r"\s+", "", m.group(1))
        if len(digits) == 4:
            return digits
    return None


def net_off(dev):
    dual.sh(dev, "shell", "svc", "wifi", "disable")
    dual.sh(dev, "shell", "svc", "data", "disable")


def net_on(dev):
    dual.sh(dev, "shell", "svc", "wifi", "enable")
    dual.sh(dev, "shell", "svc", "data", "enable")


# ------------------------------------------------------------------- flows

def flow_preflight():
    for dev in (D1, D2):
        name = NAMES[dev]
        step("preflight %s" % name)
        pkg = dual.sh(dev, "shell", "dumpsys", "package", dual.PKG)
        ver = re.search(r"versionName=(\S+)", pkg)
        check("%s package installed" % name, bool(ver), ver.group(1) if ver else "missing")
        check("%s has no DEBUGGABLE flag" % name, "DEBUGGABLE" not in pkg,
              "release build on a device" if "DEBUGGABLE" not in pkg else "debuggable!")
        print("  locale=%s size=%s" % (dual.locale(dev), dual.screen_size(dev)), flush=True)
        dual.sh(dev, "shell", "svc", "power", "stayon", "true")
        dual.launch(dev)
        time.sleep(8)
        ev(dev, "preflight", "main-menu")
        labels = texts(dev)
        check("%s main menu shows the bottom bar" % name,
              any(LBL_ONLINE in t for t in labels) or dual.find(dual.dump(dev), LBL_ONLINE),
              " | ".join(labels[:8]))


def play_rounds(flow, rounds=None):
    """Sample both phones through a running match, one round card at a time."""
    rounds = rounds or ROUNDS
    for rnd in range(1, rounds + 1):
        time.sleep(14)
        ev(D1, flow, "r%02d-play-d1" % rnd)
        ev(D2, flow, "r%02d-play-d2" % rnd)
        card = await_any(D1, ROUND_CARD, 78, poll=0.6)
        card2 = await_any(D2, ROUND_CARD, 10, poll=0.6) if card else None
        ev(D1, flow, "r%02d-card-d1" % rnd)
        ev(D2, flow, "r%02d-card-d2" % rnd)
        check("round %d card reached both phones" % rnd, bool(card) and bool(card2),
              "D1=%s D2=%s" % (card, card2))
        print("  round %d card: D1=%s D2=%s" % (rnd, card, card2), flush=True)
        if card in (LBL_SETTLING,):
            break
    settle = await_any(D1, SETTLE_CARD, 120)
    ev(D1, flow, "last-settle-d1")
    ev(D2, flow, "last-settle-d2")
    t1, t2 = texts(D1), texts(D2)
    check("settlement card on both phones", bool(settle),
          "D1=%s | D2=%s" % (" / ".join(t1[:6]), " / ".join(t2[:6])))
    return settle


def flow_ranked():
    """Full ranked match between the two phones."""
    step("ranked: both phones to the ranked page")
    ok1 = goto_online(D1, LBL_RANKED_TAB)
    ok2 = goto_online(D2, LBL_RANKED_TAB)
    check("both phones on the ranked tab", ok1 and ok2, "D1=%s D2=%s" % (ok1, ok2))
    ev(D1, "ranked", "01-lobby-d1")
    ev(D2, "ranked", "02-lobby-d2")
    print("  ranked stats before: D1=%s D2=%s"
          % (read_mode_stats(D1), read_mode_stats(D2)), flush=True)

    step("ranked: queue both within the bot-fallback window")
    queue_both(LBL_RANKED_PLAY, "ranked")
    for dev in (D1, D2):
        ev(dev, "ranked", "03-searching-%s" % NAMES[dev])

    step("ranked: wait for the opponent-found card on both")
    f1 = wait_for(D1, LBL_FOUND, 45)
    f2 = wait_for(D2, LBL_FOUND, 45)
    check("both phones show the opponent-found card", bool(f1) and bool(f2),
          "D1=%s D2=%s" % (bool(f1), bool(f2)))
    ev(D1, "ranked", "04-found-d1")
    ev(D2, "ranked", "04-found-d2")
    n1 = [t for t in texts(D1) if len(t) < 16]
    n2 = [t for t in texts(D2) if len(t) < 16]
    print("  D1 card texts: %s" % " | ".join(n1[:12]), flush=True)
    print("  D2 card texts: %s" % " | ".join(n2[:12]), flush=True)

    step("ranked: dual START (staged: waiting -> opponent ready)")
    tap_when(D1, LBL_START, 12)
    time.sleep(4)
    ev(D1, "ranked", "05a-d1-pressed")
    check("D1 shows the waiting-for-opponent state",
          bool(dual.find(dual.dump(D1), LBL_WAITING)), " | ".join(texts(D1)[:8]))
    tap_when(D2, LBL_START, 12)
    time.sleep(4)
    ev(D2, "ranked", "05b-d2-pressed")
    check("D1 flips to the opponent-is-ready state",
          bool(dual.find(dual.dump(D1), LBL_READY)), " | ".join(texts(D1)[:8]))
    ev(D1, "ranked", "05c-d1-ready")

    step("ranked: %d rounds" % ROUNDS)
    play_rounds("ranked")


def flow_friend():
    step("friend: D1 creates a room, D2 joins by code")
    check("D1 on friend tab", goto_online(D1, LBL_FRIEND_TAB))
    check("D2 on friend tab", goto_online(D2, LBL_FRIEND_TAB))
    tap_when(D1, LBL_CREATE, 10)
    code = None
    for _ in range(10):
        code = room_code(D1)
        if code:
            break
        time.sleep(1)
    check("D1 shows a 4-digit room code", code is not None, code or "no code")
    ev(D1, "friend", "01-room-created")
    tap_when(D2, LBL_JOIN, 10)
    time.sleep(1.5)
    ev(D2, "friend", "02-join-dialog")
    check("D2 typed the room code", type_code(D2, code or "0000"))
    f1 = wait_for(D1, LBL_FOUND, 30)
    f2 = wait_for(D2, LBL_FOUND, 30)
    check("both show the opponent-found card", bool(f1) and bool(f2))
    ev(D1, "friend", "03-found-d1")
    ev(D2, "friend", "03-found-d2")
    tap_when(D1, LBL_START, 12)
    tap_when(D2, LBL_START, 12)
    step("friend: playing out the match")
    play_rounds("friend")


def flow_resign():
    step("resign: ranked match, D1 resigns in round 1")
    check("D1 on ranked tab", goto_online(D1, LBL_RANKED_TAB))
    check("D2 on ranked tab", goto_online(D2, LBL_RANKED_TAB))
    queue_both(LBL_RANKED_PLAY, "resign")
    check("paired", bool(wait_for(D1, LBL_FOUND, 45)) and bool(wait_for(D2, LBL_FOUND, 45)))
    tap_when(D1, LBL_START, 12)
    tap_when(D2, LBL_START, 12)
    step("resign: waiting out the pre-round list, then back -> dialog")
    time.sleep(18)
    ev(D1, "resign", "01-in-round")
    dialog = False
    for attempt in range(4):
        dual.key(D1, "back")
        for _ in range(8):
            xml = dual.dump(D1)
            if dual.find(xml, LBL_RESIGN_DIALOG):
                dialog = True
                break
            time.sleep(0.8)
        if dialog:
            break
    check("resign dialog appeared on D1", dialog)
    ev(D1, "resign", "02-dialog")
    if dialog:
        tap_when(D1, LBL_RESIGN_YES, 6)
    step("resign: settlement mirror")
    time.sleep(6)
    ev(D1, "resign", "03-after-resign-d1")
    ev(D2, "resign", "03-after-resign-d2")
    t1, t2 = texts(D1), texts(D2)
    check("D1 sees a loss card", any(LBL_LOST[0] in t for t in t1), " | ".join(t1[:8]))
    check("D2 sees a win card", any(("赢" in t or "恭喜" in t) for t in t2),
          " | ".join(t2[:8]))


def flow_reconnect():
    step("reconnect: friend match, D1 loses network mid-round")
    check("D1 on friend tab", goto_online(D1, LBL_FRIEND_TAB))
    check("D2 on friend tab", goto_online(D2, LBL_FRIEND_TAB))
    tap_when(D1, LBL_CREATE, 10)
    code = None
    for _ in range(10):
        code = room_code(D1)
        if code:
            break
        time.sleep(1)
    check("room code on D1", code is not None, code or "no code")
    tap_when(D2, LBL_JOIN, 10)
    time.sleep(1.5)
    check("D2 joined the room", type_code(D2, code or "0000"))
    check("paired", bool(wait_for(D1, LBL_FOUND, 30)) and bool(wait_for(D2, LBL_FOUND, 30)))
    tap_when(D1, LBL_START, 10)
    tap_when(D2, LBL_START, 10)
    step("reconnect: into round 1, then cut D1's network for ~18s")
    time.sleep(14)
    ev(D1, "reconnect", "01-in-round-d1")
    net_off(D1)
    time.sleep(10)
    ev(D2, "reconnect", "02-opponent-dropped-d2")
    t2 = texts(D2)
    check("D2 shows the opponent reconnecting hint",
          any(("重新连接" in t or "reconnect" in t.lower()) for t in t2), " | ".join(t2[:8]))
    net_on(D1)
    time.sleep(12)
    ev(D1, "reconnect", "03-recovered-d1")
    ev(D2, "reconnect", "03-recovered-d2")
    t1 = texts(D1)
    check("D1 finished reconnecting",
          not any("重新连接中" in t for t in t1), " | ".join(t1[:8]))


def flow_practice():
    step("practice: D1 plays a bot match (difficulty picker -> round -> intermission -> quit)")
    check("D1 on practice tab", goto_online(D1, LBL_PRACTICE_TAB))
    tap_when(D1, LBL_BOT_PLAY, 10)
    time.sleep(2)
    ev(D1, "practice", "01-difficulty-dialog")
    labels = texts(D1)
    pick = dual.find(dual.dump(D1), ("青铜", "BRONZE"))
    check("difficulty picker opened", bool(pick) or any(LBL_BOT_PICK in t for t in labels),
          " | ".join(labels[:10]))
    if pick:
        dual.tap(D1, pick[0], pick[1])
    time.sleep(5)
    if wait_for(D1, LBL_FOUND, 20):
        ev(D1, "practice", "02-found")
        tap_when(D1, LBL_START, 10)
        time.sleep(12)
        ev(D1, "practice", "03-round1")
        check("practice round 1 in a game", len(texts(D1)) > 0,
              " | ".join(texts(D1)[:8]))
        # sample fast for the intermission card (practice intermission is 2s)
        card = await_any(D1, ROUND_CARD, 70, poll=0.5)
        ev(D1, "practice", "04-intermission")
        check("practice intermission card seen", bool(card), card or "not sampled")
        print("  practice card: %s" % card, flush=True)
    else:
        check("practice match found", False, " | ".join(texts(D1)[:10]))
        return
    step("practice: quit the match (bot wins) and read the settlement")
    time.sleep(6)
    for attempt in range(3):
        dual.key(D1, "back")
        time.sleep(2)
        if tap_when(D1, ("好", "确定", "YES"), 4):
            break
    time.sleep(6)
    ev(D1, "practice", "05-settlement")
    t = texts(D1)
    print("  practice settlement: %s" % " | ".join(t[:14]), flush=True)
    check("practice settlement card", any(("你输了" in x or "对局结束" in x) for x in t),
          " | ".join(t[:8]))
    step("practice: per-mode stats after the bot match")
    if goto_online(D1, LBL_PRACTICE_TAB):
        print("  D1 practice stats: %s" % read_mode_stats(D1), flush=True)
        ev(D1, "practice", "06-stats-after")


def flow_tabs(dev=None):
    dev = dev or D1
    step("tabs: %s mode tab tap + swipe" % NAMES.get(dev, dev))
    check("online page open", goto_online(dev, LBL_RANKED_TAB))
    ev(dev, "tabs", "01-ranked")
    ranked = read_mode_stats(dev)
    print("  ranked stats: %s" % ranked, flush=True)
    for tab, tag in ((LBL_PRACTICE_TAB, "02-practice"), (LBL_FRIEND_TAB, "03-friend")):
        open_tab(dev, tab)
        time.sleep(1.5)
        ev(dev, "tabs", tag)
        print("  %s stats: %s" % (tab, read_mode_stats(dev)), flush=True)
    # swipe back: friend -> practice by swiping right
    w, h = dual.screen_size(dev)
    dual.swipe(dev, int(w * 0.2), int(h * 0.45), int(w * 0.85), int(h * 0.45), 400)
    time.sleep(2)
    ev(dev, "tabs", "04-swipe-back")
    t = texts(dev)
    check("swipe moved to the practice page", any(LBL_BOT_PLAY in x for x in t),
          " | ".join(t[:8]))


def flow_stats(dev=None):
    dev = dev or D1
    step("stats: per-mode figures on %s" % NAMES.get(dev, dev))
    if not goto_online(dev, LBL_RANKED_TAB):
        check("online page", False)
        return
    for tab, tag in ((LBL_RANKED_TAB, "ranked"), (LBL_PRACTICE_TAB, "practice"),
                     (LBL_FRIEND_TAB, "friend")):
        open_tab(dev, tab)
        time.sleep(1.5)
        ev(dev, "stats", tag)
        print("  %-8s %s" % (tab, read_mode_stats(dev)), flush=True)


def flow_offline(dev=None):
    dev = dev or D1
    step("offline: %s menu tabs + a real game + challenge + profile" % NAMES.get(dev, dev))
    check("game in the foreground", goto_main_menu(dev))
    dual.tap_bottom(dev, 0)
    time.sleep(3)
    ev(dev, "offline", "01-sprint")
    print("  sprint: %s" % " | ".join(texts(dev)[:12]), flush=True)

    # Open the first sprint game tile and play it briefly (R8 path: game
    # activity, GL/asset loading, level data).
    xml = dual.dump(dev)
    tiles = dual.bar_items(xml)
    hit = dual.find(xml, "记忆网格")
    if hit:
        game_tile = (hit[0], hit[1] - 80)   # the tile above its label
        dual.tap(dev, game_tile[0], max(game_tile[1], 300))
        time.sleep(9)
        ev(dev, "offline", "02-game-open")
        print("  game screen: %s" % " | ".join(texts(dev)[:12]), flush=True)
        check("game screen rendered something", len(texts(dev)) > 0)
        time.sleep(6)
        ev(dev, "offline", "03-game-playing")
        dual.key(dev, "back")
        time.sleep(3)
    else:
        check("sprint tile found", False, "记忆网格 not on screen")

    for index, tag in ((2, "04-challenge"), (4, "05-profile")):
        dual.tap_bottom(dev, index)
        time.sleep(4)
        ev(dev, "offline", tag)
        print("  %s: %s" % (tag, " | ".join(texts(dev)[:14])), flush=True)
    dual.tap_bottom(dev, 0)
    time.sleep(2)


def flow_play():
    """Play out a live pairing: both phones already sit at the accept gate."""
    step("play: both phones at the accept gate")
    f1 = wait_for(D1, LBL_FOUND, 40)
    f2 = wait_for(D2, LBL_FOUND, 40)
    check("accept gate on both phones", bool(f1) and bool(f2),
          "D1=%s D2=%s" % (bool(f1), bool(f2)))
    ev(D1, "ranked", "04-found-d1")
    ev(D2, "ranked", "04-found-d2")
    print("  D1 gate: %s" % " | ".join(texts(D1)[:12]), flush=True)
    print("  D2 gate: %s" % " | ".join(texts(D2)[:12]), flush=True)

    step("play: staged dual START")
    tap_when(D1, LBL_START, 12)
    time.sleep(4)
    ev(D1, "ranked", "05a-d1-pressed")
    check("D1 shows the waiting-for-opponent state",
          bool(dual.find(dual.dump(D1), LBL_WAITING)), " | ".join(texts(D1)[:8]))
    tap_when(D2, LBL_START, 12)
    time.sleep(4)
    ev(D2, "ranked", "05b-d2-pressed")
    check("D1 flips to the opponent-is-ready state",
          bool(dual.find(dual.dump(D1), LBL_READY)), " | ".join(texts(D1)[:8]))
    ev(D1, "ranked", "05c-d1-ready")

    step("play: %d rounds" % ROUNDS)
    play_rounds("ranked")
    step("play: per-mode stats after the match")
    for dev, name in ((D1, "D1"), (D2, "D2")):
        if goto_online(dev, LBL_RANKED_TAB):
            print("  %s ranked stats: %s" % (name, read_mode_stats(dev)), flush=True)
            ev(dev, "ranked", "07-stats-after-%s" % name)


def flow_reconnect_resign():
    """One ranked match that covers both edge cases: D1 drops mid-round 1
    (opponent hint + catch-up), then resigns (forfeit mirror). Coins are
    tight, so both scenarios share a single paid match."""
    step("edge: ranked match -> network drop/recover -> resign")
    check("D1 on ranked tab", goto_online(D1, LBL_RANKED_TAB))
    check("D2 on ranked tab", goto_online(D2, LBL_RANKED_TAB))
    queue_both(LBL_RANKED_PLAY, "edge")
    check("paired", bool(wait_for(D1, LBL_FOUND, 45)) and bool(wait_for(D2, LBL_FOUND, 45)))
    ev(D1, "edge", "01-found-d1")
    tap_when(D1, LBL_START, 12)
    time.sleep(3)
    tap_when(D2, LBL_START, 12)
    step("edge: into round 1, then D1 loses the network for ~18s")
    time.sleep(16)
    ev(D1, "edge", "02-in-round-d1")
    net_off(D1)
    time.sleep(12)
    ev(D2, "edge", "03-opp-dropped-d2")
    t2 = texts(D2)
    check("D2 shows the opponent-reconnecting hint",
          any(("重新连接" in t) for t in t2), " | ".join(t2[:10]))
    net_on(D1)
    step("edge: D1 recovers (catch-up) and the match continues")
    recovered = None
    deadline = time.time() + 60
    while time.time() < deadline:
        t1 = texts(D1)
        if not any("重新连接中" in t for t in t1):
            recovered = True
            break
        time.sleep(3)
    check("D1 finished reconnecting", bool(recovered), " | ".join(texts(D1)[:10]))
    ev(D1, "edge", "04-recovered-d1")
    ev(D2, "edge", "04-recovered-d2")
    print("  D2 after recovery: %s" % " | ".join(texts(D2)[:10]), flush=True)
    step("edge: resign on D1 (forfeit mirror)")
    dialog = False
    for _ in range(4):
        dual.key(D1, "back")
        for _ in range(8):
            if dual.find(dual.dump(D1), LBL_RESIGN_DIALOG):
                dialog = True
                break
            time.sleep(0.8)
        if dialog:
            break
    check("resign dialog appeared on D1", dialog)
    ev(D1, "edge", "05-resign-dialog")
    if dialog:
        tap_when(D1, LBL_RESIGN_YES, 6)
    time.sleep(8)
    ev(D1, "edge", "06-after-d1")
    ev(D2, "edge", "06-after-d2")
    t1 = texts(D1)
    t2 = texts(D2)
    check("D1 sees a loss card", any(LBL_LOST[0] in t for t in t1), " | ".join(t1[:10]))
    check("D2 sees a win card", any(("赢" in t or "恭喜" in t) for t in t2), " | ".join(t2[:10]))
    step("edge: per-mode stats after the forfeit")
    for dev, name in ((D1, "D1"), (D2, "D2")):
        if goto_online(dev, LBL_RANKED_TAB):
            print("  %s ranked stats: %s" % (name, read_mode_stats(dev)), flush=True)
            ev(dev, "edge", "07-stats-%s" % name)


FLOWS = {"preflight": flow_preflight, "ranked": flow_ranked, "friend": flow_friend,
         "resign": flow_resign, "reconnect": flow_reconnect, "practice": flow_practice,
         "tabs": flow_tabs, "stats": flow_stats, "offline": flow_offline,
         "play": flow_play, "edge": flow_reconnect_resign}


def main():
    flow = sys.argv[1]
    if flow not in FLOWS:
        raise SystemExit("flows: %s" % ", ".join(sorted(FLOWS)))
    if len(sys.argv) > 2:
        FLOWS[flow](sys.argv[2])
    else:
        FLOWS[flow]()
    print("\n==== %s summary ====" % flow)
    failed = 0
    for name, ok, detail in RESULTS:
        print("[%s] %s%s" % ("PASS" if ok else "FAIL", name,
                             (" — " + detail) if detail else ""))
        failed += 0 if ok else 1
    print("%d/%d passed" % (len(RESULTS) - failed, len(RESULTS)))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
