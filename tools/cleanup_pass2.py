#!/usr/bin/env python3
"""Second-pass decompiler-artifact cleanup.

- Merges jadx-split named nested classes (A$B.java) back into their
  enclosing class A as static nested types. Anonymous A$N classes stay.
- Inlines string/int constants that the original compiler folded into
  classes of removed SDKs (ProfileActivity, TransportMediator,
  AppLovinEventTypes, facebook ShareConstants, fabric EventsFilesManager).
- Removes picasso's optional OkHttpDownloader (okhttp2 was stripped).
- Renames charting ObjectPool.recycle(List) to recycleAll (erasure clash).
"""
import re
from pathlib import Path

JAVA = Path(r"D:\Code\workspace\android\memory-game\memory-game\src\main\java")

# ---------- 1. merge named nested classes ----------
NESTED = [
    "com/github/florent37/viewanimator/AnimationListener$Update.java",
    "com/github/mikephil/charting/components/YAxis$AxisDependency.java",
    "com/github/mikephil/charting/components/YAxis$YAxisLabelPosition.java",
    "com/github/mikephil/charting/data/LineDataSet$Mode.java",
    "com/google/gson/Gson$FutureTypeAdapter.java",
    "com/squareup/picasso/Downloader$Response.java",
    "com/squareup/picasso/Picasso$CleanupThread.java",
    "com/squareup/picasso/Picasso$Listener.java",
    "com/squareup/picasso/Picasso$LoadedFrom.java",
    "com/squareup/picasso/Picasso$Priority.java",
    "com/squareup/picasso/Picasso$RequestTransformer.java",
]

def merge_nested(rel):
    nested_path = JAVA / rel
    parent_rel = rel.split("$")[0] + ".java"
    parent_path = JAVA / parent_rel
    ntext = nested_path.read_text(encoding="utf-8")
    ptext = parent_path.read_text(encoding="utf-8")

    # strip package + imports from nested body
    body = re.sub(r"^package [^;]+;\s*", "", ntext, flags=re.M)
    imports = re.findall(r"^import ([^;]+);", body, flags=re.M)
    body = re.sub(r"^import [^;]+;\s*\n", "", body, flags=re.M)
    body = body.strip()

    # add `static` to the nested type declaration if missing
    body = re.sub(r"^((?:public |final |abstract |)*)(class |interface |enum |@interface )",
                  lambda m: m.group(1) + ("static " if "static" not in m.group(1).split() else "") + m.group(2),
                  body, count=1, flags=re.M)

    # merge imports into parent (only ones not already imported / same-package)
    parent_pkg = re.search(r"^package ([^;]+);", ptext, flags=re.M).group(1)
    parent_imports = set(re.findall(r"^import ([^;]+);", ptext, flags=re.M))
    new_imports = []
    for imp in imports:
        if imp in parent_imports:
            continue
        if imp == parent_pkg + ".*" or imp.rsplit(".", 1)[0] == parent_pkg:
            continue
        if imp.startswith("java.lang."):
            continue
        new_imports.append(imp)
        parent_imports.add(imp)

    if new_imports:
        lines = ptext.split("\n")
        pkg_idx = next(i for i, l in enumerate(lines) if l.startswith("package "))
        insert_at = pkg_idx + 1
        while insert_at < len(lines) and lines[insert_at].strip().startswith("import "):
            insert_at += 1
        for imp in sorted(new_imports):
            lines.insert(insert_at, f"import {imp};")
        ptext = "\n".join(lines)

    # insert nested body before the final closing brace
    ptext = ptext.rstrip()
    assert ptext.endswith("}"), parent_path
    ptext = ptext[:-1].rstrip() + "\n\n    " + body.replace("\n", "\n    ") + "\n}\n"
    parent_path.write_text(ptext, encoding="utf-8")
    nested_path.unlink()
    print(f"merged {rel}")

for rel in NESTED:
    merge_nested(rel)

# ---------- 2. folded constants ----------
def patch(rel, subs, label):
    p = JAVA / rel
    t = p.read_text(encoding="utf-8")
    o = t
    for old, new in subs:
        t = t.replace(old, new)
    if t != o:
        p.write_text(t, encoding="utf-8")
        print(f"patched {label}")
    else:
        print(f"NO CHANGE: {label}")

patch("com/google/gson/stream/JsonReader.java", [
    ("import com.cube.memorygames.activity.ProfileActivity;\n", ""),
    ("ProfileActivity.REQUEST_CODE_UCROP", "125"),
    ("ProfileActivity.REQUEST_CODE_ASK_PERMISSIONS", "123"),
], "JsonReader constants")

patch("com/github/mikephil/charting/utils/ColorTemplate.java", [
    ("import android.support.v4.media.TransportMediator;\n", ""),
    ("TransportMediator.KEYCODE_MEDIA_RECORD", "130"),
], "ColorTemplate constant")

patch("com/squareup/picasso/MediaStoreRequestHandler.java", [
    ("import com.applovin.sdk.AppLovinEventTypes;\n", ""),
    ("import com.facebook.share.internal.ShareConstants;\n", ""),
    ("AppLovinEventTypes.USER_VIEWED_CONTENT", '"content"'),
    ("ShareConstants.WEB_DIALOG_PARAM_MEDIA", '"media"'),
], "MediaStoreRequestHandler constants")

patch("com/squareup/picasso/ContactsPhotoRequestHandler.java", [
    ("import com.applovin.sdk.AppLovinEventTypes;\n", ""),
    ("AppLovinEventTypes.USER_VIEWED_CONTENT", '"content"'),
], "ContactsPhotoRequestHandler constant")

for rel in ["com/google/gson/FieldNamingPolicy.java",
            "com/google/gson/internal/bind/TypeAdapters.java"]:
    patch(rel, [
        ("import io.fabric.sdk.android.services.events.EventsFilesManager;\n", ""),
        ("EventsFilesManager.ROLL_OVER_FILE_NAME_SEPARATOR", '"_"'),
    ], f"{rel} fabric constant")

# ---------- 3. picasso: drop OkHttpDownloader ----------
okhttp_downloader = JAVA / "com/squareup/picasso/OkHttpDownloader.java"
if okhttp_downloader.exists():
    okhttp_downloader.unlink()
    print("deleted OkHttpDownloader.java")

patch("com/squareup/picasso/Utils.java", [
    ("            return new OkHttpDownloader(context);", "            return new UrlConnectionDownloader(context);"),
], "Utils default downloader")

# ---------- 4. ObjectPool erasure clash ----------
patch("com/github/mikephil/charting/utils/ObjectPool.java", [
    ("public synchronized void recycle(List<T> objects) {",
     "public synchronized void recycleAll(List<T> objects) {"),
], "ObjectPool recycleAll")
for f in JAVA.glob("com/github/mikephil/charting/**/*.java"):
    t = f.read_text(encoding="utf-8")
    o = t
    t = t.replace(".recycle((List) instances)", ".recycleAll((List) instances)")
    t = t.replace(".recycle((List)", ".recycleAll((List)")
    if t != o:
        f.write_text(t, encoding="utf-8")
        print(f"recycle->recycleAll in {f.name}")

print("done")
