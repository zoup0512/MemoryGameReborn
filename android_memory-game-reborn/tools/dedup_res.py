#!/usr/bin/env python3
"""Remove library-provided resources from the decompiled res/ tree.

The decompiled APK merged app resources with all library resources. The
rebuilt project links the original support AARs (appcompat-v7 26.1.0 line),
so every resource those AARs provide must be removed from res/ to avoid
duplicate-resource build errors.
"""
import io
import re
import sys
import urllib.request
import zipfile
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

ROOT = Path(r"D:\Code\workspace\android\memory-game\memory-game\src\main")
RES = ROOT / "res"
AAR_DIR = Path(r"D:\Code\workspace\android\memory-game\memory-game\tools\aars")

BASE = "https://dl.google.com/dl/android/maven2/com/android/support"
AARS = [
    "appcompat-v7", "support-v4", "support-compat", "support-core-ui",
    "support-core-utils", "support-fragment", "support-media-compat",
    "support-vector-drawable", "animated-vector-drawable",
    "recyclerview-v7", "cardview-v7", "design", "transition",
]
VERSION = "26.1.0"

# value-entry tag name -> resource type
TAG_TYPES = {"string": "string", "color": "color", "dimen": "dimen",
             "integer": "integer", "bool": "bool", "id": "id",
             "style": "style", "attr": "attr", "item": None,
             "declare-styleable": None, "array": "array",
             "integer-array": "array", "string-array": "array"}

AAR_NAMES = defaultdict(set)  # type -> {names}
AAR_FILES = set()             # file resource basenames (drawable/layout/...)

AAR_DIR.mkdir(parents=True, exist_ok=True)

for lib in AARS:
    aar = AAR_DIR / f"{lib}-{VERSION}.aar"
    if not aar.exists():
        url = f"{BASE}/{lib}/{VERSION}/{lib}-{VERSION}.aar"
        print(f"downloading {url}")
        with urllib.request.urlopen(url, timeout=60) as r, open(aar, "wb") as f:
            f.write(r.read())
    zf = zipfile.ZipFile(aar)
    # 1) file-based resources
    for info in zf.infolist():
        if info.filename.startswith("res/"):
            parts = info.filename.split("/")
            if len(parts) >= 3 and parts[0] == "res":
                base = parts[-1].rsplit(".", 1)[0]
                AAR_FILES.add(base)
    # 2) public.txt (authoritative name/type list)
    if "res/public.txt" in zf.namelist():
        for line in zf.read("res/public.txt").decode("utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            rtype, name = line.split()
            AAR_NAMES[rtype].add(name)
    else:
        # fallback: parse values xml (attrs are often declared inline inside
        # declare-styleable blocks in the support libraries)
        for info in zf.infolist():
            if re.match(r"res/values[^/]*/.*\.xml$", info.filename):
                root = ET.fromstring(zf.read(info.filename))
                for el in root:
                    if el.tag == "declare-styleable":
                        for a in el.findall("attr"):
                            if a.get("name"):
                                AAR_NAMES["attr"].add(a.get("name"))
                        continue
                    rtype = el.tag if el.tag != "item" else el.get("type")
                    if rtype and el.get("name"):
                        AAR_NAMES[rtype].add(el.get("name"))
    zf.close()

total_names = sum(len(v) for v in AAR_NAMES.values())
print(f"AAR provides {total_names} named resources, {len(AAR_FILES)} file resources")

removed_files = 0
for sub in RES.iterdir():
    if not sub.is_dir() or sub.name == "values" or sub.name.startswith("values-"):
        continue
    for f in list(sub.iterdir()):
        if f.is_file() and f.stem in AAR_FILES:
            f.unlink()
            removed_files += 1
print(f"deleted {removed_files} file resources")

# values dedup with declare-styleable awareness
removed_entries = 0
removed_styleables = 0

def clean_values(path: Path):
    global removed_entries, removed_styleables
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as e:
        print(f"PARSE ERROR {path}: {e}")
        return
    # first pass: find styleable blocks to drop (they reference removed attrs)
    drop_blocks = set()
    children = list(root)
    for i, el in enumerate(children):
        if el.tag == "declare-styleable":
            for attr in el.findall("attr"):
                nm = attr.get("name", "")
                if not nm.startswith("android:") and nm in AAR_NAMES.get("attr", ()):
                    drop_blocks.add(i)
                    break
    new_children = []
    for i, el in enumerate(children):
        if i in drop_blocks:
            removed_styleables += 1
            continue
        rtype = el.tag if el.tag in TAG_TYPES and el.tag != "item" else el.get("type")
        name = el.get("name")
        if rtype and name and name in AAR_NAMES.get(rtype, ()):
            removed_entries += 1
            continue
        if el.tag in ("attr",) and name and name in AAR_NAMES.get("attr", ()):
            removed_entries += 1
            continue
        new_children.append(el)
    if len(new_children) != len(children):
        new_root = ET.Element("resources")
        new_root.extend(new_children)
        ET.ElementTree(new_root).write(path, encoding="utf-8", xml_declaration=False)

for sub in sorted(RES.iterdir()):
    if sub.is_dir() and (sub.name == "values" or sub.name.startswith("values-")):
        for f in sorted(sub.iterdir()):
            if f.suffix == ".xml":
                clean_values(f)
print(f"removed {removed_entries} value entries, {removed_styleables} styleable blocks")

pub = RES / "values" / "public.xml"
if pub.exists():
    pub.unlink()
    print("deleted public.xml")
print("done")
