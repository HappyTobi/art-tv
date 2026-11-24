import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
from zipfile import ZipFile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import preview


class ProjectTest(unittest.TestCase):
    def test_artwork_is_identical_in_apk_and_catalogue(self):
        entries = preview.artworks()
        with ZipFile(ROOT / 'ArtTV.apk') as apk:
            self.assertEqual(entries, json.loads(apk.read('assets/art/catalogue.json')))
            for entry in entries:
                self.assertEqual((ROOT / 'app/assets/art' / entry['file']).read_bytes(),
                                 apk.read('assets/art/' + entry['file']))
            self.assertEqual((ROOT / 'LICENSE').read_bytes(), apk.read('assets/LICENSE.txt'))

    def test_invalid_artwork_cannot_escape_the_asset_directory(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            directory = root / 'app/assets/art'
            directory.mkdir(parents=True)
            valid = {'id': 'example', 'title': 'Example', 'file': 'image.jpg'}
            (directory / 'image.jpg').write_bytes(b'test image')
            for entries in ([], [valid, valid], [dict(valid, id='../example')],
                            [dict(valid, file='../image.jpg')], [dict(valid, title='')],
                            [dict(valid, file='missing.jpg')]):
                (directory / 'catalogue.json').write_text(json.dumps(entries))
                with patch.object(preview, 'ROOT', root), self.assertRaises(ValueError):
                    preview.artworks()

    def test_home_is_registered_separately_from_the_apps_entry(self):
        manifest = ET.parse(ROOT / 'app/AndroidManifest.xml').getroot()
        android = '{http://schemas.android.com/apk/res/android}'
        self.assertEqual('io.happytobi.arttv', manifest.attrib['package'])
        self.assertEqual({'android.permission.INTERNET', 'android.permission.RECEIVE_BOOT_COMPLETED'},
                         {item.attrib[android + 'name'] for item in manifest.findall('uses-permission')})
        activity = manifest.find(f".//activity[@{android}name='.HomeActivity']")
        launcher = {'android.intent.category.LAUNCHER', 'android.intent.category.LEANBACK_LAUNCHER'}
        home = {'android.intent.category.HOME', 'android.intent.category.DEFAULT'}
        self.assertEqual(
            [launcher, home],
            [{entry.get(android + 'name') for entry in intent.findall('category')}
             for intent in activity.findall('intent-filter')
             if intent.find(f"action[@{android}name='android.intent.action.MAIN']") is not None])

    def test_startup_receiver_accepts_only_the_system_boot_broadcast(self):
        android = '{http://schemas.android.com/apk/res/android}'
        manifest = ET.parse(ROOT / 'app/AndroidManifest.xml')
        receiver, = manifest.findall('.//receiver')
        self.assertEqual('.BootReceiver', receiver.get(android + 'name'))
        self.assertEqual('false', receiver.get(android + 'exported'))
        self.assertEqual(['android.intent.action.BOOT_COMPLETED'],
                         [action.get(android + 'name') for action in receiver.findall('intent-filter/action')])

    def test_home_button_service_requests_keys_without_screen_access(self):
        android = '{http://schemas.android.com/apk/res/android}'
        manifest = ET.parse(ROOT / 'app/AndroidManifest.xml')
        service, = manifest.findall('.//service')
        self.assertEqual('.HomeButtonService', service.get(android + 'name'))
        self.assertEqual('android.permission.BIND_ACCESSIBILITY_SERVICE', service.get(android + 'permission'))
        self.assertEqual('@xml/home_button_service', service.find('meta-data').get(android + 'resource'))
        settings = ET.parse(ROOT / 'app/res/xml/home_button_service.xml').getroot()
        self.assertEqual('flagRequestFilterKeyEvents', settings.get(android + 'accessibilityFlags'))
        self.assertEqual('true', settings.get(android + 'canRequestFilterKeyEvents'))
        self.assertEqual('false', settings.get(android + 'canRetrieveWindowContent'))
        self.assertNotIn(android + 'accessibilityEventTypes', settings.attrib)

    def test_signing_files_and_local_tv_data_are_git_ignored(self):
        private = ['art-tv.keystore', '.keystore-password', 'config.properties',
                   'ArtTV.apk', 'ArtTV.apk.idsig', 'out/preview/index.html',
                   'sony-channels.local.json', 'tools/__pycache__/sony_channels.pyc',
                   'local/channel-backup.json', 'exports/.channels.local.json-abc123',
                   'archives/ArtTV.apk', 'archives/ArtTV.apk.idsig', 'archives/ArtTV.aab',
                   'signing/release.jks', 'signing/release.p12', 'signing/release.pem',
                   'signing/local.key', 'adbkey', 'adbkey.pub', '.env', '.env.local', 'app/local.properties',
                   '.idea/workspace.xml', '.vscode/settings.json', 'docs/.DS_Store']
        public = ['README.md', 'LICENSE', 'build.sh', 'app/AndroidManifest.xml',
                  'config.example.properties', 'app/assets/art/catalogue.json',
                  'docs/menu-preview.png', 'preview/art.js', 'tools/sony_channels.py',
                  'tests/ProjectTest.py']
        with tempfile.TemporaryDirectory() as temporary:
            subprocess.run(['git', 'init', '--quiet', temporary], check=True, capture_output=True)
            shutil.copyfile(ROOT / '.gitignore', Path(temporary) / '.gitignore')
            ignored = subprocess.run(['git', 'check-ignore', '--no-index', '--stdin'],
                                     input='\n'.join(private + public) + '\n', text=True,
                                     cwd=temporary, check=True, capture_output=True)
            self.assertEqual(private, ignored.stdout.splitlines())


if __name__ == '__main__':
    unittest.main()
