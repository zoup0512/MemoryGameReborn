#!/usr/bin/env python3
"""Restore symbolic resource references in the decompiled memory-game sources.

1. Parses res/values/public.xml (apktool output) which maps original resource
   IDs (0x7f........) to (type, name).
2. Rewrites hardcoded integer resource IDs in Java sources back to
   R.<type>.<name> references, so they bind to the freshly assigned IDs of the
   rebuilt project instead of the stale original ones.
3. Emits "alias R" classes for vendored library packages whose decompiled code
   references their own package-local R class, redirecting every used entry to
   the application R class.
"""
import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(r"D:\Code\workspace\android\memory-game\memory-game\src\main")
PUBLIC = ROOT / "res" / "values" / "public.xml"
JAVA = ROOT / "java"
APP_PACKAGE = "com.memory.brain.training.games"

TYPES = ("anim", "animator", "array", "attr", "bool", "color", "dimen",
         "drawable", "id", "integer", "layout", "menu", "mipmap", "raw",
         "string", "style", "transition", "xml")

# type -> name set from public.xml (for alias validation)
names_by_type = defaultdict(set)
# decimal int -> (type, name)
id_map = {}

if PUBLIC.exists():
    for m in re.finditer(r'<public type="(\w+)" name="([^"]+)" id="0x([0-9a-fA-F]+)"\s*/>',
                         PUBLIC.read_text(encoding="utf-8")):
        rtype, name, hexid = m.group(1), m.group(2), m.group(3)
        names_by_type[rtype].add(name)
        id_map[int(hexid, 16)] = (rtype, name)

# always also index the live res tree (public.xml may be gone after dedup)
_FILE_TYPES = ("drawable", "layout", "anim", "animator", "menu", "mipmap",
               "raw", "xml", "color", "transition")
for sub in ROOT.glob("res/*"):
    if not sub.is_dir():
        continue
    rtype = sub.name.split("-")[0]
    if rtype in _FILE_TYPES:
        for f in sub.iterdir():
            if f.is_file():
                names_by_type[rtype].add(f.stem)
    elif sub.name == "values" or sub.name.startswith("values-"):
        for f in sub.iterdir():
            if not f.suffix == ".xml":
                continue
            for m in re.finditer(r'<(?:string|color|dimen|integer|bool|id|style|attr|array|integer-array|string-array)\s+name="([^"]+)"|<item\s+type="(\w+)"\s+name="([^"]+)"|<declare-styleable\s+name="([^"]+)"|<attr\s+name="([^"]+)"',
                                 f.read_text(encoding="utf-8", errors="replace")):
                if m.group(1):
                    tguess = m.group(0)[1:m.group(0).find(" ")]
                    tguess = {"integer-array": "array", "string-array": "array"}.get(tguess, tguess)
                    names_by_type[tguess].add(m.group(1))
                elif m.group(2):
                    names_by_type[m.group(2)].add(m.group(3))
                elif m.group(4):
                    names_by_type["styleable"].add(m.group(4))
                    names_by_type["styleable"].add(m.group(4))
                elif m.group(5):
                    names_by_type["attr"].add(m.group(5))

print(f"resource index: {sum(len(v) for v in names_by_type.values())} names")

# ---------- 2. rewrite hardcoded IDs in java sources ----------
dec_re = re.compile(r"(?<![\w.\"])(\d{9,10})(?![\w.\"])")
hex_re = re.compile(r"(?<![\w.\"])0[xX]7[fF]([0-9a-fA-F]{6})(?![\w.\"])")
per_file = defaultdict(int)

for path in JAVA.rglob("*.java"):
    text = path.read_text(encoding="utf-8", errors="replace")

    def dec_sub(m):
        val = int(m.group(1))
        if val in id_map and 0x7F000000 <= val <= 0x7FFFFFFF:
            rtype, name = id_map[val]
            per_file[path.name] += 1
            return f"R.{rtype}.{name}"
        return m.group(0)

    def hex_sub(m):
        val = int(m.group(1), 16)
        if val in id_map:
            rtype, name = id_map[val]
            per_file[path.name] += 1
            return f"R.{rtype}.{name}"
        return m.group(0)

    new = dec_re.sub(dec_sub, text)
    new = hex_re.sub(hex_sub, new)
    if new != text:
        path.write_text(new, encoding="utf-8")

