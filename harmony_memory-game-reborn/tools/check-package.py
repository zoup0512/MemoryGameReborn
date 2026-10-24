"""Validate phone App configuration, packaged metadata, resources and offline scope."""
from pathlib import Path
import hashlib
import json
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BUNDLE = 'com.razor.memory.brain.training.games'


def validate_application(app, module):
    assert app['bundleType'] == 'app', 'Expected an installable App, not an atomic service'
    assert app['bundleName'] == BUNDLE, 'Unexpected App bundleName'
    assert module['type'] == 'entry'
    assert module['deviceTypes'] == ['phone'], 'Only the phone device type is supported'
    assert module['deliveryWithInstall'] is True
    assert module['installationFree'] is False, 'Phone App must require installation'
    assert 'atomicService' not in module, 'Atomic-service-only configuration must be removed'
    assert not module.get('requestPermissions'), 'Offline App must not request permissions'
    assert module['mainElement'] == 'EntryAbility'
    ability = next(a for a in module['abilities'] if a['name'] == module['mainElement'])
    assert ability['exported'] is True
    assert any('entity.system.home' in skill.get('entities', []) and
               'ohos.want.action.home' in skill.get('actions', [])
               for skill in ability.get('skills', [])), 'App must have a desktop launcher entry'


def main():
    app = json.loads((ROOT / 'AppScope/app.json5').read_text(encoding='utf-8'))['app']
    module = json.loads((ROOT / 'entry/src/main/module.json5').read_text(encoding='utf-8'))['module']
    build = json.loads((ROOT / 'build-profile.json5').read_text(encoding='utf-8'))
    validate_application(app, module)
    product = build['app']['products'][0]
    assert product['compatibleSdkVersion'] == '5.1.1(19)'
    assert product['compileSdkVersion'] == product['targetSdkVersion'] == '6.1.1(24)'
    source = '\n'.join(p.read_text(encoding='utf-8') for p in (ROOT/'entry/src/main/ets').rglob('*.ets'))
    assert not re.search(r'@ohos\.net|@kit\.NetworkKit|https?://|websocket|INTERNET', source, re.I)
    resource_names = {p.stem for p in (ROOT/'entry/src/main/resources/base/media').iterdir()}
    resource_names.update(p.stem for p in (ROOT/'AppScope/resources/base/media').iterdir())
    for name in re.findall(r"\$r\('app\.media\.([a-zA-Z0-9_]+)'\)", source):
        assert name in resource_names, f'Missing resource {name}'
    # Canvas-only pseudo names must never go through the image mapping.
    assert 'SpatialBoard' in source and 'MemoryBoard' in source and 'QuickBoard' in source
    packages = sorted((ROOT/'entry/build/default/outputs').rglob('*.hap'))
    assert packages, 'Build the HAP first'
    report = []
    for hap in packages:
        with zipfile.ZipFile(hap) as archive:
            assert 'module.json' in archive.namelist()
            packed = json.loads(archive.read('module.json'))
            validate_application(packed['app'], packed['module'])
            assert packed['app']['bundleName'] == app['bundleName']
            assert packed['app']['minAPIVersion'] == 50101019
            assert packed['app']['targetAPIVersion'] == 60101024
        report.append({'file': str(hap.relative_to(ROOT)), 'bytes': hap.stat().st_size,
                       'sizeLimitEnforced': False,
                       'sha256': hashlib.sha256(hap.read_bytes()).hexdigest(),
                       'signed': 'unsigned' not in hap.name,
                       'buildMode': packed['app']['buildMode']})
    result = {'validation': 'passed', 'configuration': 'phone App API19..24, offline',
              'bundleName': app['bundleName'], 'packages': report}
    out = ROOT/'entry/build/package-check.json'
    out.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
