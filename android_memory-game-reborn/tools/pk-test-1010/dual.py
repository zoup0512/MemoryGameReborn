#!/usr/bin/env python3
"""Dual-device driver primitives for the two-phone regression round.

The existing suite (regression.py) pairs one phone with a scripted WS probe.
This module is for two REAL phones: every primitive takes a device serial, so
scenarios can interleave both phones (queue both, confirm both, compare the
question sets, etc.).

Usage (from this folder):
  python dual.py <serial> dump
  python dual.py <serial> find "在线对战"
  python dual.py <serial> tap "在线对战"
  python dual.py <serial> wait "开始" [seconds]
  python dual.py <serial> shot <name>
  python dual.py <serial> key back
  python dual.py <serial> swipe <x1> <y1> <x2> <y2> [ms]
  python dual.py <serial> launch
  python dual.py <serial> ui              # compact text/desc listing of visible nodes

Devices are driven over USB (adb -s). ui.xml is dumped to the device path from
Python so Git Bash's path mangling never touches it.
"""
import os
import re
import subprocess
import sys
import time

ADB = os.environ.get("ADB", "adb")
EVIDENCE = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                        "..", "..", "build", "test-round-20261016", "evidence")
PKG = "com.memory.brain.training.games"


def sh(serial, *args, timeout=60):
    result = subprocess.run([ADB, "-s", serial] + [str(a) for a in args],
                            capture_output=True, text=True, errors="replace",
                            timeout=timeout)
    return result.stdout


def sh_out(serial, *args, timeout=120):
    """Raw bytes out (for screencap)."""
    result = subprocess.run([ADB, "-s", serial] + [str(a) for a in args],
                            capture_output=True, timeout=timeout)
    return result.stdout


def dump(serial):
    sh(serial, "shell", "rm", "-f", "/data/local/tmp/ui.xml")
    sh(serial, "shell", "uiautomator", "dump", "/data/local/tmp/ui.xml")
    return sh(serial, "shell", "cat", "/data/local/tmp/ui.xml")


