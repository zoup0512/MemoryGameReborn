"""Run on an existing, running HarmonyOS emulator; no app data is cleared."""
from pathlib import Path
import argparse
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
BUNDLE = json.loads((ROOT / 'AppScope/app.json5').read_text(encoding='utf-8'))['app']['bundleName']

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
        values = [item.get('text', ''), item.get('description', '')]
        if any(value == text if exact else text in value for value in values) and item.get('visible') == 'true':
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
        if click(name) or click(name + '，', exact=False): return
        hdc('shell', 'uitest', 'uiInput', 'swipe', 650, 2320, 650, 950, 1400)
        time.sleep(.2)
    raise AssertionError('Game not found: ' + name)


def text_values():
    return [value for item in nodes(layout()) for value in [item.get('text', ''), item.get('description', '')] if value]


def scroll_home_top():
    for _ in range(16):
        hdc('shell', 'uitest', 'uiInput', 'swipe', 650, 950, 650, 2320, 700)


def economy_smoke():
    values = text_values()
    assert any('金币余额 60' in value for value in values), 'Use a fresh economy save with exactly 60 coins; no data is reset automatically'
    find_game('找到图片')
    values = text_values()
    assert '解锁游戏' in values and any('还差 240 金币' in value for value in values)
    confirmation = next((item for item in nodes(layout()) if item.get('text') == '解锁 · 300 金币'), None)
    assert confirmation is not None and confirmation.get('enabled') == 'false', 'Insufficient purchase must be disabled'
    assert click('取消')
    assert any('金币余额 60' in value for value in text_values())
    find_game('金字塔')
    assert '解锁游戏' in text_values(), 'Use a fresh save with Pyramids still locked'
    assert click('解锁 · 0 金币')
    assert '解锁游戏' not in text_values(), 'Free purchase must return to the list'
    assert any('金币余额 60' in value for value in text_values())
    assert click('金字塔') or click('金字塔，', exact=False)
    assert '解锁游戏' not in text_values(), 'Pyramids must stay unlocked'
    hdc('shell', 'uitest', 'uiInput', 'keyEvent', 'Back')
    time.sleep(.5)
    scroll_home_top()
    screenshot('economy-home')
    print('PASS fresh 60 coins, insufficient purchase/cancel, free Pyramids unlock and repeat entry; no app data cleared')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--economy', action='store_true', help='Check a fresh economy save without clearing data; unlocks Pyramids for zero coins')
    parser.add_argument('--all-unlocked', action='store_true', help='Require a test save with every game legitimately unlocked and run all 23 entries')
    args = parser.parse_args()
    hdc('shell', 'aa', 'start', '-a', 'EntryAbility', '-b', BUNDLE)
    time.sleep(1)
    if args.economy:
        economy_smoke()
        raise SystemExit(0)
    games = ['记忆网格', '六边形', '谁是新来的', '跟随路径', '图像漩涡', '找到图片', '旋转网格', '抓住它们', '舒尔特方格', '独一无二', '全部相同', '数字排序', '找出全部', '纸飞机', '和之前一样吗？', '颜色', '激光', '对称', '正确吗？', '多与少', '248', '数一数', '金字塔']
    paid_or_confirmed = {'找到图片', '抓住它们', '舒尔特方格', '独一无二', '数字排序', '找出全部', '激光', '多与少', '248', '金字塔'}
    if not args.all_unlocked:
        games = [name for name in games if name not in paid_or_confirmed]
    results = []
    screenshot('00-home')
    for index, name in enumerate(games):
        find_game(name)
        assert '解锁游戏' not in text_values(), 'This smoke mode requires the game to be already unlocked: ' + name
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
            assert click('暂停'), f'Pause missing: {name}'
            assert click('继续游戏'), f'Resume missing: {name}'
            if index == 0:
                for _ in range(6):
                    assert click('暂停'), f'Pause missing: {name}'
                    assert click('继续游戏')
            assert click('暂停'), f'Pause missing: {name}'
            assert click('结束本局并查看成绩')
        assert click('返回游戏列表')
        results.append({'id': index+1, 'name': name, 'entry': True, 'pauseResume': True, 'result': True})
        print(f'PASS {index+1}/{len(games)} {name}', flush=True)
    (OUT/'results.json').write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f'PASS {len(games)} entries, tutorials, pause/resume, result and return; first game paused more than five times.')
