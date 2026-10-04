#!/usr/bin/env bash
# Requires the packaged app, JDK, ffmpeg, and a display (or xvfb-run).
set -euo pipefail
cd "$(dirname "$0")/.."
DEMO_OUTPUT="${1:-dist/demo}"
mkdir -p "$DEMO_OUTPUT"
DEMO_OUTPUT="$(cd "$DEMO_OUTPUT" && pwd)"
DEMO_TEMP="$(mktemp -d -t gantry-demo-XXXXXX)"
trap 'rm -rf "$DEMO_TEMP"' EXIT
javac -cp app/target/app-1.0.0.jar -d "$DEMO_TEMP" scripts/gui/GuiAcceptance.java scripts/gui/FirstPlotDemo.java
timeout 120s java -Dsun.java2d.uiScale=1 -cp "$DEMO_TEMP:app/target/app-1.0.0.jar" FirstPlotDemo "$PWD" "$DEMO_TEMP/profile" "$DEMO_OUTPUT"
ffmpeg -hide_banner -loglevel error -y -framerate 1/10 -i "$DEMO_OUTPUT/step-%02d.png" \
    -vf 'fps=25,format=yuv420p' -c:v libx264 -preset fast -t 60 -movflags +faststart \
    "$DEMO_OUTPUT/Gantry-first-plot-mock.mp4"
printf 'One-minute mock walkthrough: %s\n' "$DEMO_OUTPUT/Gantry-first-plot-mock.mp4"
