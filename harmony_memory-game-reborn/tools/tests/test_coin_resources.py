import copy
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from PIL import Image

TOOLS = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("prepare_resources", TOOLS / "prepare_resources.py")
resources = importlib.util.module_from_spec(spec)
spec.loader.exec_module(resources)


class CoinResourceTests(unittest.TestCase):
    def setUp(self):
        self.manifest = json.loads(resources.MANIFEST.read_text(encoding="utf-8"))
        self.catalog = resources.CATALOG.read_text(encoding="utf-8")
        self.coin = next(r for r in self.manifest["images"] if r["name"] == "ic_coin")

    def test_original_highest_resolution_png_is_lossless_and_manifest_matches(self):
        self.assertIn("ic_coin", resources.UI_BITMAPS)
        source = resources.bitmap_source("ic_coin")
        self.assertEqual(source.parent.name, "drawable-xxxhdpi-v4")
        record, data = resources.image_plan([
            {"name": "ic_coin", "androidName": "ic_coin", "kind": "ui_bitmap"}])[0]
        self.assertEqual(record, self.coin)
        self.assertEqual((resources.REPO / record["output"]).read_bytes(), data)
        with Image.open(source) as original, Image.open(io.BytesIO(data)) as generated:
            self.assertEqual(original.size, (128, 128))
            self.assertEqual(generated.format, "WEBP")
            self.assertEqual(generated.size, original.size)
            # Invisible RGB can be canonicalized by WebP; every visible RGBA pixel
            # and the entire alpha plane must be exact, never visually lossy.
            expected_bytes = original.convert("RGBA").tobytes()
            actual_bytes = generated.convert("RGBA").tobytes()
            expected = [expected_bytes[i:i + 4] for i in range(0, len(expected_bytes), 4)]
            actual = [actual_bytes[i:i + 4] for i in range(0, len(actual_bytes), 4)]
            self.assertEqual([p[3] for p in actual], [p[3] for p in expected])
            self.assertEqual([p for p in actual if p[3]], [p for p in expected if p[3]])
        resources.sync_ui_bitmap("ic_coin", check=True)

    def test_single_bitmap_sync_inserts_only_coin_and_preserves_game_and_other_assets(self):
        before = copy.deepcopy(self.manifest)
        before["images"] = [r for r in before["images"] if r["name"] != "ic_coin"]
        del before["mediaMappings"]["ic_coin"]
        before["summary"]["imagesCount"] -= 1
        before["summary"]["imagesBytes"] -= self.coin["bytes"]
        before["summary"]["totalAssetBytes"] -= self.coin["bytes"]
        before["summary"]["imageCounts"]["ui_bitmap"] -= 1
        mapping = "    case 'ic_coin': return $r('app.media.ic_coin');\n"
        catalog_before = self.catalog.replace(mapping, "")
        source_bytes = (resources.REPO / self.coin["source"]).read_bytes()
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            media = repo / "harmony_memory-game-reborn/entry/src/main/resources/base/media"
            media.mkdir(parents=True)
            source = repo / self.coin["source"]
            source.parent.mkdir(parents=True)
            source.write_bytes(source_bytes)
            manifest_path = repo / "manifest.json"
            manifest_path.write_text(json.dumps(before), encoding="utf-8")
            catalog_path = repo / "GameCatalog.ets"
            catalog_path.write_text(catalog_before, encoding="utf-8")
            unrelated = media / "unrelated.webp"
            unrelated.write_bytes(b"unrelated asset must not change")
            with (patch.object(resources, "REPO", repo),
                  patch.object(resources, "RES", repo / "android_memory-game-reborn/src/main/res"),
                  patch.object(resources, "MEDIA", media),
                  patch.object(resources, "MANIFEST", manifest_path),
                  patch.object(resources, "CATALOG", catalog_path),
                  patch.object(resources, "ui_plan", side_effect=AssertionError("full UI plan called")),
                  patch.object(resources, "source_catalog", side_effect=AssertionError("full catalog called")),
                  patch.object(resources, "audio_plan", side_effect=AssertionError("audio called")),
                  patch.object(resources, "font_plan", side_effect=AssertionError("font called"))):
                # Generator identity remains the actual script, not a temp source.
                with patch.object(resources, "relative", side_effect=lambda p:
                    self.manifest["generator"] if p == Path(resources.__file__)
                    else p.relative_to(repo).as_posix()):
                    resources.sync_ui_bitmap("ic_coin", check=False)
                    after = json.loads(manifest_path.read_text(encoding="utf-8"))
                    self.assertEqual([r for r in after["images"] if r["name"] != "ic_coin"], before["images"])
                    for group in ("audio", "fonts", "appIcons", "games"):
                        self.assertEqual(after[group], before[group])
                    self.assertEqual(unrelated.read_bytes(), b"unrelated asset must not change")
                    self.assertEqual(catalog_path.read_text(encoding="utf-8").replace(mapping, ""), catalog_before)
                    self.assertEqual(after["summary"]["imagesBytes"], before["summary"]["imagesBytes"] + self.coin["bytes"])
                    self.assertEqual(after["summary"]["totalAssetBytes"], before["summary"]["totalAssetBytes"] + self.coin["bytes"])
                    snapshot = {p: p.read_bytes() for p in repo.rglob("*") if p.is_file()}
                    resources.sync_ui_bitmap("ic_coin", check=True)
                    resources.sync_ui_bitmap("ic_coin", check=False)
                    self.assertEqual({p: p.read_bytes() for p in repo.rglob("*") if p.is_file()}, snapshot)
                    (media / "ic_coin.webp").write_bytes(b"modified")
                    with self.assertRaisesRegex(RuntimeError, "modified/unowned"):
                        resources.sync_ui_bitmap("ic_coin", check=False)

    def test_policy_distinguishes_local_coin_unlocks_from_real_money(self):
        self.assertEqual(self.manifest["sourcePolicy"], resources.SOURCE_POLICY)
        self.assertEqual(self.manifest["drawablePolicy"]["currency"], resources.CURRENCY_POLICY)
        self.assertIn("local coin", resources.SOURCE_POLICY)
        self.assertIn("no network or real-money purchases", resources.SOURCE_POLICY)


if __name__ == "__main__":
    unittest.main()
