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
javac -cp "$JAR" -d "$GUI_TEMP" scripts/gui/GuiAcceptance.java scripts/gui/SerialSimulatorAcceptance.java scripts/gui/RoadmapAcceptance.java scripts/gui/WizardLifecycleAcceptance.java scripts/gui/ImportLayouts.java scripts/gui/PenFillAcceptance.java scripts/gui/WorkflowIssueAcceptance.java scripts/gui/ButtonStateAcceptance.java scripts/gui/KeyboardWorkflowAcceptance.java scripts/gui/VectorizeLifecycleAcceptance.java scripts/gui/PreparationIntegrationAcceptance.java
for phase in seed defer recover; do
    timeout 120s java -Dsun.java2d.uiScale=1 -Dgantry.gui.realKeyboard="${GANTRY_GUI_REAL_KEYBOARD:-false}" -cp "$GUI_TEMP:$JAR" GuiAcceptance \
        "$PWD" "$GUI_PROFILE" "$phase" 2>&1 | tee "$RESULTS/$phase.log"
done
timeout 150s java -cp "$GUI_TEMP:$JAR" SerialSimulatorAcceptance "$PWD" "$RESULTS/serial-simulator" 2>&1 | tee "$RESULTS/serial-simulator.log"
ROADMAP_PROFILE="$(mktemp -d "$RESULTS/roadmap-profile-XXXXXX")"
for phase in seed recover; do
    timeout 150s java -cp "$GUI_TEMP:$JAR" RoadmapAcceptance "$PWD" "$ROADMAP_PROFILE" "$phase" 2>&1 | tee "$RESULTS/roadmap-$phase.log"
done
timeout 30s java -cp "$GUI_TEMP:$JAR" org.trostheide.gantry.app.gui.WizardLifecycleAcceptance 2>&1 | tee "$RESULTS/wizard.log"
timeout 90s java -cp "$GUI_TEMP:$JAR" ImportLayouts "$PWD" "$RESULTS/imports" 2>&1 | tee "$RESULTS/imports.log"
timeout 90s java -cp "$GUI_TEMP:$JAR" org.trostheide.gantry.app.gui.PenFillAcceptance "$RESULTS/pen-fill" 2>&1 | tee "$RESULTS/pen-fill.log"
timeout 90s java -Dsun.java2d.uiScale=1 -cp "$GUI_TEMP:$JAR" org.trostheide.gantry.app.gui.VectorizeLifecycleAcceptance 2>&1 | tee "$RESULTS/trace-lifecycle.log"
timeout 180s java -Dsun.java2d.uiScale=1 -cp "$GUI_TEMP:$JAR" WorkflowIssueAcceptance "$RESULTS/workflows" 2>&1 | tee "$RESULTS/workflows.log"
timeout 90s java -Dsun.java2d.uiScale=1 -cp "$GUI_TEMP:$JAR" PreparationIntegrationAcceptance "$RESULTS/preparation" 2>&1 | tee "$RESULTS/preparation.log"
timeout 120s java -Dsun.java2d.uiScale=1 -cp "$GUI_TEMP:$JAR" PreparationIntegrationAcceptance "$RESULTS/upright-migration" legacy 2>&1 | tee "$RESULTS/upright-migration.log"
if [[ "${GANTRY_GUI_REAL_KEYBOARD:-false}" == true ]]; then
    timeout 90s java -Dsun.java2d.uiScale=1 -cp "$GUI_TEMP:$JAR" org.trostheide.gantry.app.gui.ButtonStateAcceptance "$RESULTS/button-states" 2>&1 | tee "$RESULTS/button-states.log"
    timeout 180s java -Dsun.java2d.uiScale=1 -cp "$GUI_TEMP:$JAR" KeyboardWorkflowAcceptance "$PWD" 2>&1 | tee "$RESULTS/keyboard.log"
fi
printf 'GUI acceptance passed. Evidence: %s\n' "$RESULTS"