total = sum(per_file.values())
print(f"replaced {total} hardcoded resource IDs in {len(per_file)} files")
for name, count in sorted(per_file.items(), key=lambda kv: -kv[1])[:20]:
    print(f"  {count:4d}  {name}")

# ---------- 3. generate alias R classes for vendored libs ----------
VENDOR_PACKAGES = {
    "com/roughike/bottombar": "com.roughike.bottombar",
    "com/viewpagerindicator": "com.viewpagerindicator",
    "uk/co/chrisjenx/calligraphy": "uk.co.chrisjenx.calligraphy",
    "com/wenchao/cardstack": "com.wenchao.cardstack",
    "com/github/florent37/viewanimator": "com.github.florent37.viewanimator",
    "com/activeandroid": "com.activeandroid",
    "com/squareup/picasso": "com.squareup.picasso",
    "com/github/mikephil/charting": "com.github.mikephil.charting",
    "de/hdodenhof/circleimageview": "de.hdodenhof.circleimageview",
    "me/grantland/widget": "me.grantland.widget",
}

ref_re = re.compile(r"(?<![\w.])R\.(\w+)\.([A-Za-z_][A-Za-z0-9_]*)")

# fallback: resources owned by the linked support AARs resolve to the
# original library R packages (AGP regenerates library R classes per app)
LIB_R_FALLBACK = {
    ("attr", "colorPrimary"): "android.support.v7.appcompat",
    ("attr", "selectableItemBackgroundBorderless"): "android.support.v7.appcompat",
    ("attr", "selectableItemBackground"): "android.support.v7.appcompat",
    ("attr", "colorPrimaryDark"): "android.support.v7.appcompat",
    ("attr", "colorAccent"): "android.support.v7.appcompat",
}

# styleable block names declared in the app's attrs.xml (used to decide
# whether a styleable alias entry is an int[] block or an int field)
styleable_blocks = set()
for attrs_file in ROOT.glob("res/values*/attrs*.xml"):
    for m in re.finditer(r'<declare-styleable name="([^"]+)"',
                         attrs_file.read_text(encoding="utf-8", errors="replace")):
        styleable_blocks.add(m.group(1))

for rel, package in VENDOR_PACKAGES.items():
    pkg_dir = JAVA / rel
    if not pkg_dir.is_dir():
        continue
    used = defaultdict(set)  # type -> {names}
    foreign = defaultdict(dict)  # type -> {name: origin package}
    missing = defaultdict(set)
    for path in pkg_dir.rglob("*.java"):
        for m in ref_re.finditer(path.read_text(encoding="utf-8", errors="replace")):
            rtype, name = m.group(1), m.group(2)
            if rtype not in TYPES and rtype != "styleable":
                continue
            if rtype == "styleable":
                used[rtype].add(name)  # validated later against attrs.xml
                continue
            if name in names_by_type.get(rtype, ()):
                used[rtype].add(name)
            elif (rtype, name) in LIB_R_FALLBACK:
                foreign[rtype][name] = LIB_R_FALLBACK[(rtype, name)]
            else:
                missing[rtype].add(name)
    if missing:
        print(f"WARNING {package}: unmapped R refs: {dict(missing)}")
    if not used and not foreign:
        continue
    lines = [f"package {package};", "",
             "/**",
             " * Alias R class generated for the vendored decompiled library code.",
             " * Every entry redirects to the R class that owns the resource in",
             " * the rebuilt project so IDs stay correct at compile time.",
             " */",
             "public final class R {"]
    for rtype in TYPES + ("styleable",):
        if rtype not in used and rtype not in foreign:
            continue
        lines.append(f"    public static final class {rtype} {{")
        for name in sorted(used.get(rtype, ())):
            if rtype == "styleable":
                if name in styleable_blocks:
                    lines.append(f"        public static final int[] {name} = {APP_PACKAGE}.R.styleable.{name};")
                else:
                    lines.append(f"        public static final int {name} = {APP_PACKAGE}.R.styleable.{name};")
            else:
                lines.append(f"        public static final int {name} = {APP_PACKAGE}.R.{rtype}.{name};")
        for name, origin in sorted(foreign.get(rtype, {}).items()):
            lines.append(f"        public static final int {name} = {origin}.R.{rtype}.{name};")
        lines.append("    }")
    lines.append("}")
    out = pkg_dir / "R.java"
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"alias R written: {package} ({sum(len(v) for v in used.values())} local, {sum(len(v) for v in foreign.values())} foreign)")

print("done")
