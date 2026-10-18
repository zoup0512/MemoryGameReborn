"""Run on an existing, running HarmonyOS emulator; no app data is cleared."""
from pathlib import Path
import json
import os
import re
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
HOME = Path(os.environ.get('DEVECO_HOME', 'C:/Program Files/Huawei/DevEco Studio'))
HDC = HOME / 'sdk/default/openharmony/toolchains/hdc.exe'
OUT = ROOT / 'entry/build/device-smoke'
OUT.mkdir(parents=True, exist_ok=True)
BUNDLE = 'com.razor.memory.brain.training.games'

def hdc(*args):
    result = subprocess.run([str(HDC), *map(str, args)], capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=30)
    if result.returncode != 0 or '[Fail]' in result.stdout:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout

def layout():
    hdc('shell', 'uitest', 'dumpLayout', '-p', '/data/local/tmp/memory-smoke.json')
    path = OUT / 'layout.json'
    hdc('file', 'recv', '/data/local/tmp/memory-smoke.json', path)
    return json.loads(path.read_text(encoding='utf-8'))

def nodes(root):
    yield root.get('attributes', {})
    for child in root.get('children', []):
        yield from nodes(child)

def click(text, exact=True):
    for item in nodes(layout()):
        value = item.get('text', '') or item.get('description', '')
        if (value == text if exact else text in value) and item.get('visible') == 'true':
            numbers = list(map(int, re.findall(r'-?\d+', item.get('bounds', ''))))
            if len(numbers) == 4:
                x1, y1, x2, y2 = numbers
                if y1 >= 0 and y2 <= 2856 and y2 > y1:
                    hdc('shell', 'uitest', 'uiInput', 'click', (x1 + x2)//2, (y1 + y2)//2)
                    time.sleep(.25)
                    return True
    return False

def screenshot(name):
    remote = '/data/local/tmp/memory-smoke.jpeg'
    hdc('shell', 'snapshot_display', '-f', remote)
    hdc('file', 'recv', remote, OUT / (name + '.jpeg'))

def find_game(name):
    for _ in range(16):
        if click(name): return
        hdc('shell', 'uitest', 'uiInput', 'swipe', 650, 2320, 650, 950, 1400)
        time.sleep(.2)
    raise AssertionError('Game not found: ' + name)

if __name__ == '__main__':
    hdc('shell', 'aa', 'start', '-a', 'EntryAbility', '-b', BUNDLE)
    time.sleep(1)
    games = ['记忆网格', '六边形', '谁是新来的', '跟随路径', '图像漩涡', '找到图片', '旋转网格', '抓住它们', '舒尔特方格', '独一无二', '全部相同', '数字排序', '找出全部', '纸飞机', '和之前一样吗？', '颜色', '激光', '对称', '正确吗？', '多与少', '248', '数一数', '金字塔']
    results = []
    screenshot('00-home')
    for index, name in enumerate(games):
        find_game(name)
        for _ in range(5):
            state = list(nodes(layout()))
            if not any(n.get('text') in ['下一步', '开始'] for n in state): break
            if click('开始'): break
            if not click('下一步'): break
        time.sleep(4.4 if name not in ['旋转网格', '抓住它们'] else 7.5)
        game_layout = layout()
        (OUT / f'{index+1:02d}-layout.json').write_text(json.dumps(game_layout, ensure_ascii=False), encoding='utf-8')
        current = list(nodes(game_layout))
        assert any(n.get('text') == name for n in current), f'Game screen missing: {name}'
        assert not any('应用无响应' in n.get('text', '') for n in current)
        screenshot(f'{index+1:02d}-game')
        if any(n.get('text') == '再试一次？' for n in current):
            assert click('结束本局并查看成绩')
        else:
            if not click('暂停'):
                hdc('shell', 'uitest', 'uiInput', 'click', 105, 366)
            assert click('继续游戏'), f'Resume missing: {name}'
            if index == 0:
                for _ in range(6):
                    hdc('shell', 'uitest', 'uiInput', 'click', 105, 366)
                    assert click('继续游戏')
            hdc('shell', 'uitest', 'uiInput', 'click', 105, 366)
            assert click('结束本局并查看成绩')
        assert click('返回游戏列表')
        results.append({'id': index+1, 'name': name, 'entry': True, 'pauseResume': True, 'result': True})
        print(f'PASS {index+1}/23 {name}', flush=True)
    (OUT/'results.json').write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding='utf-8')
    print('PASS all 23 entries, tutorials, pause/resume, result and return; first game paused more than five times.')