def nodes(xml):
    return [(m.group(1), (int(m.group(2)) + int(m.group(4))) // 2,
             (int(m.group(3)) + int(m.group(5))) // 2,
             m.group(2), m.group(3), m.group(4), m.group(5))
            for m in re.finditer(
                r'<node[^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)]


def find(xml, text, clickable_only=False):
    """Same contract as ui.py: exact match wins over substring; text may be a tuple."""
    needles = (text,) if isinstance(text, str) else tuple(text)
    pool = nodes(xml)
    if clickable_only:
        pool = [n for n in pool if 'clickable="true"' in _node_tag(xml, n)]
    for needle in needles:
        for node_text, x, y, *_ in pool:
            if node_text.strip().lower() == needle.strip().lower():
                return x, y, node_text
    for needle in needles:
        low = needle.lower()
        for node_text, x, y, *_ in pool:
            if low in node_text.lower():
                return x, y, node_text
    return None


def _node_tag(xml, node):
    """The raw <node ...> tag containing this node's bounds."""
    x1, y1, x2, y2 = node[3], node[4], node[5], node[6]
    needle = 'bounds="[%s,%s][%s,%s]"' % (x1, y1, x2, y2)
    idx = xml.find(needle)
    if idx < 0:
        return ""
    start = xml.rfind("<node", 0, idx)
    end = xml.find(">", idx)
    return xml[start:end + 1]


def visible_texts(xml):
    """Visible (non-empty text, non-degenerate bounds) labels with centers."""
    out = []
    for node_text, x, y, x1, y1, x2, y2 in nodes(xml):
        if node_text.strip() and int(x2) > int(x1) and int(y2) > int(y1):
            out.append((node_text, x, y))
    return out


def descs(xml):
    return re.findall(r'content-desc="([^"]+)"', xml)


def max_y(xml):
    top = 0
    for m in re.finditer(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        top = max(top, int(m.group(4)))
    return top


def bar_items(xml):
    """Clickable nodes of the bottom bar row, sorted left to right.

    Only the lowest row of clickable nodes counts: a scrolling list above the
    bar can contain full-width clickable rows that would otherwise shift the
    tab indices."""
    limit = max_y(xml) * 0.88
    cands = []
    for m in re.finditer(r'<node[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*?>', xml):
        x1, y1, x2, y2 = (int(m.group(i)) for i in range(1, 5))
        if y1 >= limit and (x2 - x1) > 40 and 'clickable="true"' in m.group(0):
            cands.append((x1, y1, x2, y2))
    if not cands:
        return []
    row_y = max(c[1] for c in cands)
    return sorted(c for c in cands if abs(c[1] - row_y) <= 24)


def tap_bottom(serial, index):
    """Tap a bottom-bar tab by index (0-based) using its dumped bounds."""
    items = bar_items(dump(serial))
    if index >= len(items):
        return None
    x1, y1, x2, y2 = items[index]
    cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
    tap(serial, cx, cy)
    return (cx, cy)


def app_foreground(serial):
    """True when one of the game's own screens is on top."""
    xml = dump(serial)
    markers = ("记忆网格", "六边形", "在线对战", "排位赛", "练习场", "亲友赛", "冲刺")
    return any(find(xml, m) for m in markers)


def foreground_pkg(serial):
    """The resumed activity's package, e.g. the .accounttest clone vs the main app."""
    out = sh(serial, "shell", "dumpsys", "activity", "activities")
    match = re.search(r"mResumedActivity: ActivityRecord\{[^}]*?\s+([\w.]+)/", out)
    return match.group(1) if match else None


def ensure_main_app(serial, tries=3):
    """Guarantee the MAIN package owns the foreground before any tapping.

    The .accounttest clone renders the same screens; a stray BACK can reveal
    its task underneath and silently retarget every later tap."""
    for _ in range(tries):
        if foreground_pkg(serial) == PKG:
            return True
        launch(serial)
        time.sleep(6)
    return foreground_pkg(serial) == PKG


def tap(serial, x, y):
    sh(serial, "shell", "input", "tap", str(x), str(y))


def tap_text(serial, text, timeout=0.0):
    deadline = time.time() + timeout
    while True:
        hit = find(dump(serial), text)
        if hit:
            tap(serial, hit[0], hit[1])
            return hit
        if time.time() >= deadline:
            return None
        time.sleep(1.0)


def wait(serial, text, timeout=20.0, poll=1.5):
    deadline = time.time() + timeout
    while time.time() < deadline:
        hit = find(dump(serial), text)
        if hit:
            return hit
        time.sleep(poll)
    return None


def swipe(serial, x1, y1, x2, y2, ms=300):
    sh(serial, "shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), str(ms))


def key(serial, name):
    codes = {"back": 4, "home": 3, "enter": 66, "menu": 82, "power": 26,
             "app_switch": 187}
    sh(serial, "shell", "input", "keyevent", str(codes.get(name, name)))


def screenshot(serial, name, subdir=""):
    folder = os.path.join(EVIDENCE, subdir) if subdir else EVIDENCE
    os.makedirs(folder, exist_ok=True)
    path = os.path.join(folder, name + ".png")
    with open(path, "wb") as fh:
        fh.write(sh_out(serial, "exec-out", "screencap", "-p"))
    return path


def save_dump(serial, name, subdir=""):
    folder = os.path.join(EVIDENCE, subdir) if subdir else EVIDENCE
    os.makedirs(folder, exist_ok=True)
    path = os.path.join(folder, name + ".xml")
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(dump(serial))
    return path


def launch(serial, activity=None):
    if activity:
        sh(serial, "shell", "am", "start", "-n", activity)
    else:
        sh(serial, "shell", "monkey", "-p", PKG, "-c",
           "android.intent.category.LAUNCHER", "1")


def screen_size(serial):
    out = sh(serial, "shell", "wm", "size")
    m = re.search(r"(\d+)x(\d+)", out)
    return (int(m.group(1)), int(m.group(2))) if m else (1080, 1920)


def locale(serial):
    return sh(serial, "shell", "getprop", "persist.sys.locale").strip() or \
        sh(serial, "shell", "getprop", "ro.product.locale").strip()


def main():
    serial = sys.argv[1]
    cmd = sys.argv[2]
    if cmd == "dump":
        print(dump(serial))
    elif cmd == "find":
        hit = find(dump(serial), sys.argv[3])
        print(hit if hit else "NOT FOUND")
    elif cmd == "tap":
        hit = tap_text(serial, sys.argv[3])
        print("tapped %s" % (hit,) if hit else "NOT FOUND")
    elif cmd == "wait":
        timeout = float(sys.argv[4]) if len(sys.argv) > 4 else 20.0
        print(wait(serial, sys.argv[3], timeout) or "TIMEOUT")
    elif cmd == "shot":
        print(screenshot(serial, sys.argv[3]))
    elif cmd == "key":
        key(serial, sys.argv[3])
    elif cmd == "swipe":
        swipe(serial, *[int(a) for a in sys.argv[3:8]])
    elif cmd == "launch":
        launch(serial, sys.argv[3] if len(sys.argv) > 3 else None)
        print("launched")
    elif cmd == "ui":
        xml = dump(serial)
        for text, x, y in visible_texts(xml):
            print("text=%-40r center=(%d,%d)" % (text, x, y))
        for d in descs(xml):
            print("desc=%r" % d)
    elif cmd == "locale":
        print(locale(serial))
    elif cmd == "size":
        print(screen_size(serial))
    elif cmd == "save":
        print(save_dump(serial, sys.argv[3]))
    else:
        raise SystemExit("unknown command: %s" % cmd)


if __name__ == "__main__":
    main()
