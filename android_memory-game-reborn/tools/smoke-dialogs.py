#!/usr/bin/env python3
"""Preview-only device smoke test. Never clicks purchase, login or upload actions."""
import argparse
import json
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("--adb", required=True)
parser.add_argument("--serial", required=True)
parser.add_argument("--package", default="com.memory.brain.training.games.accounttest")
parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] / "build/dialog-ui-evidence")
options = parser.parse_args()
if not options.package.endswith(".accounttest"):
    parser.error("Only the isolated .accounttest application is permitted")
options.output.mkdir(parents=True, exist_ok=True)


def adb(*args, binary=False):
    return subprocess.check_output([options.adb, "-s", options.serial, *args],
                                   timeout=30, text=not binary, encoding=None if binary else "utf-8")


def tree():
    adb("shell", "uiautomator", "dump", "/sdcard/dialog-preview.xml")
    # The dedicated filesystem reader cannot read an Android device path.
    return adb("exec-out", "cat", "/sdcard/dialog-preview.xml")


scenarios = [
    ("Pause", "游戏已暂停", "Game paused"),
    ("Retry", "再试一次", "Try again"),
    ("Challenge result", "三星目标", "Three-star target"),
    ("Trophy", "已达到第", "level"),
    ("Feedback", "反馈", "feedback"),
    ("Workout help", "训练", "workout"),
    ("Workout invitation", "训练", "workout"),
    ("Practice exit", "不会扣除金币", "No coins"),
    ("Room code", "加入房间", "Join room"),
    ("Room waiting", "复制房间码", "Copy room code"),
    ("Difficulty", "青铜", "Bronze"),
    ("Coins", "金币不足", "Not enough coins"),
    ("Result layout", "最佳到达第 10 关", "Best level: 10"),
    ("Answer layout", "几个格子", "How many"),
    ("Unlock layout", "100", "100"),
]
results = []
for index, (name, chinese, english) in enumerate(scenarios, 1):
    adb("shell", "am", "force-stop", options.package)
    adb("shell", "am", "start", "-n", options.package + "/com.cube.memorygames.DebugDialogPreviewActivity",
        "--es", "preview", '"' + name + '"')
    time.sleep(1.5)
    xml = tree()
    nodes = ET.fromstring(xml)
    texts = " ".join(n.get("text", "") + " " + n.get("content-desc", "") for n in nodes.iter("node"))
    passed = chinese in texts or english.lower() in texts.lower()
    stem = f"{index:02d}-" + name.lower().replace(" ", "-")
    (options.output / (stem + ".xml")).write_text(xml, encoding="utf-8")
    (options.output / (stem + ".png")).write_bytes(adb("exec-out", "screencap", "-p", binary=True))
    result = {"name": name, "passed": passed, "expected": [chinese, english]}
    results.append(result)
    print(("PASS" if passed else "FAIL") + ": " + name, flush=True)
(options.output / "results.json").write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
adb("shell", "am", "force-stop", options.package)
if not all(r["passed"] for r in results):
    raise SystemExit(1)
print(f"PASS: {len(results)} preview dialogs; no purchase/authentication/upload actions submitted")
