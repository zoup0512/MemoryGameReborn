#!/usr/bin/env python3
"""Restore repository Android assets with lossless size optimizations.

Python + Pillow + fontTools + ffmpeg. All bitmaps except the app icon become
lossless WebP at original dimensions; referenced Roboto fonts are subset
to the characters used by ArkTS; sound effects become mono MP3 VBR q7. Android
vectors become SVG without changing path geometry or colors. Stateful/stretchable
XML is parsed only to build SVG media; its raw XML copies and the drawable JSON
description are not packaged. Only GameCatalog.media is regenerated; game
parameters and UI code are untouched.
No network, purchases, dependency installation, config or Android source edits.
The app icon comes from the local 1024px master in assets/.
--check verifies exact reproducibility without writing or imposing a HAP budget.
--icons-only updates or checks the app icons without audio/font dependencies.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import io
import json
from pathlib import Path
import re
import shutil
import subprocess
import wave
import xml.etree.ElementTree as ET

from PIL import Image

HARMONY = Path(__file__).resolve().parents[1]
REPO = HARMONY.parent
ANDROID = REPO / "android_memory-game-reborn/src/main"
JAVA = ANDROID / "java/com/cube/memorygames"
RES = ANDROID / "res"
MEDIA = HARMONY / "entry/src/main/resources/base/media"
RAW = HARMONY / "entry/src/main/resources/rawfile"
CATALOG = HARMONY / "entry/src/main/ets/core/GameCatalog.ets"
MANIFEST = HARMONY / "resources-manifest.json"
APP_MEDIA = HARMONY / "AppScope/resources/base/media"
APP_ICON = HARMONY / "assets/app-icon-1024.png"
SOURCE_POLICY = "Repository Android assets and local 1024px app icon master; no network or currency/purchase functionality"
ICON_SOURCE = {"path": APP_ICON.relative_to(REPO).as_posix(),
               "size": [1024, 1024], "generator": "harmony_memory-game-reborn/tools/render_app_icon.py"}
EXPECTED = {"tutorial": 63, "thumbnail": 23, "icon": 94, "app_icon": 1}
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
UI_BITMAPS = """arrow blue_background_2 menu_qhd logo home info next nextlevel
play play_press play_select play_unselected roundedbutton roundedbutton_inactive
speaker mute soundon soundoff rightanswer wronganswer point_gray point_red
ic_arrow_back_white ic_humburger ic_play ic_star_small icon_1 icon_2 icon_3 icon_4
cupdialog paperplane_cyan paperplane_orange rain_cloud square laser laser_1 laser_2
laser_3 laser_4 laser_5 laser_6 up up2 down down2 left left2 right right2""".split()
UI_XML = """ic_pause ic_pause_new ic_exit ic_reload ic_cup ic_star ic_menu
ic_challenge ic_icon_daily_workout ic_play_rounded_button ic_questions_circular_button
ic_life ic_lock ic_check ic_close ic_ok ic_game12_cancel ic_game12_tick
ic_game12_cancel_white ic_game12_tick_white ic_replay_arrow ic_direction ic_bolt
ic_podium ic_ranking_history doodle_26 doodle_31 doodle_33 doodle_42 button_play
button_tutorial button_continue button_count button_sounds_on_off home_selectable
next_level_selectable dialog_primary_button dialog_secondary_button dialog_surface
drawable_background_dialog drawable_background_fail_dialog
drawable_background_fail_dialog_inner drawable_panel_view_pager_item_text_area
drawable_babble drawable_white_circle_blue_check drawable_progress_circle
level_number start_level_info_background suggestion_background horizontal_progress
progess_drawable progressbar progress progress_statistics background_game11_white
background_game11_press background_game11_wrong background_game12_white
text_compare_background_equals text_compare_background_hidden_success
text_compare_background_wrong text_compare_equals_background_wrong
text_compare_equals_hidden_success text_compare_equals_success game17_circle""".split()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def relative(path: Path) -> str:
    return path.relative_to(REPO).as_posix()


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def evidence(path: Path, marker: str) -> dict:
    content = text(path)
    require(marker in content, f"Android source changed: {relative(path)} / {marker}")
    return {"path": relative(path), "line": content[:content.index(marker)].count("\n") + 1,
            "marker": marker}


def xml_values(path: Path) -> dict[str, str]:
    return {n.attrib["name"]: "".join(n.itertext()) for n in ET.parse(path).getroot()}


def map_entries(source: str, field: str) -> dict[str, str]:
    match = re.search(r"private Map<[^\n]+> " + field + r" =.*?\n    };", source, re.S)
    require(match is not None, f"Missing Games.java map: {field}")
    return dict(re.findall(r"put\(Games.this.(\w+), (.*?)\);", match.group(0)))


def source_catalog() -> tuple[list[dict], list[dict]]:
    path = JAVA / "Games.java"
    source = text(path)
    zh_path = RES / "values-zh/strings.xml"
    strings = xml_values(zh_path)
    colors_path = RES / "values/colors.xml"
    colors = xml_values(colors_path)
    declarations = {m[0]: (int(m[1]), m[2], int(m[3])) for m in re.findall(
        r'private GameInfo (\w+) = new GameInfo\(R.string.game_name_(\d+), "[^"]+", '
        r'R.drawable.(\w+), (\d+),', source)}
    sprint = re.search(r"private List<GameInfo> sprintGames =.*?\n    };", source, re.S)
    require(sprint is not None, "Missing Android sprint order")
    order = re.findall(r"add\(Games.this.(\w+)\)", sprint.group(0))
    images = map_entries(source, "tutorialImages")
    tutorials = map_entries(source, "tutorialStrings")
    categories = map_entries(source, "gameCategories")
    trophies = map_entries(source, "trophyLevels")
    activities = map_entries(source, "gameActivities")
    base = JAVA / "games/Game1MemoryGridActivity.java"
    starting = text(base).split("protected int getStartingLevel()", 1)[1].split(
        "public void restartLevel", 1)[0]
    # All ids explicitly tested here return 1 in offline Sprint, including
    # symmetry (Android id 20), whose exception applies only to Challenge.
    fixed_ids = {int(n) + 1 for n in re.findall(r'id.equals\("(\d+)"\)', starting)}
    require(fixed_ids == {4, 7, 9, 10, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23},
            "Review changed Android getStartingLevel() policy")
    # Actual OFFLINE whole-session timers, not GameInfo.onlineLevelTime.
    whole_seconds = {7: 61, 10: 60, 13: 60, 14: 60, 15: 30, 16: 30,
                     17: 60, 18: 60, 19: 60, 20: 30, 21: 80, 22: 80, 23: 50}
    timer_markers = {7: "this.timeLeft = 61000;", 10: "this.gameTime = 60000;",
                     13: "private static final int GAME_TIME = 60000;",
                     14: "this.gameTime = 60000;", 15: "this.gameTime = 30000;",
                     16: ": 30000L;", 17: "this.gameTime = 60000;",
                     18: "this.gameTime = 60000;", 19: "this.gameTime = 60000;",
                     20: ": 30000L;", 21: "this.gameTime = 80000;",
                     22: "private static final int GAME_TIME = 80000;",
                     23: "this.gameTime = 50000;"}
    require(len(order) == len(declarations) == 23, "Expected 23 Android games")
    games: list[dict] = []
    assets: dict[str, dict] = {}
    for position, key in enumerate(order, 1):
        game_id, thumbnail, online_seconds = declarations[key]
        category = categories[key].split(".")[-1]
        image_names = re.findall(r"R.drawable.(\w+)", images[key])
        string_names = re.findall(r"R.string.(\w+)", tutorials[key])
        require(len(image_names) == len(string_names), f"Tutorial mismatch: {key}")
        activity = JAVA / ("games/" + activities[key].split(".")[0] + ".java")
        fields = {
            "id": game_id, "name": strings[f"game_name_{game_id}"],
            "category": strings[category], "color": "#" + colors[category][-6:].upper(),
            "thumbnail": thumbnail, "tutorialImages": image_names,
            "tutorialText": [strings[n].replace(r"\n", "\n").replace(r"\'", "'")
                             for n in string_names],
            "duration": whole_seconds.get(game_id, 0),
            "adaptive": game_id not in fixed_ids,
            "trophies": [int(n) for n in re.findall(r"Integer.valueOf\((\d+)\)", trophies[key])],
        }
        require(len(fields["trophies"]) == 4, f"Trophies missing for {key}")
        origins = {
            "sprintOrder": evidence(path, f"add(Games.this.{key})"),
            "idNameThumbnailOnlineDuration": evidence(path, f"private GameInfo {key} ="),
            "name": evidence(zh_path, f'name="game_name_{game_id}"'),
            "categoryMapping": evidence(path, f"put(Games.this.{key}, Games.this.{category})"),
            "category": evidence(zh_path, f'name="{category}"'),
            "color": evidence(colors_path, f'name="{category}"'),
            "tutorialImages": evidence(path, f"put(Games.this.{key}, {images[key]});"),
            "tutorialText": [evidence(zh_path, f'name="{n}"') for n in string_names],
            "trophies": evidence(path, f"put(Games.this.{key}, {trophies[key]});"),
            "adaptive": evidence(base, "protected int getStartingLevel()"),
            "duration": evidence(activity, timer_markers[game_id]) if game_id in timer_markers
                else evidence(activity if game_id in (11, 12) else base,
                              "protected class UserInputEnabledFlowState" if game_id in (11, 12)
                              else "protected void startOnlineLevelTimer()"),
        }
        item = {"sprintPosition": position, "androidId": str(game_id - 1),
                "androidKey": key, "androidActivity": relative(activity),
                "androidOnlineDurationSeconds": online_seconds, "game": fields, "sources": origins,
                "durationScope": "whole_session" if fields["duration"] else "no_whole_session_timer"}
        if game_id in (11, 12):
            item["perQuestionSeconds"] = {"levels1to10": 4 if game_id == 11 else 6,
                                          "levels11plus": 8 if game_id == 11 else 10}
            if game_id == 12:
                item["sources"]["perQuestionConstant"] = evidence(
                    ANDROID / "java/com/facebook/login/widget/ToolTipPopup.java",
                    "public static final long DEFAULT_POPUP_DISPLAY_TIME = 6000;")
        games.append(item)
        for name in image_names:
            assets[name] = {"name": name, "androidName": name, "kind": "tutorial"}
        assets[thumbnail] = {"name": thumbnail, "androidName": thumbnail, "kind": "thumbnail"}
    icon_names = {p.stem for p in RES.glob("drawable*/icg_*.png")}
    require(icon_names == {f"icg_{i}" for i in range(1, 95)}, "Expected icg_1 through icg_94")
    for name in sorted(icon_names):
        assets[name] = {"name": name, "androidName": name, "kind": "icon"}
    assets["app_icon"] = {"name": "app_icon", "androidName": "ic_launcher", "kind": "app_icon"}
    require(dict(Counter(a["kind"] for a in assets.values())) == EXPECTED,
            "Image inventory differs from 63 tutorial / 23 thumbnail / 94 icon / 1 app icon")
    return games, sorted(assets.values(), key=lambda a: a["name"])


def bitmap_source(name: str) -> Path:
    candidates = list(RES.glob(f"drawable*/{name}.png"))
    require(bool(candidates), f"Missing Android PNG: {name}")
    def rank(path: Path) -> tuple[int, str]:
        with Image.open(path) as image:
            return image.width * image.height, path.as_posix()
    return max(candidates, key=rank)


def copied(source: Path, output: Path, **metadata) -> tuple[dict, bytes]:
    data = source.read_bytes()
    return ({**metadata, "source": relative(source), "sourceSha256": digest(data),
             "output": relative(output), "sha256": digest(data), "bytes": len(data),
             "encoding": "original-bytes"}, data)


def webp_lossless(source: Path) -> tuple[bytes, list[int], list[int]]:
    with Image.open(source) as image:
        require(image.format == "PNG", f"Not a PNG: {source}")
        image.load()
        source_size = list(image.size)
        if image.mode == "P":
            rgba = image.convert("RGBA")
            image = rgba if rgba.getchannel("A").getextrema()[0] < 255 else rgba.convert("RGB")
        elif image.mode == "LA":
            image = image.convert("RGBA")
        elif image.mode not in ("RGB", "RGBA"):
            image = image.convert("RGB")
        buffer = io.BytesIO()
        image.save(buffer, format="WEBP", lossless=True, method=4)
        return buffer.getvalue(), source_size, list(image.size)


def image_plan(assets: list[dict]) -> list[tuple[dict, bytes]]:
    result = []
    for asset in assets:
        source = APP_ICON if asset["kind"] == "app_icon" else bitmap_source(asset["androidName"])
        if asset["kind"] == "app_icon":
            with Image.open(source) as image:
                require(image.format == "PNG", f"Not a PNG: {source}")
                image.load()
                require(image.size == (1024, 1024), "App icon master must be 1024x1024")
                require(image.mode == "RGB", "App icon master must be opaque RGB")
                size = list(image.size)
            result.append(copied(source, MEDIA / (asset["name"] + ".png"), **asset,
                                 sourceSize=size, size=size, format="PNG"))
            continue
        data, source_size, size = webp_lossless(source)
        result.append(({**asset, "source": relative(source),
                        "sourceSha256": digest(source.read_bytes()),
                        "output": relative(MEDIA / (asset["name"] + ".webp")),
                        "sha256": digest(data), "bytes": len(data),
                        "sourceSize": source_size, "size": size, "format": "WEBP",
                        "encoding": "lossless WebP, original dimensions"}, data))
    return result


def audio_info(source: Path) -> dict:
    if source.suffix == ".wav":
        with wave.open(str(source), "rb") as wav:
            require(wav.getcomptype() == "NONE", f"Expected original PCM WAV: {source}")
            rate, channels, bits = wav.getframerate(), wav.getnchannels(), wav.getsampwidth() * 8
            return {"codec": f"pcm_s{bits}le", "sampleRate": rate, "channels": channels,
                    "bitsPerSample": bits, "bitrate": rate * channels * bits,
                    "frames": wav.getnframes()}
    # Report the original MPEG frame header, never re-encode or discard ID3 data.
    data = source.read_bytes()
    start = 0
    if data[:3] == b"ID3":
        start = 10 + sum((b & 127) << shift for b, shift in zip(data[6:10], (21, 14, 7, 0)))
    for offset in range(start, len(data) - 4):
        header = int.from_bytes(data[offset:offset + 4], "big")
        version, layer = (header >> 19) & 3, (header >> 17) & 3
        bitrate_index, rate_index = (header >> 12) & 15, (header >> 10) & 3
        if header >> 21 != 0x7FF or version == 1 or layer != 1 or not 0 < bitrate_index < 15 or rate_index == 3:
            continue
        rates = [44100, 48000, 32000]
        bitrates = ([0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320]
                    if version == 3 else [0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160])
        return {"codec": "mp3", "sampleRate": rates[rate_index] // {3: 1, 2: 2, 0: 4}[version],
                "channels": 1 if (header >> 6) & 3 == 3 else 2,
                "bitrate": bitrates[bitrate_index] * 1000, "bitrateScope": "first MPEG audio frame; original stream preserved"}
    raise RuntimeError(f"No MPEG Layer III header: {source}")


def transcode_audio(source: Path) -> bytes:
    executable = shutil.which("ffmpeg")
    require(executable is not None, "ffmpeg is required: install ffmpeg and add it to PATH")
    command = [executable, "-y", "-v", "error", "-i", str(source), "-vn", "-map_metadata", "-1",
               "-ac", "1", "-ar", "44100", "-c:a", "libmp3lame", "-q:a", "7",
               "-write_id3v1", "0", "-id3v2_version", "0", "-f", "mp3", "pipe:1"]
    result = subprocess.run(command, capture_output=True)
    require(result.returncode == 0,
            f"ffmpeg failed for {source}: {result.stderr.decode(errors='replace')}")
    return result.stdout


def audio_plan() -> list[tuple[dict, bytes]]:
    result = []
    for name, source_name, output_name, sound in [("error", "error.mp3", "error.mp3", "FAIL"),
                                                   ("tap", "tap.wav", "tap.mp3", "TAP"),
                                                   ("win", "win.mp3", "win.mp3", "WIN"),
                                                   ("wrong", "wrong.wav", "wrong.mp3", "WRONG")]:
        source = RES / "raw" / source_name
        data = transcode_audio(source)
        result.append(({"name": name, "androidSound": sound,
                        "source": relative(source), "sourceSha256": digest(source.read_bytes()),
                        "output": relative(RAW / output_name), "sha256": digest(data), "bytes": len(data),
                        "codec": "mp3", "sampleRate": 44100, "channels": 1,
                        "encoding": "MP3 mono VBR q7 (LAME), 44100Hz, metadata stripped",
                        "mappingSource": evidence(JAVA / "SoundUtils.java", f"R.raw.{name}")}, data))
    return result


def used_characters() -> set[str]:
    source_text = "\n".join(text(path) for path in (HARMONY / "entry/src/main/ets").rglob("*.ets"))
    return {chr(code) for code in range(0x20, 0x7f)} | {ch for ch in source_text if ord(ch) > 0x7f}


def subset_font(source: Path, characters: set[str]) -> tuple[bytes, int, int]:
    try:
        from fontTools.subset import Options, Subsetter
        from fontTools.ttLib import TTFont
    except ImportError as error:
        raise SystemExit("fontTools is required: python -m pip install fonttools") from error
    font = TTFont(source, recalcTimestamp=False)
    cmap = set()
    for table in font["cmap"].tables:
        cmap.update(table.cmap)
    unicodes = sorted(ord(ch) for ch in characters if ord(ch) in cmap)
    options = Options()
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.notdef_outline = True
    options.recommended_glyphs = True
    options.drop_tables = ["DSIG"]
    subsetter = Subsetter(options=options)
    subsetter.populate(unicodes=unicodes)
    subsetter.subset(font)
    buffer = io.BytesIO()
    font.save(buffer)
    return buffer.getvalue(), len(unicodes), len(cmap)


def font_plan() -> list[tuple[dict, bytes]]:
    references: dict[str, list[dict]] = {}
    for path in sorted(JAVA.rglob("*.java")):
        for line_number, line in enumerate(text(path).splitlines(), 1):
            for name in re.findall(r'"(Roboto-[^"/]+\.ttf)"', line):
                references.setdefault(name, []).append({"path": relative(path), "line": line_number,
                                                        "marker": line.strip()})
    require(set(references) == {f"Roboto-{weight}.ttf" for weight in ("Light", "Regular", "Medium", "Bold")},
            "Review changed Android font usage")
    characters = used_characters()
    result = []
    for name, origins in sorted(references.items()):
        source = ANDROID / "assets" / name
        data, glyphs, source_glyphs = subset_font(source, characters)
        result.append(({"name": Path(name).stem, "source": relative(source),
                        "sourceSha256": digest(source.read_bytes()),
                        "output": relative(RAW / "fonts" / name),
                        "sha256": digest(data), "bytes": len(data),
                        "encoding": "TrueType subset: ASCII + used non-ASCII glyphs, original outlines/features",
                        "glyphs": glyphs, "sourceGlyphs": source_glyphs,
                        "androidReferences": origins}, data))
    return result


def resource_values() -> dict[str, str]:
    result = {}
    for path in sorted((RES / "values").glob("*.xml")):
        for node in ET.parse(path).getroot():
            if node.tag in {"color", "dimen"}:
                result[f"@{node.tag}/{node.attrib['name']}"] = "".join(node.itertext()).strip()
    return result


def resolve_value(value: str, values: dict[str, str]) -> str:
    builtins = {"@android:color/white": "#ffffffff", "@android:color/black": "#ff000000",
                "@android:color/transparent": "#00000000"}
    seen = set()
    while value in values or value in builtins:
        require(value not in seen, f"Cyclic Android value {value}")
        seen.add(value)
        value = builtins[value] if value in builtins else values[value]
    require(not value.startswith(("@color/", "@dimen/", "@android:color/")), f"Unresolved value {value}")
    return value


def resolved_node(node: ET.Element, values: dict[str, str]) -> dict:
    return {"tag": node.tag, "attributes": {k.removeprefix(ANDROID_NS): resolve_value(v, values)
            for k, v in node.attrib.items()}, "children": [resolved_node(c, values) for c in node]}


def svg_color(value: str) -> tuple[str, str]:
    require(bool(re.fullmatch(r"#[0-9a-fA-F]{6}|#[0-9a-fA-F]{8}", value)), f"Unsupported SVG color {value}")
    return ("#" + value[-6:], str(int(value[1:3], 16) / 255)) if len(value) == 9 else (value, "1")


def vector_svg(node: ET.Element, values: dict[str, str]) -> bytes:
    def attr(item: ET.Element, name: str) -> str:
        return item.attrib[ANDROID_NS + name]
    allowed = {"width", "height", "viewportWidth", "viewportHeight", "alpha"}
    require({k.removeprefix(ANDROID_NS) for k in node.attrib} <= allowed, "Unsupported vector attribute")
    width = re.sub(r"(?:dip|dp)$", "", attr(node, "width"))
    height = re.sub(r"(?:dip|dp)$", "", attr(node, "height"))
    root = ET.Element("svg", {"xmlns": "http://www.w3.org/2000/svg", "width": width, "height": height,
                               "viewBox": f"0 0 {attr(node, 'viewportWidth')} {attr(node, 'viewportHeight')}",
                               "opacity": node.attrib.get(ANDROID_NS + "alpha", "1")})
    mapping = {"pathData": "d", "fillAlpha": "fill-opacity", "strokeAlpha": "stroke-opacity",
               "strokeWidth": "stroke-width", "strokeLineCap": "stroke-linecap",
               "strokeLineJoin": "stroke-linejoin", "strokeMiterLimit": "stroke-miterlimit"}
    for child in node:
        require(child.tag == "path", "Vector groups/clips require explicit conversion, never silently flatten")
        attributes = {"fill": "#000000"}
        for key, value in child.attrib.items():
            key, value = key.removeprefix(ANDROID_NS), resolve_value(value, values)
            if key in {"fillColor", "strokeColor"}:
                name = "fill" if key == "fillColor" else "stroke"
                color, opacity = svg_color(value)
                attributes[name] = color
                attributes[name + "-opacity"] = opacity
            elif key == "fillType":
                attributes["fill-rule"] = "evenodd" if value == "evenOdd" else "nonzero"
            else:
                require(key in mapping, f"Unsupported vector path attribute: {key}")
                attributes[mapping[key]] = value
        for name in ("fill", "stroke"):
            alpha = child.attrib.get(ANDROID_NS + name + "Alpha")
            color = child.attrib.get(ANDROID_NS + name + "Color")
            if alpha and color:
                attributes[name + "-opacity"] = str(float(alpha) * float(svg_color(resolve_value(color, values))[1]))
        ET.SubElement(root, "path", attributes)
    return ET.tostring(root, encoding="utf-8", xml_declaration=True) + b"\n"


def ui_plan() -> list[tuple[dict, bytes]]:
    bitmaps = set(UI_BITMAPS)
    # All four Android trophy tiers are artwork, not a currency/purchase system.
    bitmaps.update(p.stem for p in RES.glob("drawable*/cup_*.png"))
    pending = set(UI_XML)
    pending.update(p.stem for p in (RES / "drawable").glob("*.xml") if p.stem.startswith(
        ("drawable_state_", "drawable_circle_", "drawable_cell_", "background_cell_twofold", "progress_item_")))
    values, xmls = resource_values(), {}
    while pending:
        name = sorted(pending)[0]
        pending.remove(name)
        if name in xmls:
            continue
        paths = sorted(RES.glob(f"drawable*/{name}.xml"))
        require(bool(paths), f"Missing Android drawable XML: {name}")
        source = paths[0]
        node = ET.parse(source).getroot()
        xmls[name] = (source, node)
        for dependency in re.findall(r"@drawable/(\w+)", text(source)):
            if list(RES.glob(f"drawable*/{dependency}.xml")):
                pending.add(dependency)
            else:
                bitmaps.add(dependency)
    images = image_plan([{"name": name, "androidName": name, "kind": "ui_bitmap"} for name in sorted(bitmaps)])
    for name, (source, node) in sorted(xmls.items()):
        if node.tag != "vector":
            continue
        data = vector_svg(node, values)
        images.append(({"name": name, "androidName": name, "kind": "ui_vector", "format": "SVG",
                        "source": relative(source), "sourceSha256": digest(source.read_bytes()),
                        "output": relative(MEDIA / (name + ".svg")), "encoding": "svg-path-preserving",
                        "sha256": digest(data), "bytes": len(data)}, data))
    return images


def media_function(names: list[str]) -> str:
    lines = ["export function media(name: string): Resource {", "  switch (name) {"]
    for name in sorted(names):
        if name == "app_icon":
            lines.append("    case 'ic_launcher':")
        lines.append(f"    case '{name}': return $r('app.media.{name}');")
    lines += ["    default: return $r('app.media.app_icon');", "  }", "}"]
    return "\n".join(lines)


def catalog_text(names: list[str]) -> str:
    content = text(CATALOG)
    pattern = r"export function media\(name: string\): Resource \{.*?^\}"
    require(len(re.findall(pattern, content, re.M | re.S)) == 1, "Expected one media mapping")
    return re.sub(pattern, lambda _: media_function(names), content, flags=re.M | re.S)


def json_bytes(value: dict) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def summary(groups: dict[str, list[tuple[dict, bytes]]]) -> dict:
    result = {"totalAssetBytes": 0}
    for key, items in groups.items():
        result[key + "Count"] = len(items)
        result[key + "Bytes"] = sum(len(data) for _, data in items)
        result["totalAssetBytes"] += result[key + "Bytes"]
    result["imageCounts"] = dict(Counter(r["kind"] for r, _ in groups["images"]))
    return result


def build_plan(games: list[dict], assets: list[dict]) -> tuple[dict, dict[str, bytes]]:
    images = image_plan(assets)
    images += ui_plan()
    icon = next(record for record, _ in images if record["name"] == "app_icon")
    # Preserve AppScope's resource name so app.json5 does not change.
    app_icons = [copied(REPO / icon["source"], APP_MEDIA / "game_icon.png", name="game_icon")]
    groups = {"images": images, "audio": audio_plan(), "fonts": font_plan(),
              "appIcons": app_icons}
    outputs = {record["output"]: data for items in groups.values() for record, data in items}
    names = [record["name"] for record, _ in images]
    require(len(names) == len(set(names)), "Duplicate image resource names")
    require(dict(Counter(record["kind"] for record, _ in images[:181])) == EXPECTED,
            "Original 181-image inventory changed")
    manifest = {
        "version": 2, "generator": relative(Path(__file__)),
        "sourcePolicy": SOURCE_POLICY,
        "sizePolicy": {"limits": None, "note": "Lossless image/font optimization; original dimensions preserved"},
        "imagePolicy": {"sourceSelection": "App icon: local 1024px master; other PNGs: largest Android pixel area, deterministic lexical tie-break",
                        "resize": False, "encoding": "All bitmaps except app_icon: lossless WebP at original dimensions; app icon: PNG; vectors: path/color-preserving SVG"},
        "audioPolicy": {"encoding": "MP3 mono VBR q7 (LAME), 44100Hz, metadata stripped",
                        "filenames": {"error": "error.mp3", "tap": "tap.mp3", "win": "win.mp3", "wrong": "wrong.mp3"},
                        "integration": "SoundService opens the four MP3 rawfiles"},
        "fontPolicy": "Referenced Roboto fonts subset to ArkTS-used glyphs; TTF with original outlines and layout features",
        "drawablePolicy": {"vectors": "media SVG; exact path geometry, original dimensions and ARGB alpha",
                           "statefulAndStretchable": "Parsed only to build SVG media; raw Android XML and drawable JSON are not packaged",
                           "currency": "No coin, reward, online, purchase, replay-for-currency assets or behavior introduced; gold trophy tier is original artwork"},
        "catalogPolicy": {"writeScope": "media function only; GAMES, game parameters and other code preserved"},
        "applicationIconSource": ICON_SOURCE,
        **{key: [record for record, _ in items] for key, items in groups.items()},
        "mediaMappings": {**{name: "app.media." + name for name in sorted(names)}, "ic_launcher": "app.media.app_icon"},
        "games": games, "summary": summary(groups),
    }
    outputs[relative(CATALOG)] = catalog_text(names).encode("utf-8")
    return manifest, outputs


def existing_records(manifest: dict) -> list[dict]:
    return [record for key in ("images", "audio", "fonts", "drawables", "appIcons")
            for record in manifest.get(key, [])]


def cleanup_plan(previous: dict, outputs: dict[str, bytes]) -> list[Path]:
    require(previous.get("generator") == relative(Path(__file__)), "Unrecognized old manifest generator")
    allowed = {MEDIA.resolve(), RAW.resolve(), APP_MEDIA.resolve()}
    obsolete = []
    for record in existing_records(previous):
        path = REPO / record["output"]
        if record["output"] in outputs or not path.exists():
            continue
        require(any(path.resolve().is_relative_to(root) for root in allowed), "Unsafe old output path")
        require(digest(path.read_bytes()) == record["sha256"],
                f"Refusing to delete modified/unowned old asset: {relative(path)}")
        obsolete.append(path)
    # v1 omitted AppScope: prove ownership by exact hash of generated app_icon.
    legacy_icon = APP_MEDIA / "game_icon.webp"
    if legacy_icon.exists() and legacy_icon not in obsolete:
        previous_icon = next((x for x in previous.get("images", []) if x["name"] == "app_icon"), None)
        require(previous_icon is not None and digest(legacy_icon.read_bytes()) == previous_icon["sha256"],
                "Refusing to delete unrecognized AppScope icon")
        obsolete.append(legacy_icon)
    obsolete_set = {p.resolve() for p in obsolete}
    # Never glob-delete resources. Unowned same-stem files are explicit errors.
    for output in outputs:
        path = REPO / output
        if path.parent.resolve() not in {MEDIA.resolve(), APP_MEDIA.resolve()}:
            continue
        for existing in path.parent.glob(path.stem + ".*"):
            require(existing == path or existing.resolve() in obsolete_set,
                    f"Conflicting unowned media resource: {relative(existing)}")
    return obsolete


def validate(manifest: dict, expected: dict, outputs: dict[str, bytes]) -> None:
    require(manifest == expected, "Manifest differs from source plan")
    for name, data in outputs.items():
        path = REPO / name
        require(path.is_file() and path.read_bytes() == data, f"Output differs from source plan: {name}")
    for directory in (MEDIA, APP_MEDIA):
        names = [p.stem for p in directory.iterdir() if p.is_file()]
        require(len(names) == len(set(names)), f"Same-name media conflict in {relative(directory)}")
    for name in ("tap", "wrong"):
        require(not (RAW / (name + ".wav")).exists(), f"Obsolete WAV remains: {name}.wav")
    for record in manifest["images"]:
        if record["format"] in ("PNG", "WEBP"):
            with Image.open(REPO / record["output"]) as image:
                image.load()
                require(image.format == record["format"], f"Image format changed: {record['name']}")
                require(list(image.size) == record["sourceSize"] == record["size"], "Image dimensions changed")
        else:
            source = ET.parse(REPO / record["source"]).getroot()
            svg = ET.parse(REPO / record["output"]).getroot()
            require([p.attrib[ANDROID_NS + "pathData"] for p in source] == [p.attrib["d"] for p in svg],
                    f"Vector geometry changed: {record['name']}")
    require(sum(len(g["game"]["tutorialImages"]) for g in manifest["games"]) == 63, "Incomplete tutorials")
    print(json.dumps({"validation": "passed", **manifest["summary"]}, ensure_ascii=False, indent=2), flush=True)


def sync_icons(check: bool) -> None:
    manifest = json.loads(text(MANIFEST))
    images = image_plan([{"name": "app_icon", "androidName": "ic_launcher", "kind": "app_icon"}])
    app_icons = [copied(APP_ICON, APP_MEDIA / "game_icon.png", name="game_icon")]
    expected = json.loads(json.dumps(manifest))
    expected["images"] = [images[0][0] if record["name"] == "app_icon" else record
                          for record in expected["images"]]
    expected["appIcons"] = [record for record, _ in app_icons]
    expected["sourcePolicy"] = SOURCE_POLICY
    expected["applicationIconSource"] = ICON_SOURCE
    expected["imagePolicy"]["sourceSelection"] = (
        "App icon: local 1024px master; other PNGs: largest Android pixel area, deterministic lexical tie-break")
    groups = {key: [(record, (REPO / record["output"]).read_bytes())
                    for record in manifest[key]] for key in ("images", "audio", "fonts", "appIcons")}
    groups["images"] = [(images[0] if record["name"] == "app_icon" else (record, data))
                        for record, data in groups["images"]]
    groups["appIcons"] = app_icons
    expected["summary"] = summary(groups)
    outputs = {record["output"]: data for record, data in images + app_icons}
    if not check:
        cleanup_plan(manifest, {record["output"]: data for items in groups.values()
                                for record, data in items})
        for name, data in outputs.items():
            (REPO / name).write_bytes(data)
        MANIFEST.write_bytes(json_bytes(expected))
        manifest = expected
    require(manifest == expected, "Icon manifest differs from master")
    for name, data in outputs.items():
        require((REPO / name).read_bytes() == data, f"Icon differs from master: {name}")
    print("App icons validated: 1024x1024 opaque RGB PNG; source and both resources match", flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Verify reproducible assets; no writes or size limits")
    parser.add_argument("--icons-only", action="store_true", help="Update/check app icons without regenerating other assets")
    args = parser.parse_args()
    if args.icons_only:
        sync_icons(args.check)
        return
    games, assets = source_catalog()
    manifest, outputs = build_plan(games, assets)
    if args.check:
        validate(json.loads(text(MANIFEST)), manifest, outputs)
        return
    previous = json.loads(text(MANIFEST)) if MANIFEST.exists() else {"generator": relative(Path(__file__))}
    obsolete = cleanup_plan(previous, outputs)
    # Preflight all deletions/conflicts before changing any output.
    for path in obsolete:
        path.unlink()
        print(f"Removed verified old generated asset: {relative(path)}", flush=True)
    for name, data in outputs.items():
        path = REPO / name
        path.parent.mkdir(parents=True, exist_ok=True)
        if not path.exists() or path.read_bytes() != data:
            path.write_bytes(data)
    MANIFEST.write_bytes(json_bytes(manifest))
    validate(manifest, manifest, outputs)


if __name__ == "__main__":
    main()
