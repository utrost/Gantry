#!/usr/bin/env python3
"""Build versioned JARs and optional native installers using the host JDK 17."""
import argparse
import hashlib
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
UPGRADE_UUID = "8ac1c562-5cd4-46bb-a64d-7b21af9e350a"


def native_version(version):
    match = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)(?:-rc\.([1-9]\d*))?", version)
    if not match:
        raise ValueError("Use MAJOR.MINOR.PATCH or MAJOR.MINOR.PATCH-rc.N")
    major, minor, patch = map(int, match.group(1, 2, 3))
    candidate = int(match.group(4)) if match.group(4) else 99
    if major > 255 or minor > 255 or patch > 654 or not 1 <= candidate <= 99 or (match.group(4) and candidate == 99):
        raise ValueError("Installer limits: major/minor <=255, patch <=654, RC 1..98")
    # MSI compares three numeric fields. Reserve 99 for stable so it upgrades all RCs.
    return f"{major}.{minor}.{patch * 100 + candidate}"


def run(*args):
    subprocess.run([str(a) for a in args], cwd=ROOT, check=True)


def verify_jar(path, version):
    with zipfile.ZipFile(path) as jar:
        manifest = jar.read("META-INF/MANIFEST.MF").decode().replace("\r\n ", "")
        if f"Implementation-Version: {version}\r\n" not in manifest:
            raise RuntimeError(f"Wrong embedded version in {path}")


def checksums(directory, files):
    lines = [f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n" for p in sorted(files)]
    (directory / "SHA256SUMS").write_text("".join(lines), encoding="ascii")


def installer(version, output):
    system = platform.system()
    if system not in ("Windows", "Linux") or platform.machine().lower() not in ("amd64", "x86_64"):
        raise RuntimeError("Installers currently support Windows/Linux x86-64 hosts only")
    kind = "msi" if system == "Windows" else "deb"
    with tempfile.TemporaryDirectory(prefix="gantry-package-") as temp:
        temp = Path(temp)
        source = temp / "input"
        source.mkdir()
        shutil.copy2(output / f"Gantry-{version}.jar", source / "Gantry.jar")
        shutil.copy2(ROOT / "LICENSE", source)
        command = ["jpackage", "--type", kind, "--name", "Gantry", "--app-version", native_version(version),
                   "--vendor", "Gantry", "--description", "SVG preparation and pen plotter studio",
                   "--input", source, "--main-jar", "Gantry.jar",
                   "--main-class", "org.trostheide.gantry.app.GantryApp", "--dest", temp / "out",
                   "--license-file", ROOT / "LICENSE", "--about-url", "https://github.com/utrost/Gantry"]
        if system == "Windows":
            command += ["--win-menu", "--win-menu-group", "Gantry", "--win-shortcut",
                        "--win-dir-chooser", "--win-upgrade-uuid", UPGRADE_UUID]
        else:
            command += ["--linux-shortcut", "--linux-menu-group", "Graphics", "--linux-package-name", "gantry"]
        run(*command)
        produced, = (temp / "out").glob(f"*.{kind}")
        target = output / f"Gantry-{version}-{system.lower()}-x64.{kind}"
        shutil.copy2(produced, target)
        return target


def upgrade_fixture(version, baseline, output):
    """Package current code with an older identity solely to exercise native upgrades."""
    if tuple(map(int, native_version(baseline).split('.'))) >= tuple(map(int, native_version(version).split('.'))):
        raise ValueError("Upgrade fixture must be older than the candidate")
    fixture = ROOT / "dist" / "upgrade-fixture" / version / baseline
    fixture.mkdir(parents=True, exist_ok=True)
    target = fixture / f"Gantry-{baseline}.jar"
    with zipfile.ZipFile(output / f"Gantry-{version}.jar") as original, zipfile.ZipFile(target, 'w') as rewritten:
        for info in original.infolist():
            data = original.read(info.filename)
            if info.filename == 'META-INF/MANIFEST.MF':
                data = data.replace(f'Implementation-Version: {version}\r\n'.encode(),
                                    f'Implementation-Version: {baseline}\r\n'.encode())
            rewritten.writestr(info, data)
    verify_jar(target, baseline)
    return installer(baseline, fixture)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("version")
    parser.add_argument("--installer", action="store_true", help="also build the host platform's installer")
    parser.add_argument("--upgrade-from", help="build a synthetic older installer for upgrade validation; never publish it")
    args = parser.parse_args()
    if args.upgrade_from and not args.installer:
        parser.error("--upgrade-from requires --installer")
    if args.upgrade_from:
        older = tuple(map(int, native_version(args.upgrade_from).split('.')))
        current = tuple(map(int, native_version(args.version).split('.')))
        if older >= current:
            parser.error("--upgrade-from must be older than the candidate")
    native_version(args.version)  # Validate before invoking tools or constructing paths.
    project_version = ET.parse(ROOT / "pom.xml").getroot().findtext("{http://maven.apache.org/POM/4.0.0}version")
    maven = shutil.which("mvn.cmd" if os.name == "nt" else "mvn")
    if not maven:
        raise RuntimeError("Maven is required on PATH")
    run(maven, "-B", "clean", "package", f"-Dgantry.release.version={args.version}")
    output = ROOT / "dist" / args.version
    output.mkdir(parents=True, exist_ok=True)
    files = []
    for module, label in (("app", "Gantry"), ("cli", "Gantry-CLI")):
        target = output / f"{label}-{args.version}.jar"
        shutil.copy2(ROOT / module / "target" / f"{module}-{project_version}.jar", target)
        verify_jar(target, args.version)
        files.append(target)
    for name in ("LICENSE", "README.md"):
        shutil.copy2(ROOT / name, output)
        files.append(output / name)
    run("java", "-jar", output / f"Gantry-CLI-{args.version}.jar", "--help")
    if args.installer:
        files.append(installer(args.version, output))
    if args.upgrade_from:
        print(f"Upgrade test fixture (not a release asset): {upgrade_fixture(args.version, args.upgrade_from, output)}")
    checksums(output, files)
    print(f"Release artifacts: {output}")


if __name__ == "__main__":
    main()
