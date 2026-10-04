#!/usr/bin/env python3
"""Launch a packaged GUI with no system Java on PATH and an isolated mock profile."""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('launcher', type=Path)
parser.add_argument('version')
args = parser.parse_args()
launcher = args.launcher.resolve()
with tempfile.TemporaryDirectory(prefix='gantry-launch-') as temp:
    report = Path(temp) / 'ready.txt'
    environment = os.environ.copy()
    for key in ('JAVA_HOME', 'JDK_HOME', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        environment.pop(key, None)
    environment['PATH'] = os.path.join(environment.get('SystemRoot', 'C:\\Windows'), 'System32') if os.name == 'nt' else '/nonexistent'
    subprocess.run([str(launcher), '--smoke-test', str(report)], cwd=temp,
                   env=environment, check=True, timeout=45)
    assert report.read_text() == f'GUI ready {args.version}', 'GUI/version smoke check failed'
    print(report.read_text())
