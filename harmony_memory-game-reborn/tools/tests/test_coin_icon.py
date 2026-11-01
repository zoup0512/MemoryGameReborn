from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
MEDIA = ROOT / "entry/src/main/resources/base/media"
SVG = "{http://www.w3.org/2000/svg}"


class CoinIconTests(unittest.TestCase):
    def test_coin_has_one_vector_resource_without_bitmap_conflict(self):
        self.assertEqual(list(MEDIA.glob("ic_coin.*")), [MEDIA / "ic_coin.svg"])
        icon = ET.parse(MEDIA / "ic_coin.svg").getroot()
        self.assertEqual(icon.tag, SVG + "svg")
        self.assertEqual(icon.attrib["viewBox"], "0 0 32 32")
        self.assertEqual(icon.attrib["width"], icon.attrib["height"])
        self.assertEqual(len(icon.findall(SVG + "circle")), 3)
        self.assertEqual(len(icon.findall(SVG + "path")), 1)
        for element in icon.iter():
            self.assertIn(element.tag, {SVG + tag for tag in ("svg", "circle", "path")})
            self.assertFalse(any("href" in name for name in element.attrib))
        star = icon.find(SVG + "path")
        self.assertEqual(star.attrib["stroke-linejoin"], "round")

    def test_existing_coin_mapping_and_ui_sizes_are_preserved(self):
        catalog = (ROOT / "entry/src/main/ets/core/GameCatalog.ets").read_text(encoding="utf-8")
        self.assertIn("case 'ic_coin': return $r('app.media.ic_coin');", catalog)
        screen = (ROOT / "entry/src/main/ets/components/AndroidScreens.ets").read_text(encoding="utf-8")
        sizes = re.findall(r"Image\(media\('ic_coin'\)\)\.width\((\d+)\)\.height\((\d+)\)", screen)
        self.assertEqual(sizes, [("16", "16"), ("20", "20")])


if __name__ == "__main__":
    unittest.main()
