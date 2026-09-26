#!/usr/bin/env bash
# Capture the real GUI in a temporary mock profile. Requires a visible desktop or Xvfb.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$PWD"
JAR="${1:-dist/1.0.0-rc.2/Gantry-1.0.0-rc.2.jar}"
if [[ ! -f "$JAR" ]]; then
    echo "Build a GUI JAR first, or pass its path as the first argument." >&2
    exit 1
fi
CAPTURE_TEMP="$(mktemp -d -t gantry-screenshots-XXXXXX)"
trap 'rm -rf "$CAPTURE_TEMP"' EXIT
javac -cp "$JAR" -d "$CAPTURE_TEMP" scripts/screenshots/CaptureScreenshots.java
java -Dsun.java2d.uiScale=1 -cp "$CAPTURE_TEMP:$JAR" CaptureScreenshots "$ROOT" "$ROOT/docs/images" "$CAPTURE_TEMP/profile"
