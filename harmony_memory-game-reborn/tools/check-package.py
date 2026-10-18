"""Validate atomic-service configuration, resource references and offline scope; report HAP size without a cap."""
from pathlib import Path
import hashlib
import json
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
app = json.loads((ROOT / 'AppScope/app.json5').read_text(encoding='utf-8'))['app']
module = json.loads((ROOT / 'entry/src/main/module.json5').read_text(encoding='utf-8'))['module']
build = json.loads((ROOT / 'build-profile.json5').read_text(encoding='utf-8'))
assert app['bundleType'] == 'atomicService'
assert app['bundleName'] == 'com.atomicservice.6917619386167723098'
assert module['installationFree'] is True
assert not module.get('requestPermissions'), 'Offline service must not request network permissions'
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
packages = list((ROOT/'entry/build/default/outputs').rglob('*.hap'))
assert packages, 'Build the HAP first'
report = []
for hap in packages:
    size = hap.stat().st_size
    with zipfile.ZipFile(hap) as archive:
        assert 'module.json' in archive.namelist()
        packed = json.loads(archive.read('module.json'))
        assert packed['app']['bundleType'] == 'atomicService'
        assert packed['module']['installationFree'] is True
        assert not packed['module'].get('requestPermissions')
        assert packed['app']['minAPIVersion'] == 50101019
        assert packed['app']['targetAPIVersion'] == 60101024
    report.append({'file': str(hap.relative_to(ROOT)), 'bytes': size, 'sizeLimitEnforced': False,
                   'sha256': hashlib.sha256(hap.read_bytes()).hexdigest(), 'signed': 'unsigned' not in hap.name})
result = {'validation': 'passed', 'configuration': 'atomicService API19..24, offline', 'packages': report}
out = ROOT/'entry/build/package-check.json'
out.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(result, ensure_ascii=False, indent=2))
