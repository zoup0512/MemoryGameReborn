"""Regression checks for installable phone App identity and launcher metadata."""
from copy import deepcopy
import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('check_package', ROOT / 'tools/check-package.py')
package_check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(package_check)


class AppConfigTests(unittest.TestCase):
    def setUp(self):
        self.app = json.loads((ROOT / 'AppScope/app.json5').read_text(encoding='utf-8'))['app']
        self.module = json.loads((ROOT / 'entry/src/main/module.json5').read_text(encoding='utf-8'))['module']

    def test_phone_app_source_configuration(self):
        package_check.validate_application(self.app, self.module)

    def test_reject_atomic_service_identity(self):
        for key, value in [('bundleType', 'atomicService'),
                           ('bundleName', 'com.atomicservice.6917619386167723098')]:
            with self.subTest(key=key):
                app = dict(self.app, **{key: value})
                with self.assertRaises(AssertionError):
                    package_check.validate_application(app, self.module)

    def test_reject_invalid_phone_module(self):
        for key, value in [('installationFree', True), ('deliveryWithInstall', False),
                           ('deviceTypes', ['tablet']), ('atomicService', {}),
                           ('requestPermissions', [{'name': 'ohos.permission.INTERNET'}])]:
            with self.subTest(key=key):
                module = dict(self.module, **{key: value})
                with self.assertRaises(AssertionError):
                    package_check.validate_application(self.app, module)

    def test_reject_missing_launcher(self):
        module = deepcopy(self.module)
        module['abilities'][0]['skills'] = []
        with self.assertRaises(AssertionError):
            package_check.validate_application(self.app, module)

    def test_reject_non_exported_launcher(self):
        module = deepcopy(self.module)
        module['abilities'][0]['exported'] = False
        with self.assertRaises(AssertionError):
            package_check.validate_application(self.app, module)

    def test_no_atomic_service_capsule_spacer(self):
        index = (ROOT / 'entry/src/main/ets/pages/Index.ets').read_text(encoding='utf-8')
        self.assertNotIn('.padding({ top: 36 })', index)


if __name__ == '__main__':
    unittest.main()
