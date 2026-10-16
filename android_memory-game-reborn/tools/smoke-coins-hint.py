#!/usr/bin/env python3
"""Exercise the real home-screen hint in the isolated debug app; no online actions."""
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
parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] / "build/coin-hint-evidence")
options = parser.parse_args()
if not options.package.endswith(".accounttest"):
    parser.error("Only the isolated .accounttest application is permitted")
options.output.mkdir(parents=True, exist_ok=True)


def adb(*args, binary=False):
    return subprocess.check_output([options.adb, "-s", options.serial, *args], timeout=30,
                                   text=not binary, encoding=None if binary else "utf-8")


def launch(activity="DebugCoinsHintActivity", *extras):
    adb("shell", "am", "force-stop", options.package)
    adb("shell", "am", "start", "-W", "-n",
        options.package + "/com.cube.memorygames." + activity, *extras)
    time.sleep(.5)


def snapshot(name):
    image = adb("exec-out", "screencap", "-p", binary=True)
    (options.output / (name + ".png")).write_bytes(image)
    adb("shell", "uiautomator", "dump", "/sdcard/coin-hint-preview.xml")
    # A device path is not accessible to the host filesystem reader.
    xml = adb("exec-out", "cat", "/sdcard/coin-hint-preview.xml")
    (options.output / (name + ".xml")).write_text(xml, encoding="utf-8")
    return ET.fromstring(xml), image


def hint_nodes(tree):
    return [node for node in tree.iter("node")
            if node.get("resource-id", "").endswith("/coins_hint_message")]


results = []
try:
    for name, extras, expected in [
        ("default-zh", ["--es", "previewLocale", "zh"], "金币不足？在这里获取更多！"),
        ("narrow-zh", ["--es", "previewLocale", "zh", "--ei", "previewWidthDp", "320"], "金币不足？在这里获取更多！"),
        ("large-zh", ["--es", "previewLocale", "zh", "--ei", "previewWidthDp", "320", "--ef", "previewFontScale", "2.0"], "金币不足？在这里获取更多！"),
        ("large-de", ["--es", "previewLocale", "de", "--ei", "previewWidthDp", "320", "--ef", "previewFontScale", "2.0"], "Nicht genügend Münzen? Holen Sie sich sie hier kostenlos an!"),
    ]:
        launch("DebugCoinsHintActivity", *extras)
        tree, image = snapshot(name)
        nodes = hint_nodes(tree)
        assert len(nodes) == 1 and nodes[0].get("text") == expected, name + ": full hint text missing"
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", nodes[0].get("bounds", "")))
        width, height = int.from_bytes(image[16:20], "big"), int.from_bytes(image[20:24], "big")
        assert 0 < x1 < x2 < width and 0 < y1 < y2 < height, name + ": text outside screen"
        results.append({"name": name, "passed": True, "textBounds": [x1, y1, x2, y2]})
        print("PASS: " + name, flush=True)

    launch("DebugCoinsHintActivity", "--es", "previewLocale", "zh")
    time.sleep(8.5)
    assert not hint_nodes(snapshot("auto-dismissed")[0]), "Hint remains accessible after fading out"
    results.append({"name": "auto-dismissed", "passed": True})
    print("PASS: auto-dismissed", flush=True)

    launch("MainMenuActivity")
    assert not hint_nodes(snapshot("not-repeated")[0]), "Hint repeated after it was shown"
    results.append({"name": "not-repeated", "passed": True})
    print("PASS: not-repeated", flush=True)

    launch("DebugCoinsHintActivity", "--ei", "previewGamesPlayed", "1")
    assert not hint_nodes(snapshot("before-threshold")[0]), "Hint shown before two distinct games"
    results.append({"name": "before-threshold", "passed": True})
    print("PASS: before-threshold", flush=True)
finally:
    (options.output / "results.json").write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    adb("shell", "am", "force-stop", options.package)
print(f"PASS: {len(results)} coin hint checks; no purchases, authentication or uploads")
