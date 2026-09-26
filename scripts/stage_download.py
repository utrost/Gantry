#!/usr/bin/env python3
"""Separate platform installers from portable Java downloads; checksums cover each artifact."""
import argparse
from pathlib import Path
import shutil
from release import ROOT, checksums, native_version


def stage(version, system):
    native_version(version)
    source = ROOT / 'dist' / version
    suffix = {'Windows': 'windows-x64.msi', 'Linux': 'linux-x64.deb'}[system]
    directory = ROOT / 'dist/downloads' / system.lower()
    if directory.exists():
        shutil.rmtree(directory)
    directory.mkdir(parents=True)
    names = [f'Gantry-{version}-{suffix}', 'LICENSE', 'README.md', 'VERSION.txt']
    for name in names:
        shutil.copy2(source / name, directory / name)
    checksums(directory, [directory / name for name in names])
    if system == 'Linux':
        portable = ROOT / 'dist/downloads/portable'
        if portable.exists():
            shutil.rmtree(portable)
        portable.mkdir(parents=True)
        names = [f'Gantry-{version}.jar', f'Gantry-CLI-{version}.jar',
                 f'Gantry-{version}-docs.zip', 'LICENSE', 'README.md', 'VERSION.txt']
        for name in names:
            shutil.copy2(source / name, portable / name)
        checksums(portable, [portable / name for name in names])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('version')
    parser.add_argument('system', choices=['Windows', 'Linux'])
    args = parser.parse_args()
    stage(args.version, args.system)
