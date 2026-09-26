import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import release
import release_payload
import stage_download

class ReleasePayloadTest(unittest.TestCase):
    def test_offline_archive_contains_all_linked_assets_and_embedded_version(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            archive = release_payload.docs_archive(release.ROOT, output, '1.0.0-rc.2')
            with zipfile.ZipFile(archive) as bundle:
                bundle.extractall(output / 'extracted')
            release_payload.validate_help(output / 'extracted')
            html = (output / 'extracted/docs/index.html').read_text()
            self.assertIn('1.0.0-rc.2', html)
            self.assertNotIn('@VERSION@', html)
            (output / 'extracted/docs/images/workspace-layer-preview.png').unlink()
            with self.assertRaisesRegex(ValueError, 'missing/escaping link'):
                release_payload.validate_help(output / 'extracted')

    def test_readme_is_standalone_and_installer_artifacts_do_not_mix_in_portable_jars(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            output = root / 'dist/1.0.0-rc.2'
            output.mkdir(parents=True)
            release_payload.write_download_readme(output, '1.0.0-rc.2')
            readme = (output / 'README.md').read_text()
            self.assertNotIn('![', readme)
            self.assertNotIn('](docs/', readme)
            for name in ['LICENSE', 'VERSION.txt', 'Gantry-1.0.0-rc.2.jar', 'Gantry-CLI-1.0.0-rc.2.jar',
                         'Gantry-1.0.0-rc.2-docs.zip', 'Gantry-1.0.0-rc.2-linux-x64.deb',
                         'Gantry-1.0.0-rc.2-windows-x64.msi']:
                (output / name).write_bytes(b'fixture')
            with patch.object(stage_download, 'ROOT', root):
                stage_download.stage('1.0.0-rc.2', 'Windows')
                (root / 'dist/downloads/windows/stale.jar').write_bytes(b'old')
                stage_download.stage('1.0.0-rc.2', 'Windows')
                stage_download.stage('1.0.0-rc.2', 'Linux')
            for system in ['windows', 'linux', 'portable']:
                staged = root / 'dist/downloads' / system
                for line in (staged / 'SHA256SUMS').read_text().splitlines():
                    checksum, name = line.split('  ')
                    self.assertEqual(checksum, hashlib.sha256((staged / name).read_bytes()).hexdigest())
                self.assertEqual(bool(list(staged.glob('*.jar'))), system == 'portable')
                self.assertTrue((staged / 'README.md').is_file())
                self.assertTrue((staged / 'LICENSE').is_file())

    def test_windows_installer_has_console_cli_offline_help_and_per_user_scope(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            for label in ['Gantry', 'Gantry-CLI']:
                (output / f'{label}-1.0.0-rc.2.jar').write_bytes(b'fixture')
            def package(*command):
                source = Path(command[command.index('--input') + 1])
                release_payload.validate_help(source)
                self.assertTrue((source / 'Gantry-CLI.jar').is_file())
                self.assertIn('--win-per-user-install', command)
                properties = [str(command[i+1]).split('=', 1) for i, item in enumerate(command) if item == '--add-launcher']
                cli = Path(dict(properties)['gantry-cli']).read_text()
                self.assertIn('win-console=true', cli)
                self.assertIn('win-menu=false', cli)
                self.assertIn('win-shortcut=false', cli)
                help_text = Path(dict(properties)['Gantry Help']).read_text()
                self.assertIn('arguments=--offline-help', help_text)
                self.assertIn('win-menu=true', help_text)
                target = Path(command[command.index('--dest') + 1])
                target.mkdir()
                (target / 'Gantry.msi').write_bytes(b'installer fixture')
            with patch('release.platform.system', return_value='Windows'), \
                 patch('release.platform.machine', return_value='AMD64'), patch('release.run', side_effect=package):
                target = release.installer('1.0.0-rc.2', output)
            self.assertEqual(target.read_bytes(), b'installer fixture')

if __name__ == '__main__':
    unittest.main()
