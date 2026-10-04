"""Verify installed CLI, offline resources and help resolution using bundled Java."""
import json
import os
from pathlib import Path
import platform
import subprocess
from release_payload import IMAGES, SAMPLES, validate_help


def verify_payload(launcher, version, workspace):
    windows = platform.system() == 'Windows'
    installed = launcher.parent
    payload = installed / 'app' if windows else installed.parent / 'lib/app'
    cli = installed / ('gantry-cli.exe' if windows else 'gantry-cli')
    help_launcher = installed / ('Gantry Help.exe' if windows else 'Gantry Help')
    required = [cli, help_launcher, payload / 'Gantry.jar', payload / 'Gantry-CLI.jar',
                payload / 'LICENSE', payload / 'VERSION.txt', payload / 'docs/index.html']
    required += [payload / 'docs/images' / name for name in IMAGES]
    required += [payload / 'docs/samples' / name for name in SAMPLES]
    missing = [str(path) for path in required if not path.is_file() or not path.stat().st_size]
    if missing:
        raise RuntimeError(f'Installed payload is incomplete: {missing}')
    validate_help(payload)
    if f'Gantry {version}\n' not in (payload / 'VERSION.txt').read_text(encoding='utf-8'):
        raise RuntimeError('Installed VERSION.txt does not match candidate')
    if version not in (payload / 'docs/index.html').read_text(encoding='utf-8'):
        raise RuntimeError('Offline guide does not identify candidate')
    environment = os.environ.copy()
    for key in ('JAVA_HOME', 'JDK_HOME', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        environment.pop(key, None)
    environment['PATH'] = str(Path(environment.get('SystemRoot', 'C:/Windows')) / 'System32') if windows else '/nonexistent'
    # Work outside the installation to verify path resolution and avoid modifying installed files.
    workspace.mkdir(parents=True, exist_ok=True)
    def invoke(executable, *arguments):
        result = subprocess.run([str(executable.resolve()), *map(str, arguments)], cwd=workspace,
                                env=environment, check=True, timeout=60, capture_output=True, text=True)
        return result.stdout + result.stderr
    help_text = invoke(cli, '--help')
    if '--input' not in help_text or '--output' not in help_text:
        raise RuntimeError(f'Native CLI did not return usage: {help_text}')
    (workspace / 'native-cli-help.txt').write_text(help_text, encoding='utf-8')
    output = workspace / 'native-cli-output.json'
    output.unlink(missing_ok=True)
    log = invoke(cli, '-i', payload / 'docs/samples/simple-line.svg', '-o', output)
    (workspace / 'native-cli-import.log').write_text(log, encoding='utf-8')
    model = json.loads(output.read_text(encoding='utf-8'))
    if not model.get('layers') or not any(layer.get('commands') for layer in model['layers']):
        raise RuntimeError('Native CLI did not import the shipped SVG sample')
    report = workspace / 'offline-help-location.txt'
    report.unlink(missing_ok=True)
    invoke(help_launcher, '--offline-help-check', report)
    if Path(report.read_text(encoding='utf-8')).resolve() != (payload / 'docs/index.html').resolve():
        raise RuntimeError('Help shortcut launcher did not resolve the installed offline guide')
    return {'status': 'passed', 'files': [str(path) for path in required],
            'checks': ['complete-offline-resources', 'offline-links-resolve', 'native-cli-help',
                       'native-cli-import-shipped-sample', 'help-launcher-resolves-local-guide']}
