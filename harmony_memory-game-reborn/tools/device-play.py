"""Real-device memory regression based on visible pixels, not private puzzle state.

Run tools/device-smoke.py first so tutorials have been seen. Cell centers come
from the visible accessibility layout instead of fixed screen coordinates.
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
visible = list(s.nodes(s.layout()))
centers = []
for item in visible:
    label = item.get('text', '') or item.get('description', '')
    if not s.re.fullmatch(r'方格，第\d+行，第\d+列', label) or item.get('visible') != 'true':
        continue
    bounds = list(map(int, s.re.findall(r'-?\d+', item.get('bounds', ''))))
    if len(bounds) == 4:
        x1, y1, x2, y2 = bounds
        if x2 > x1 and y2 > y1:
            centers.append(((x1 + x2) // 2, (y1 + y2) // 2))
assert centers, 'Visible memory cells must be accessible during preview'
s.screenshot('play-memory-preview')
image = Image.open(s.OUT / 'play-memory-preview.jpeg').convert('RGB')
targets = []
for x, y in centers:
    assert 0 <= x < image.width and 0 <= y < image.height, 'Cell must be inside the screenshot'
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
assert s.click('暂停'), 'Pause control must be accessible'
assert s.click('结束本局并查看成绩')
s.screenshot('play-memory-result')
assert s.click('返回游戏列表')
print('PASS observed original-blue memory solution advances to level 2 and results return')
