"""Checks the installer version ordering used for RC-to-stable upgrades."""
import unittest
import tempfile
import zipfile
from pathlib import Path
from unittest.mock import patch
import release
from test_installer_upgrade import shortcut_paths
from release import native_version

class InstallerVersionTest(unittest.TestCase):
    def test_upgrade_order(self):
        versions = ['1.0.0-rc.1', '1.0.0-rc.2', '1.0.0', '1.0.1-rc.1', '1.0.1', '1.1.0-rc.1']
        numeric = [tuple(map(int, native_version(v).split('.'))) for v in versions]
        self.assertEqual(numeric, sorted(set(numeric)))

    def test_invalid_versions(self):
        for value in ['../1.0.0', '1.0.0;echo', '1.0.0-rc.0', '1.0.0-rc.99', '256.0.0', '1.0.655']:
            with self.subTest(value=value), self.assertRaises(ValueError):
                native_version(value)

class UpgradeFixtureTest(unittest.TestCase):
    def test_fixture_relabels_only_manifest_and_stays_out_of_release_directory(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / 'dist/1.0.0-rc.2'
            output.mkdir(parents=True)
            candidate = output / 'Gantry-1.0.0-rc.2.jar'
            with zipfile.ZipFile(candidate, 'w') as jar:
                jar.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\r\nImplementation-Version: 1.0.0-rc.2\r\n\r\n')
                jar.writestr('Example.class', b'unchanged executable bytes')
            original = candidate.read_bytes()
            with patch.object(release, 'ROOT', root), patch.object(release, 'installer') as package:
                release.upgrade_fixture('1.0.0-rc.2', '1.0.0-rc.1', output)
                fixture = package.call_args.args[1] / 'Gantry-1.0.0-rc.1.jar'
                self.assertFalse(fixture.is_relative_to(output))
                release.verify_jar(fixture, '1.0.0-rc.1')
                with zipfile.ZipFile(fixture) as jar:
                    self.assertEqual(jar.read('Example.class'), b'unchanged executable bytes')
            self.assertEqual(candidate.read_bytes(), original)

    def test_rejects_equal_or_newer_upgrade_fixture_before_writing(self):
        for baseline in ['1.0.0-rc.2', '1.0.0', '2.0.0']:
            with self.subTest(baseline=baseline), self.assertRaises(ValueError):
                release.upgrade_fixture('1.0.0-rc.2', baseline, Path('unused'))

class ShortcutDiscoveryTest(unittest.TestCase):
    def test_searches_both_system_defaults_and_absolute_xdg_roots(self):
        with patch('test_installer_upgrade.platform.system', return_value='Linux'), \
             patch.dict('os.environ', {'XDG_DATA_DIRS': '/opt/share:relative:/usr/share'}):
            paths = shortcut_paths()
        self.assertIn(Path('/usr/local/share/applications/gantry-Gantry.desktop'), paths)
        self.assertIn(Path('/usr/share/applications/gantry-Gantry.desktop'), paths)
        self.assertIn(Path('/opt/share/applications/gantry-Gantry.desktop'), paths)
        self.assertEqual(len(paths), 3)

if __name__ == '__main__':
    unittest.main()
