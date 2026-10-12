#!/usr/bin/env python3
"""Replace jadx-mangled vendored libraries with their official sources.

All downloads go through fastly.jsdelivr.net (reachable from this network).
Libraries replaced: MPAndroidChart, picasso, BottomBar, ViewPagerIndicator,
viewanimator, calligraphy. Kept: activeandroid (already repaired), the
 AutofitTextView / CircleImageView pair (fetched earlier, clean).
"""
import json
import os
import shutil
import urllib.request
from pathlib import Path

JAVA = Path(r"D:\Code\workspace\android\memory-game\memory-game\src\main\java")
API = "https://data.jsdelivr.com/v1/package/gh"
CDN = "https://fastly.jsdelivr.net/gh"

REPOS = [
    # (repo@tag, source-root-in-repo, target-package-dir)
    ("PhilJay/MPAndroidChart@v3.0.2", "MPChartLib/src/main/java/com/github/mikephil/charting", "com/github/mikephil/charting"),
    ("square/picasso@picasso-parent-2.5.2", "picasso/src/main/java/com/squareup/picasso", "com/squareup/picasso"),
    ("roughike/BottomBar@2.3.0", "bottom-bar/src/main/java/com/roughike/bottombar", "com/roughike/bottombar"),
    ("JakeWharton/ViewPagerIndicator@2.4.1", "library/src/com/viewpagerindicator", "com/viewpagerindicator"),
    ("florent37/ViewAnimator@master", "viewanimator/src/main/java/com/github/florent37/viewanimator", "com/github/florent37/viewanimator"),
    ("chrisjenx/Calligraphy@2.2.0", "calligraphy/src/main/java/uk/co/chrisjenx/calligraphy", "uk/co/chrisjenx/calligraphy"),
]

os.environ["PYTHONHTTPSVERIFY"] = "1"
opener = urllib.request.build_opener()
opener.addheaders = [("User-Agent", "Mozilla/5.0")]

def fetch(url, timeout=60):
    with opener.open(url, timeout=timeout) as r:
        return r.read()

for repo, srcroot, pkgdir in REPOS:
    listing_url = f"{API}/{repo}/flat"
    try:
        data = json.loads(fetch(listing_url, 90))
    except Exception as e:
        print(f"LIST FAIL {repo}: {e}")
        continue
    files = [f["name"] for f in data.get("files", []) if f["name"].endswith(".java") and ("/" + srcroot + "/") in f["name"]]
    if not files:
        print(f"NO FILES for {repo} (root {srcroot}) — check path")
        continue
    target = JAVA / pkgdir
    if target.exists():
        shutil.rmtree(target)
    target.mkdir(parents=True)
    ok, fail = 0, 0
    for name in files:
        rel_in_pkg = name.split("/" + srcroot + "/")[1]
        dest = target / rel_in_pkg
        dest.parent.mkdir(parents=True, exist_ok=True)
        url = f"{CDN}/{repo}{name}"
        try:
            dest.write_bytes(fetch(url, 60))
            ok += 1
        except Exception as e:
            print(f"  DL FAIL {name}: {e}")
            fail += 1
    print(f"{repo}: {ok} files fetched, {fail} failed -> {pkgdir}")

print("done")
