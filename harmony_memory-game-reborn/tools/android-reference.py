#!/usr/bin/env python3
"""Capture-only Android reference helper. Never touches Harmony devices or app data."""
import argparse
import datetime as dt
import json
import os
import shutil
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'entry/build/android-reference/aligned'
SDK_ROOT = os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
SDK_ADB = Path(SDK_ROOT) / 'platform-tools' / ('adb.exe' if os.name == 'nt' else 'adb') if SDK_ROOT else None
ADB = os.environ.get('ADB_PATH') or (str(SDK_ADB) if SDK_ADB and SDK_ADB.is_file() else shutil.which('adb'))
SERIAL = os.environ.get('ANDROID_SERIAL', '')
PACKAGE = 'com.memory.brain.training.games'
OUT.mkdir(parents=True, exist_ok=True)

def adb(*args, timeout=15):
    if not ADB:
        raise RuntimeError('Set ADB_PATH, ANDROID_SDK_ROOT or ANDROID_HOME, or add adb to PATH.')
    if not SERIAL:
        raise RuntimeError('Set ANDROID_SERIAL to the Android reference device; no device is selected automatically.')
    return subprocess.run([ADB, '-s', SERIAL, *map(str, args)], capture_output=True, timeout=timeout, check=True).stdout

def stamp():
    return dt.datetime.now().astimezone().isoformat()

def event(kind, **kw):
    with (OUT / 'actions.jsonl').open('a', encoding='utf-8') as f:
        f.write(json.dumps({'time': stamp(), 'kind': kind, **kw}, ensure_ascii=False) + '\n')

def bounds(node):
    return list(map(int, re.findall(r'-?\d+', node.get('bounds', ''))))

