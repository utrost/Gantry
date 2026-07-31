#!/usr/bin/env bash
# Builds every module and runs tests. Works on Linux and macOS.
# Usage: scripts/build.sh [--skip-tests]
set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v mvn >/dev/null 2>&1; then
    echo "ERROR: Maven (mvn) was not found on PATH. Install Maven 3.8+ and Java 17+." >&2
    exit 1
fi

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
GOAL="clean install"
if [[ "${1:-}" == "--skip-tests" ]]; then
    GOAL="clean install -DskipTests"
fi

echo "Building Gantry (mvn $GOAL)..."
mvn $GOAL

echo
echo "Build complete. The standalone app jar is at app/target/app-${PROJECT_VERSION}.jar"
