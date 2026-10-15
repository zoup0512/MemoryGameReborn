#!/usr/bin/env python3
"""Static resource/entry-point checks; does not replace device acceptance."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/res"
JAVA = ROOT / "src/main/java/com/cube/memorygames"
ANDROID = "{http://schemas.android.com/apk/res/android}"


def check(condition, message):
    if not condition:
        raise AssertionError(message)


def check_variants(name):
    base = ET.parse(RES / "layout" / f"{name}.xml").getroot()
    def ids(tree):
        return {v.attrib[ANDROID + "id"].split("/")[-1]: v.tag
                for v in tree.iter() if ANDROID + "id" in v.attrib}
    expected = ids(base)
    for path in RES.glob(f"layout*/{name}.xml"):
        current = ids(ET.parse(path).getroot())
        check(current == expected, f"{path}: IDs/types differ from default")


for path in RES.glob("**/*.xml"):
    ET.parse(path)
for name in ["dialog_pause", "dialog_fail", "dialog_timeout", "dialog_trophy",
             "dialog_challenge_start", "dialog_challenge_result", "dialog_unlock",
             "dialog_feedback", "dialog_try_workout", "dialog_workout_help", "dialog_game5"]:
    check_variants(name)

for path in (JAVA / "games").glob("*.java"):
    source = path.read_text(encoding="utf-8")
    check("R.string.dialog_lose_bet" not in source, f"{path}: legacy practice bet warning")
    if path.name != "Game1MemoryGridActivity.java":
        check("void showOnlineBackPressDialog()" not in source, f"{path}: duplicated exit dialog")
base = (JAVA / "games/Game1MemoryGridActivity.java").read_text(encoding="utf-8")
check("protected final void showOnlineBackPressDialog()" in base, "Missing shared exit method")
check("DialogUi.showOnlineExit" in base, "Missing shared exit UI")
rate = (JAVA / "ui/RateDialog.java").read_text(encoding="utf-8")
check(rate.index("DialogUi.setContent") < rate.index("findViewById"), "Rate views bound before inflation")
fail = (JAVA / "FailDialog.java").read_text(encoding="utf-8")
check("postDelayed" not in fail and "showTimer" not in fail, "Automatic retry timeout remains")


def luminance(value):
    rgb = [int(value[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    return sum((c / 12.92 if c <= .04045 else ((c + .055) / 1.055) ** 2.4) * w
               for c, w in zip(rgb, [.2126, .7152, .0722]))


palette = ET.parse(RES / "values/dialog_design.xml").getroot()
colors = {e.attrib.get("name"): e.text[-6:] for e in palette if e.tag == "color"}
for fg, bg in [("FFFFFF", colors["dialog_primary"]),
               (colors["dialog_text_secondary"], "FFFFFF"),
               (colors["dialog_danger"], "FFFFFF")]:
    low, high = sorted([luminance(fg), luminance(bg)])
    ratio = (high + .05) / (low + .05)
    check(ratio >= 4.5, f"Insufficient contrast: {fg}/{bg}: {ratio:.2f}")
print("PASS: XML, layout variants, exit consolidation, rate binding, retry timing and text contrast")