def tap(node):
    b = bounds(node)
    event('tap', text=node.get('text'), resourceId=node.get('resource-id'), bounds=b)
    adb('shell', 'input', 'tap', (b[0]+b[2])//2, (b[1]+b[3])//2)

def key(code='BACK'):
    event('key', code=code)
    adb('shell', 'input', 'keyevent', code)

def tree(name=None):
    started = stamp()
    remote = '/sdcard/android_reference_aligned.xml'
    try:
        adb('shell', 'rm', '-f', remote)
        adb('shell', 'uiautomator', 'dump', remote, timeout=13)
        raw = adb('exec-out', 'cat', remote)
        if name:
            (OUT / (name+'.uiautomator.raw')).write_bytes(raw)
        root = ET.fromstring(raw)
        if name:
            (OUT / (name+'.xml')).write_bytes(raw)
            data = {'serial': SERIAL, 'started': started, 'finished': stamp(),
                    'note': 'UI tree sampled after screenshot; animations/timers can differ.',
                    'elements': [dict(n.attrib, boundsPixels=bounds(n)) for n in root.iter('node')]}
            (OUT / (name+'.json')).write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
        return root
    except Exception as e:
        event('tree-error', name=name, error=str(e))
        if name:
            (OUT / (name+'.json')).write_text(json.dumps({'serial': SERIAL, 'started': started, 'error': str(e)}, indent=2), encoding='utf-8')
        return None

def nodes(root):
    return list(root.iter('node')) if root is not None else []

def find(root, text=None, rid=None):
    return next((n for n in nodes(root) if (text is None or n.get('text')==text) and (rid is None or n.get('resource-id','').endswith(':id/'+rid))), None)

def screenshot(name):
    t = stamp()
    png = adb('exec-out', 'screencap', '-p')
    (OUT / (name+'.png')).write_bytes(png)
    with Image.open(OUT / (name+'.png')) as im:
        # Literal visible crop; no scaling, recentering, repair, or layout normalization.
        im.crop((0, 84, im.width, im.height-84)).save(OUT / (name+'-visible.png'))
        size = list(im.size)
    metadata = {'serial': SERIAL, 'name': name, 'screenshotTime': t, 'pixels': size,
                'visibleCrop': [0,84,size[0],size[1]-84],
                'cropPolicy': 'Literal crop excludes only 84px system bars; no scaling or layout correction.',
                'uiTreePolicy': 'Matching XML/JSON when present is sampled after screenshots, not frame-synchronous.'}
    (OUT / (name+'.capture.json')).write_text(json.dumps(metadata,ensure_ascii=False,indent=2),encoding='utf-8')
    event('screenshot', **metadata)

def capture(name):
    screenshot(name)
    return tree(name)

def show(root):
    for n in nodes(root):
        if n.get('text') or n.get('clickable')=='true':
            print(n.get('text'), n.get('resource-id'), n.get('bounds'), flush=True)

def home():
    event('main-menu', component=PACKAGE+'/com.cube.memorygames.MainMenuActivity')
    adb('shell','am','start','-n',PACKAGE+'/com.cube.memorygames.MainMenuActivity','--activity-clear-top')
    time.sleep(.6)
    root = tree()
    for _ in range(4):
        cancel = find(root, rid='dialog_try_workout_cancel_button')
        if cancel is not None:
            tap(cancel)
        elif find(root, rid='coins_hint_message') is not None:
            # Tap observed empty portion of main root to dismiss the local hint.
            adb('shell','input','tap',100,350)
        elif find(root, rid='bottomBarCup') is not None:
            tap(find(root, rid='bottomBarCup'))
            return tree()
        else:
            key()
        time.sleep(.4)
        root = tree()
    return root

def catalog():
    # Read-only: array order and names, never writes Harmony source.
    text = (ROOT / 'entry/src/main/ets/core/GameCatalog.ets').read_text(encoding='utf-8')
    return [(int(i), n) for i,n in re.findall(r'id:\s*(\d+),\s*name:\s*"([^"]+)"', text)]

def locate_game(name):
    root = home()
    for attempt in range(35):
        target = find(root, text=name, rid='name')
        if target is not None:
            return root, target
        recycler = find(root, rid='recyclerView')
        if recycler is None:
            root = home()
            continue
        b = bounds(recycler)
        # Scan down in sprint order; reset to beginning after one full pass.
        if attempt==18:
            for _ in range(13):
                adb('shell','input','swipe',660,500,660,2400,180)
        else:
            adb('shell','input','swipe',660,b[3]-150,660,b[1]+450,300)
        time.sleep(.25)
        root = tree()
    raise RuntimeError('Game title not found in local sprint list: '+name)

def collect_game(game_id, title):
    prefix = 'game'+str(game_id)
    print('COLLECT', game_id, title, flush=True)
    root, target = locate_game(title)
    capture(prefix+'-list')
    tap(target)
    time.sleep(.7)
    root = capture(prefix+'-tutorial-page1')
    for page in range(1, 7):
        button = find(root, rid='play_button')
        label = find(root, rid='text_play_button')
        if button is None:
            raise RuntimeError('No tutorial start/next button')
        next_page = label is not None and label.get('text','').lower() in ('下一步','下一个','下一页','next','继续')
        if not next_page:
            tap(button)
            break
        tap(button)
        time.sleep(.5)
        root = capture(prefix+'-tutorial-page'+str(page+1))
    else:
        raise RuntimeError('Tutorial exceeded six pages')
    # Critical: screenshots before UIAutomator so short per-question timers survive.
    time.sleep(.9)
    screenshot(prefix+'-entry')
    time.sleep(.65 if game_id in (11,12) else 1.3)
    screenshot(prefix)
    time.sleep(.65 if game_id in (11,12) else 1.4)
    screenshot(prefix+'-input')
    root = tree(prefix+'-input')
    event('game-captured', game=game_id, title=title,
          uiTexts=[n.get('text') for n in nodes(root) if n.get('text')])
    print('CAPTURED', game_id, flush=True)

def fast_locate(title):
    order = {name: index for index, (_,name) in enumerate(catalog())}
    wanted = order[title]
    root = tree()
    if find(root,rid='bottomBarCup') is None:
        adb('shell','am','start','-n',PACKAGE+'/com.cube.memorygames.MainMenuActivity','--activity-clear-top')
        time.sleep(.45)
        root=tree()
    for _ in range(16):
        cancel=find(root,rid='dialog_try_workout_cancel_button')
        if cancel is not None:
            tap(cancel); root=tree(); continue
        target=find(root,text=title,rid='name')
        if target is not None: return root,target
        visible=[order[n.get('text')] for n in nodes(root) if n.get('text') in order and n.get('resource-id','').endswith(':id/name')]
        if not visible: raise RuntimeError('Not on sprint list')
        down = wanted>max(visible)
        adb('shell','input','swipe',660,2100 if down else 1000,660,1000 if down else 2100,600)
        time.sleep(.2)
        root=tree()
    raise RuntimeError('Title not reached '+title)

def fast_collect(selected):
    text=(ROOT/'entry/src/main/ets/core/GameCatalog.ets').read_text(encoding='utf-8')
    counts={int(i): len(re.findall(r'"[^"]+"',images)) for i,images in re.findall(r'id:\s*(\d+),[\s\S]*?tutorialImages:\s*\[([^]]+)\]',text)}
    results=[]
    for game_id,title in catalog():
        if game_id not in selected: continue
        prefix='game'+str(game_id)
        print('FAST',game_id,title,flush=True)
        try:
            root,target=fast_locate(title)
            # Persist the exact UI-element bounds used to enter this game.
            (OUT/(prefix+'-selection.json')).write_text(json.dumps({'time':stamp(),'title':title,'element':target.attrib},ensure_ascii=False,indent=2),encoding='utf-8')
            tap(target);time.sleep(.5)
            root=tree()
            button=find(root,rid='play_button');label=find(root,rid='text_play_button')
            if button is None: raise RuntimeError('No tutorial button')
            next_page=label is not None and label.get('text','').lower() in ('下一步','下一个','下一页','next','继续')
            b=bounds(button); x=(b[0]+b[2])//2;y=(b[1]+b[3])//2
            for _ in range(counts[game_id] if next_page else 1):
                adb('shell','input','tap',x,y);time.sleep(.85)
            time.sleep(.55)
            screenshot(prefix+'-entry')
            time.sleep(.8 if game_id in (11,12) else 1.3)
            screenshot(prefix)
            time.sleep(.55 if game_id in (11,12) else 1)
            screenshot(prefix+'-input')
            root=tree(prefix+'-input')
            results.append({'game':game_id,'title':title,'screenshotsWritten':True,'inputVerified':False,'note':'Screenshot existence alone is not success; visually verify screen and check crash buffer.','texts':[n.get('text') for n in nodes(root) if n.get('text')]})
            print('FAST CAPTURED',game_id,flush=True)
        except Exception as e:
            results.append({'game':game_id,'title':title,'captured':False,'error':str(e)})
            print('FAST ERROR',game_id,str(e),flush=True)
        (OUT/'fast-status.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
    adb('shell','am','start','-n',PACKAGE+'/com.cube.memorygames.MainMenuActivity','--activity-clear-top')

def collect(selected=None):
    results = []
    for game_id,title in catalog():
        if selected and game_id not in selected: continue
        try:
            collect_game(game_id,title)
            results.append({'game':game_id,'title':title,'captured':True})
        except Exception as e:
            event('game-error', game=game_id, title=title, error=str(e))
            results.append({'game':game_id,'title':title,'captured':False,'error':str(e)})
            print('ERROR',game_id,str(e),flush=True)
        (OUT/'collection-status.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
    home()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['inspect','capture','tap-text','tap-id','back','home','collect','fast'])
    parser.add_argument('value', nargs='?')
    args = parser.parse_args()
    if args.action=='inspect': show(tree())
    elif args.action=='capture': show(capture(args.value))
    elif args.action in ('tap-text','tap-id'):
        root = tree()
        n = find(root, text=args.value) if args.action=='tap-text' else find(root, rid=args.value)
        if n is None: raise RuntimeError('UI target not found: '+str(args.value))
        tap(n)
    elif args.action=='back': key()
    elif args.action=='home': show(home())
    elif args.action=='collect': collect(set(map(int,args.value.split(','))) if args.value else None)
    elif args.action=='fast': fast_collect(set(map(int,args.value.split(','))) if args.value else {11,12,15,17,19,20,21,22,23})

if __name__=='__main__': main()
