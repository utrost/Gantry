#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
RELEASE_VERSION="${1:-1.0.0}"
PROJECT_VERSION="$(python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
root = ET.parse(Path('pom.xml')).getroot()
ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
version = root.findtext('m:version', namespaces=ns)
if not version:
    raise SystemExit('Could not read project.version from pom.xml')
print(version)
PY
)"
./scripts/build.sh
mkdir -p dist
APP_SOURCE="app/target/app-${PROJECT_VERSION}.jar"
CLI_SOURCE="cli/target/cli-${PROJECT_VERSION}.jar"
if [[ ! -f "$APP_SOURCE" ]]; then
    echo "ERROR: expected GUI artifact not found: $APP_SOURCE" >&2
    exit 1
fi
if [[ ! -f "$CLI_SOURCE" ]]; then
    echo "ERROR: expected CLI artifact not found: $CLI_SOURCE" >&2
    exit 1
fi
cp "$APP_SOURCE" "dist/Gantry-${RELEASE_VERSION}.jar"
cp "$CLI_SOURCE" "dist/Gantry-CLI-${RELEASE_VERSION}.jar"
cp LICENSE README.md dist/
(cd dist && shasum -a 256 "Gantry-${RELEASE_VERSION}.jar" "Gantry-CLI-${RELEASE_VERSION}.jar" > SHA256SUMS)
echo "Release artifacts created in dist/"
echo "Project version: ${PROJECT_VERSION}"
echo "Release label: ${RELEASE_VERSION}"
