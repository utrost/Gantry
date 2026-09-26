#!/usr/bin/env python3
"""Install an older fixture, upgrade to the candidate, then uninstall; CI hosts only."""
import argparse
import json
import os
from pathlib import Path, PurePosixPath
import platform
import shlex
import subprocess

from accept_installer import acceptance, verify_preserved
from release import native_version
from verify_payload import verify_payload


def package_operation(package, install, log):
    if platform.system() == 'Windows':
        command = ['msiexec.exe', '/i' if install else '/x', str(package), '/qn', '/norestart', '/L*v', str(log)]
        if install:
            command.append(f"INSTALLDIR={Path(os.environ['LOCALAPPDATA']) / 'Gantry'}")
        result = subprocess.run(command, timeout=180)
        if result.returncode not in (0, 3010):
            raise RuntimeError(f'MSI operation failed ({result.returncode}); see {log}')
    else:
        command = ['sudo', 'apt-get', 'install', '-y', str(package)] if install else ['sudo', 'apt-get', 'remove', '-y', 'gantry']
        with log.open('w') as output:
            subprocess.run(command, check=True, stdout=output, stderr=subprocess.STDOUT, timeout=180)


def installed_version():
    if platform.system() == 'Windows':
        from windows_installation import installed_products, per_user_version
        return per_user_version(installed_products())
    result = subprocess.run(['dpkg-query', '-W', '-f=${Version}', 'gantry'], check=True, capture_output=True, text=True)
    return result.stdout.split('-')[0]


def shortcut_paths():
    if platform.system() == 'Windows':
        return [Path(os.environ['APPDATA']) / 'Microsoft/Windows/Start Menu/Programs/Gantry' / name
                for name in ('Gantry.lnk', 'Gantry Help.lnk')]
    # sudo may discard XDG_DATA_DIRS; inspect both its defaults and configured roots.
    roots = ['/usr/local/share', '/usr/share', *os.environ.get('XDG_DATA_DIRS', '').split(':')]
    return list(dict.fromkeys(Path(root) / 'applications/gantry-Gantry.desktop'
                              for root in roots if root and PurePosixPath(root).is_absolute()))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('baseline', type=Path)
    parser.add_argument('candidate', type=Path)
    parser.add_argument('baseline_version')
    parser.add_argument('candidate_version')
    parser.add_argument('workspace', type=Path)
    args = parser.parse_args()
    if os.environ.get('CI') != 'true':
        parser.error('This installation-changing check is restricted to disposable CI hosts (CI=true)')
    if platform.system() not in ('Linux', 'Windows'):
        parser.error('Only Linux/Windows installers are supported')
    workspace = args.workspace.resolve()
    workspace.mkdir(parents=True, exist_ok=True)
    launcher = (Path(os.environ['LOCALAPPDATA']) / 'Gantry/Gantry.exe'
                if platform.system() == 'Windows' else Path('/opt/gantry/bin/Gantry'))
    if launcher.exists():
        raise RuntimeError('Refusing to replace an existing Gantry installation on this test host')
    candidate = args.candidate.resolve()
    baseline = args.baseline.resolve()
    result = {'baseline': args.baseline_version, 'candidate': args.candidate_version,
              'syntheticBaseline': True, 'checks': []}
    installed = None
    try:
        package_operation(baseline, True, workspace / 'install-baseline.log')
        installed = baseline
        if installed_version() != native_version(args.baseline_version):
            raise RuntimeError('Baseline installer metadata does not match')
        acceptance(launcher, args.baseline_version, workspace, 'seed')
        result['checks'].append('baseline-installed-and-state-seeded')
        package_operation(candidate, True, workspace / 'upgrade.log')
        installed = candidate
        if installed_version() != native_version(args.candidate_version):
            raise RuntimeError('Upgrade did not replace the installed version')
        result['checks'].append('native-upgrade-replaced-product')
        shortcuts = [path for path in shortcut_paths() if path.is_file()]
        if not shortcuts or (platform.system() == 'Windows' and len(shortcuts) != 2):
            raise RuntimeError(f'Application-menu shortcut is missing; searched {shortcut_paths()}')
        if platform.system() == 'Linux':
            for shortcut in shortcuts:
                commands = [line[5:] for line in shortcut.read_text().splitlines() if line.startswith('Exec=')]
                if len(commands) != 1 or shlex.split(commands[0])[0] != str(launcher):
                    raise RuntimeError(f'Menu shortcut does not launch the installed app: {shortcut}')
        result['menuShortcuts'] = [str(path) for path in shortcuts]
        result['checks'].append('application-menu-shortcut-present')
        if platform.system() == 'Windows':
            if (shortcuts[0].parent / 'gantry-cli.lnk').exists():
                raise RuntimeError('CLI should not have a Start menu shortcut')
            result['checks'].append('windows-per-user-scope-and-help-shortcut')
            from windows_installation import installed_products
            result['windowsProducts'] = installed_products()
        result['payload'] = verify_payload(launcher, args.candidate_version, workspace / 'payload')
        result['checks'].append('complete-installed-cli-and-offline-help')
        acceptance(launcher, args.candidate_version, workspace, 'verify')
        result['checks'].append('profile-preserved-and-installed-workflows-passed')
        subprocess.run([os.sys.executable, str(Path(__file__).with_name('smoke_installer.py')), str(launcher), args.candidate_version],
                       check=True, timeout=60)
        result['checks'].append('upgraded-gui-launch')
        package_operation(candidate, False, workspace / 'uninstall.log')
        installed = None
        if launcher.exists() or (launcher.parent / ('app' if platform.system() == 'Windows' else '../lib/app')).exists() or any(path.exists() for path in shortcut_paths()):
            raise RuntimeError('Uninstall left the native launcher or menu shortcut behind')
        verify_preserved(workspace)
        result['checks'].append('uninstall-preserved-user-files')
        result['status'] = 'passed'
    except Exception as error:
        result['status'] = 'failed'
        result['error'] = str(error)
        raise
    finally:
        (workspace / 'upgrade-result.json').write_text(json.dumps(result, indent=2))
        if installed is not None:
            package_operation(installed, False, workspace / 'cleanup.log')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
