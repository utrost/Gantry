#!/usr/bin/env bash
# Linux GUI acceptance. Requires a JDK and a display (or xvfb-run).
set -euo pipefail
cd "$(dirname "$0")/.."
JAR="${1:-app/target/app-1.0.0.jar}"
RESULTS="${2:-dist/validation/gui}"
mkdir -p "$RESULTS"
RESULTS="$(cd "$RESULTS" && pwd)"
GUI_TEMP="$(mktemp -d -t gantry-gui-check-XXXXXX)"
trap 'rm -rf "$GUI_TEMP"' EXIT
GUI_PROFILE="$(mktemp -d "$RESULTS/profile-XXXXXX")"
javac -cp "$JAR" -d "$GUI_TEMP" scripts/gui/GuiAcceptance.java
for phase in seed recover; do
    timeout 120s java -Dsun.java2d.uiScale=1 -cp "$GUI_TEMP:$JAR" GuiAcceptance \
        "$PWD" "$GUI_PROFILE" "$phase" 2>&1 | tee "$RESULTS/$phase.log"
done
printf 'GUI acceptance passed. Evidence: %s\n' "$RESULTS"
