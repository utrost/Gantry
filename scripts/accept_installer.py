#!/usr/bin/env python3
"""Exercise the installed runtime using persistent isolated state and JSON evidence."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

PERSISTED = ('config.json', 'plot-history.json', '.gantry-recovery', 'fixture.gantry')
EXPECTED_CHECKS = {'settings-preserved', 'project-and-recovery-preserved', 'history-preserved',
                   'svg-import', 'mock-plot-complete', 'mock-cancel-pen-up', 'gcode-export', 'raster-vectorize-import'}


def profile_directory(workspace):
    return workspace / 'user data with spaces' / ('Gantry' if os.name == 'nt' else 'gantry')


def profile_hashes(profile):
    return {name: hashlib.sha256((profile / name).read_bytes()).hexdigest() for name in PERSISTED}


def verify_preserved(workspace):
    expected = json.loads((workspace / 'state-sha256.json').read_text())
    actual = profile_hashes(profile_directory(workspace))
    if actual != expected:
        raise RuntimeError('Persisted settings/history/recovery/project changed during upgrade or verification')
    return actual


def acceptance(launcher, version, workspace, phase):
    workspace = workspace.resolve()
    workspace.mkdir(parents=True, exist_ok=True)
    profile = profile_directory(workspace)
    if phase == 'verify':
        verify_preserved(workspace)
    cwd = workspace / f'launch directory {phase}'
    cwd.mkdir(exist_ok=True)
    report = workspace / f'{phase}.json'
    # A failed/repeated launch must never reuse a stale success report.
    report.unlink(missing_ok=True)
    environment = os.environ.copy()
    for key in ('JAVA_HOME', 'JDK_HOME', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        environment.pop(key, None)
    environment['APPDATA' if os.name == 'nt' else 'XDG_CONFIG_HOME'] = str(profile.parent)
    environment['GANTRY_SELF_TEST_EXPECT_CONFIG'] = str(profile / 'config.json')
    environment['PATH'] = os.path.join(environment.get('SystemRoot', 'C:\\Windows'), 'System32') if os.name == 'nt' else '/nonexistent'
    with (workspace / f'{phase}.log').open('w', encoding='utf-8') as log:
        subprocess.run([str(launcher.resolve()), '--self-test', phase, str(profile), str(report)],
                       cwd=cwd, env=environment, check=True, timeout=90,
                       stdout=log, stderr=subprocess.STDOUT)
    result = json.loads(report.read_text())
    if result.get('status') != 'passed' or result.get('version') != version or not result.get('defaultProfileVerified') or set(result.get('checks', [])) != EXPECTED_CHECKS:
        raise RuntimeError(f'Installed acceptance failed or reported the wrong version: {result}')
    if phase == 'seed':
        (workspace / 'state-sha256.json').write_text(json.dumps(profile_hashes(profile), indent=2))
    else:
        verify_preserved(workspace)
    print(f'{phase}: {version}: {len(EXPECTED_CHECKS)} installed-runtime checks passed')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('launcher', type=Path)
    parser.add_argument('version')
    parser.add_argument('workspace', type=Path)
    parser.add_argument('phase', choices=['seed', 'verify'])
    args = parser.parse_args()
    acceptance(args.launcher, args.version, args.workspace, args.phase)


if __name__ == '__main__':
    main()
