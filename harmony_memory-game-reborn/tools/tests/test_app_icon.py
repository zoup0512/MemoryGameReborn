import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from PIL import Image, ImageChops

TOOLS = Path(__file__).resolve().parents[1]


def load_module(name):
    spec = importlib.util.spec_from_file_location(name, TOOLS / f"{name}.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


resources = load_module("prepare_resources")
renderer = load_module("render_app_icon")


class AppIconTests(unittest.TestCase):
    def setUp(self):
        self.asset = {"name": "app_icon", "androidName": "ic_launcher", "kind": "app_icon"}

    def test_master_is_exact_opaque_png(self):
        with Image.open(resources.APP_ICON) as image:
            self.assertEqual(image.format, "PNG")
            self.assertEqual(image.size, (1024, 1024))
            self.assertEqual(image.mode, "RGB")
            self.assertIsNone(ImageChops.difference(image, renderer.render()).getbbox())

    def test_app_icon_never_reads_android_bitmap(self):
        with patch.object(resources, "bitmap_source", side_effect=AssertionError("Android icon used")):
            record, data = resources.image_plan([self.asset])[0]
        self.assertEqual(record["sourceSize"], [1024, 1024])
        self.assertEqual(data, resources.APP_ICON.read_bytes())

    def test_rejects_wrong_size_or_alpha(self):
        with tempfile.TemporaryDirectory() as directory:
            icon = Path(directory) / "icon.png"
            for size, mode in (((192, 192), "RGB"), ((1024, 1024), "RGBA")):
                with self.subTest(size=size, mode=mode):
                    Image.new(mode, size).save(icon)
                    with patch.object(resources, "APP_ICON", icon):
                        with self.assertRaises(RuntimeError):
                            resources.image_plan([self.asset])

    def test_outputs_and_manifest_match_master(self):
        resources.sync_icons(check=True)
        manifest = json.loads(resources.MANIFEST.read_text(encoding="utf-8"))
        data = resources.APP_ICON.read_bytes()
        for record in ([r for r in manifest["images"] if r["name"] == "app_icon"]
                       + manifest["appIcons"]):
            self.assertEqual((resources.REPO / record["output"]).read_bytes(), data)
            self.assertEqual(record["sha256"], resources.digest(data))
            self.assertEqual(record["bytes"], len(data))

    def test_icons_only_preserves_other_records(self):
        before = json.loads(resources.MANIFEST.read_text(encoding="utf-8"))
        with tempfile.TemporaryDirectory() as directory:
            manifest_path = Path(directory) / "manifest.json"
            manifest_path.write_text(json.dumps(before), encoding="utf-8")
            with patch.object(resources, "MANIFEST", manifest_path):
                resources.sync_icons(check=False)
            after = json.loads(manifest_path.read_text(encoding="utf-8"))
        expected = copy.deepcopy(before)
        self.assertEqual(after, expected)


if __name__ == "__main__":
    unittest.main()
