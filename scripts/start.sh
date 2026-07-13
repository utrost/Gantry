#!/usr/bin/env bash
# Launches the Gantry GUI. Works on Linux and macOS.
# Builds the app jar first if it doesn't exist yet.
set -euo pipefail
cd "$(dirname "$0")/.."

JAR="app/target/app-1.0.0.jar"

if ! command -v java >/dev/null 2>&1; then
    echo "ERROR: Java was not found on PATH. Install Java 17+." >&2
    exit 1
fi

if [[ ! -f "$JAR" ]]; then
    echo "$JAR not found, building it first..."
    scripts/build.sh
fi

echo "Starting Gantry..."
exec java -jar "$JAR" "$@"
