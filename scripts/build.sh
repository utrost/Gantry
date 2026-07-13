#!/usr/bin/env bash
# Builds every module and runs tests. Works on Linux and macOS.
# Usage: scripts/build.sh [--skip-tests]
set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v mvn >/dev/null 2>&1; then
    echo "ERROR: Maven (mvn) was not found on PATH. Install Maven 3.8+ and Java 17+." >&2
    exit 1
fi

GOAL="clean install"
if [[ "${1:-}" == "--skip-tests" ]]; then
    GOAL="clean install -DskipTests"
fi

echo "Building Gantry (mvn $GOAL)..."
mvn $GOAL

echo
echo "Build complete. The standalone app jar is at app/target/app-1.0.0.jar"
