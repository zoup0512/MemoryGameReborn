"""Real-device memory regression based on visible pixels, not private puzzle state.

Run tools/device-smoke.py first so tutorials have been seen. This fixture uses the
Pura90 1320x2856 emulator used by the screenshot comparison.
"""
from pathlib import Path
import importlib.util
import time
from PIL import Image

spec = importlib.util.spec_from_file_location('smoke', Path(__file__).with_name('device-smoke.py'))
s = importlib.util.module_from_spec(spec)
spec.loader.exec_module(s)
s.hdc('shell', 'aa', 'force-stop', s.BUNDLE)
s.hdc('shell', 'aa', 'start', '-a', 'EntryAbility', '-b', s.BUNDLE)
time.sleep(1)
s.find_game('记忆网格')
time.sleep(1.5)
s.screenshot('play-memory-preview')
image = Image.open(s.OUT / 'play-memory-preview.jpeg').convert('RGB')
assert image.size == (1320, 2856), 'Use the documented Pura90 screenshot fixture'
targets = []
for y in [1395, 1649, 1902]:
    for x in [406, 659, 912]:
        red, green, blue = image.getpixel((x, y))
        if blue > 200 and green > 100 and red < 70:
            targets.append((x, y))
assert targets, 'Original blue target cells must be visible during preview'
time.sleep(1.3)
for x, y in targets:
    s.hdc('shell', 'uitest', 'uiInput', 'click', x, y)
time.sleep(.8)
assert any(n.get('text') == '第 2 关' for n in s.nodes(s.layout())), 'Observed solution must advance to level 2'
s.screenshot('play-memory-solved')
s.hdc('shell', 'uitest', 'uiInput', 'click', 105, 366)
assert s.click('结束本局并查看成绩')
s.screenshot('play-memory-result')
assert s.click('返回游戏列表')
print('PASS observed original-blue memory solution advances to level 2 and results return')
