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
javac -cp "$JAR" -d "$GUI_TEMP" scripts/gui/GuiAcceptance.java scripts/gui/WizardLifecycleAcceptance.java scripts/gui/ImportLayouts.java
for phase in seed defer recover; do
    timeout 120s java -Dsun.java2d.uiScale=1 -Dgantry.gui.realKeyboard="${GANTRY_GUI_REAL_KEYBOARD:-false}" -cp "$GUI_TEMP:$JAR" GuiAcceptance \
        "$PWD" "$GUI_PROFILE" "$phase" 2>&1 | tee "$RESULTS/$phase.log"
done
timeout 30s java -cp "$GUI_TEMP:$JAR" org.trostheide.gantry.app.gui.WizardLifecycleAcceptance 2>&1 | tee "$RESULTS/wizard.log"
timeout 90s java -cp "$GUI_TEMP:$JAR" ImportLayouts "$PWD" "$RESULTS/imports" 2>&1 | tee "$RESULTS/imports.log"
printf 'GUI acceptance passed. Evidence: %s\n' "$RESULTS"
