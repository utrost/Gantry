"""Checks the installer version ordering used for RC-to-stable upgrades."""
import unittest
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

if __name__ == '__main__':
    unittest.main()
