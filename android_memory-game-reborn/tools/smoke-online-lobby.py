#!/usr/bin/env python3
"""Local-only Online pager checks in the isolated accounttest package."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("--adb", required=True)
parser.add_argument("--serial", required=True)
parser.add_argument("--package", default="com.memory.brain.training.games.accounttest")
parser.add_argument("--output", type=Path,
                    default=Path(__file__).resolve().parents[1] / "build/online-ui-evidence")
parser.add_argument("--case", action="append", default=[],
                    help="Run only the named checks; repeat to select multiple cases")
options = parser.parse_args()
if not options.package.endswith(".accounttest"):
    parser.error("Only the isolated .accounttest application is permitted")
options.output.mkdir(parents=True, exist_ok=True)
results = []


def adb(*args, binary=False):
    return subprocess.check_output([options.adb, "-s", options.serial, *map(str, args)],
                                   timeout=45, text=not binary,
                                   encoding=None if binary else "utf-8")


def tree():
    adb("shell", "uiautomator", "dump", "/sdcard/online-lobby-preview.xml")
    # The dedicated filesystem reader cannot read an Android device path.
    xml = adb("exec-out", "cat", "/sdcard/online-lobby-preview.xml")
    return xml, ET.fromstring(xml)


def bounds(node):
    values = [int(value) for value in re.findall(r"\d+", node.get("bounds", ""))]
    if len(values) != 4:
        raise AssertionError("Missing screen bounds: " + str(node.attrib))
    return values


def by_id(root, suffix):
    return [node for node in root.iter("node")
            if node.get("resource-id", "").endswith(":id/" + suffix)]


def tap(node):
    left, top, right, bottom = bounds(node)
    adb("shell", "input", "tap", (left + right) // 2, (top + bottom) // 2)
    time.sleep(0.7)


def tab(root, label):
    containers = by_id(root, "online_mode_tabs")
    assert len(containers) == 1, "Expected one mode tab bar"
    candidates = [node for node in containers[0].iter("node")
                  if node.get("text") == label or node.get("content-desc") == label]
    assert candidates, "Tab not visible: " + label
    return candidates[-1]


def capture(name, xml=None):
    if xml is None:
        xml, _ = tree()
    (options.output / (name + ".xml")).write_text(xml, encoding="utf-8")
    (options.output / (name + ".png")).write_bytes(
        adb("exec-out", "screencap", "-p", binary=True))


def launch(**extras):
    adb("shell", "am", "force-stop", options.package)
    command = ["shell", "am", "start", "-n", options.package
               + "/com.cube.memorygames.DebugOnlineLobbyPreviewActivity"]
    for key, value in extras.items():
        if isinstance(value, bool):
            command.extend(["--ez", key, str(value).lower()])
        elif isinstance(value, float):
            command.extend(["--ef", key, str(value)])
        elif isinstance(value, int):
            command.extend(["--ei", key, str(value)])
        else:
            command.extend(["--es", key, str(value)])
    adb(*command)
    time.sleep(2)
    return tree()


def assert_stats(root, mode, values):
    pages = by_id(root, "online_" + mode + "_page")
    assert len(pages) == 1, "Expected visible " + mode + " page"
    for field, expected in zip(("wins", "losses", "draws", "win_rate"), values):
        nodes = by_id(pages[0], "online_stat_" + field)
        assert len(nodes) == 1, "Expected visible " + mode + " " + field
        assert nodes[0].get("text") == str(expected), (mode, field, nodes[0].get("text"), expected)
        left, top, right, bottom = bounds(nodes[0])
        assert right > left and bottom > top, "Statistic has no visible bounds"
    other_modes = {"ranked", "practice", "friend"} - {mode}
    assert not any(by_id(root, "online_" + other + "_page") for other in other_modes), \
        "Offscreen mode statistics leaked into the visible page"


def run(name, action):
    try:
        action()
        results.append({"name": name, "passed": True})
        print("PASS: " + name, flush=True)
    except Exception as error:
        results.append({"name": name, "passed": False, "error": str(error)})
        capture("failure-" + name)
        print("FAIL: " + name + ": " + str(error), flush=True)
        raise


def default_pager():
    xml, root = launch(previewLanguage="zh", previewRefresh=True)
    assert by_id(root, "panel_header"), "Shared Sprint header disappeared"
    assert_stats(root, "ranked", (8, 1, 1, "80%"))
    for removed in ("online_profile_line", "online_profile_rank", "online_profile_elo", "online_season_line"):
        assert not by_id(root, removed), "Removed dashboard content remains: " + removed
    capture("01-ranked", xml)
    tap(tab(root, "练习场"))
    xml, root = tree()
    assert_stats(root, "practice", (2, 6, 2, "20%"))
    capture("02-practice", xml)
    time.sleep(5)
    xml, root = tree()
    assert_stats(root, "practice", (2, 6, 2, "20%"))
    assert any(node.get("text") == "777" for node in root.iter("node")), "Profile refresh did not reach shared header"
    capture("03-refresh-retains-practice", xml)
    pager = by_id(root, "online_mode_pager")[0]
    left, top, right, bottom = bounds(pager)
    y = top + (bottom - top) // 3
    adb("shell", "input", "swipe", right - (right - left) // 6, y,
        left + (right - left) // 6, y, 350)
    time.sleep(0.7)
    xml, root = tree()
    assert_stats(root, "friend", (1, 2, 1, "25%"))
    capture("04-swipe-friend", xml)
    adb("shell", "input", "swipe", left + (right - left) // 6, y,
        right - (right - left) // 6, y, 350)
    time.sleep(0.7)
    _, root = tree()
    assert_stats(root, "practice", (2, 6, 2, "20%"))
    tap(tab(root, "排位赛"))
    _, root = tree()
    assert_stats(root, "ranked", (8, 1, 1, "80%"))


def action_dialogs():
    _, root = launch(previewLanguage="zh")
    tap(tab(root, "练习场"))
    _, root = tree()
    tap(by_id(root, "online_play_bot_button")[0])
    xml, root = tree()
    assert any("青铜" in node.get("content-desc", "") for node in root.iter("node")), \
        "Practice difficulty picker did not open"
    capture("15-practice-difficulty-dialog", xml)
    cancel = [node for node in root.iter("node") if node.get("text") == "取消"]
    assert cancel, "Difficulty cancel action missing"
    tap(cancel[0])
    _, root = tree()
    assert_stats(root, "practice", (2, 6, 2, "20%"))
    tap(tab(root, "亲友赛"))
    _, root = tree()
    tap(by_id(root, "online_join_room")[0])
    xml, root = tree()
    assert any(node.get("class") == "android.widget.EditText" for node in root.iter("node")), \
        "Friend room entry did not open"
    capture("16-friend-code-dialog", xml)
    cancel = [node for node in root.iter("node") if node.get("text") == "取消"]
    assert cancel, "Friend room cancel action missing"
    tap(cancel[0])
    _, root = tree()
    assert_stats(root, "friend", (1, 2, 1, "25%"))


def recreation():
    _, root = launch(previewLanguage="zh", previewRecreate=True, previewRecreateDelayMs=14000)
    tap(tab(root, "亲友赛"))
    _, root = tree()
    assert_stats(root, "friend", (1, 2, 1, "25%"))
    time.sleep(14)
    xml, root = tree()
    assert_stats(root, "friend", (1, 2, 1, "25%"))
    capture("05-recreate-retains-friend", xml)
    sprint = by_id(root, "bottomBarCup")
    assert sprint, "Sprint bottom tab missing"
    tap(sprint[0])
    _, root = tree()
    online = by_id(root, "bottomBarOnline")
    assert online, "Online bottom tab missing"
    tap(online[0])
    xml, root = tree()
    assert_stats(root, "friend", (1, 2, 1, "25%"))
    capture("06-bottom-nav-retains-friend", xml)


def unsupported():
    xml, root = launch(previewLanguage="zh", previewLegacy=True)
    assert_stats(root, "ranked", ("—", "—", "—", "—"))
    assert "99" not in [node.get("text") for node in root.iter("node")], "Mixed legacy wins displayed as ranked"
    capture("07-legacy-unavailable", xml)


def empty():
    xml, root = launch(previewLanguage="zh", previewEmpty=True)
    assert_stats(root, "ranked", (0, 0, 0, "0%"))
    capture("08-authoritative-empty", xml)


def narrow_large_font():
    xml, root = launch(previewLanguage="zh", previewWidthDp=320, previewFontScale=1.5)
    assert by_id(root, "online_mode_tabs"), "Large-font tabs missing"
    capture("09-narrow-large-font-ranked", xml)
    pager = by_id(root, "online_mode_pager")[0]
    left, top, right, bottom = bounds(pager)
    adb("shell", "input", "swipe", right - (right - left) // 6, top + (bottom - top) // 2,
        left + (right - left) // 6, top + (bottom - top) // 2, 350)
    time.sleep(0.8)
    xml, root = tree()
    assert by_id(root, "online_practice_page"), "Narrow pager did not reach practice"
    capture("10-narrow-large-font-practice", xml)
    adb("shell", "input", "swipe", right - (right - left) // 6, top + (bottom - top) // 2,
        left + (right - left) // 6, top + (bottom - top) // 2, 350)
    time.sleep(0.8)
    xml, root = tree()
    assert by_id(root, "online_friend_page"), "Narrow pager did not reach friend"
    capture("11-narrow-large-font-friend", xml)
    for _ in range(4):
        adb("shell", "input", "swipe", (left + right) // 2, bottom - 60,
            (left + right) // 2, top + 60, 450)
        time.sleep(0.7)
        xml, root = tree()
        join = by_id(root, "online_join_room")
        if join and bounds(join[0])[3] < bottom - 10:
            break
    assert by_id(root, "online_create_room"), "Friend create action unreachable by vertical scroll"
    assert join and bounds(join[0])[3] < bottom - 10, "Friend join action cannot scroll fully into view"
    capture("12-narrow-friend-actions", xml)


def english():
    xml, root = launch(previewLanguage="en", previewWidthDp=360)
    assert_stats(root, "ranked", (8, 1, 1, "80%"))
    capture("13-english-ranked", xml)
    pager = by_id(root, "online_mode_pager")[0]
    left, top, right, bottom = bounds(pager)
    for mode in ("practice", "friend"):
        adb("shell", "input", "swipe", right - (right - left) // 6, top + (bottom - top) // 2,
            left + (right - left) // 6, top + (bottom - top) // 2, 350)
        time.sleep(0.7)
    xml, root = tree()
    assert_stats(root, "friend", (1, 2, 1, "25%"))
    capture("14-english-friend", xml)


try:
    for name, action in (("pager-and-independent-stats", default_pager),
                         ("mode-action-dialogs", action_dialogs),
                         ("selection-restoration", recreation),
                         ("legacy-server-unavailable", unsupported),
                         ("empty-record", empty),
                         ("narrow-large-font-scroll", narrow_large_font),
                         ("english-layout", english)):
        if not options.case or name in options.case:
            run(name, action)
finally:
    (options.output / "results.json").write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    adb("shell", "am", "force-stop", options.package)
print("PASS: Online pager UI; no real queue, room, match, purchase, authentication or upload submitted")
