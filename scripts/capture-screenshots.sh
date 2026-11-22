#!/usr/bin/env bash
# Capture the real GUI in a temporary mock profile. Requires a visible desktop or Xvfb.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$PWD"
JAR="${1:-app/target/app-1.0.0.jar}"
THEMES="${2:-All}"
case "$THEMES" in
    Dark) WORKSPACE_THEMES=(Dark) ;;
    All) WORKSPACE_THEMES=(Dark Light) ;;
    *) echo "Usage: $0 [app-jar] [Dark|All]" >&2; exit 2 ;;
esac
if [[ ! -f "$JAR" ]]; then
    echo "Build a GUI JAR first, or pass its path as the first argument." >&2
    exit 1
fi
CAPTURE_TEMP="$(mktemp -d -t gantry-screenshots-XXXXXX)"
trap 'rm -rf "$CAPTURE_TEMP"' EXIT
javac -cp "$JAR" -d "$CAPTURE_TEMP" scripts/screenshots/CaptureScreenshots.java
java -Dsun.java2d.uiScale=1 -cp "$CAPTURE_TEMP:$JAR" CaptureScreenshots "$ROOT" "$ROOT/docs/images" "$CAPTURE_TEMP/profile"

# Workspace states in the requested themes, import dialogs and physical fill tools.
javac -cp "$JAR" -d "$CAPTURE_TEMP" scripts/gui/GuiAcceptance.java scripts/gui/WorkspaceLayouts.java scripts/gui/ImportLayouts.java scripts/gui/PenFillAcceptance.java scripts/screenshots/CaptureFillScreenshots.java
for theme in "${WORKSPACE_THEMES[@]}"; do
    java -Dsun.java2d.uiScale=1 -cp "$CAPTURE_TEMP:$JAR" WorkspaceLayouts "$ROOT" "$CAPTURE_TEMP/$theme" "$theme" Comfortable
    for shot in "$CAPTURE_TEMP/$theme/"*.png; do
        name="$(basename "$shot")"
        if [[ "$theme" == Light ]]; then name="light-$name"; fi
        cp "$shot" "$ROOT/docs/images/workspace/$name"
    done
done
cp "$ROOT/docs/images/workspace/prepared.png" "$ROOT/docs/images/workspace/dark-prepared.png"
cp "$ROOT/docs/images/workspace/machine.png" "$ROOT/docs/images/workspace/disabled-machine.png"
if [[ "$THEMES" == All ]]; then
    cp "$ROOT/docs/images/workspace/light-machine.png" "$ROOT/docs/images/workspace/light-disabled-machine.png"
fi
java -cp "$CAPTURE_TEMP:$JAR" ImportLayouts "$ROOT" "$ROOT/docs/images/workspace"
java -cp "$CAPTURE_TEMP:$JAR" org.trostheide.gantry.app.gui.PenFillAcceptance "$CAPTURE_TEMP/fill"
cp "$CAPTURE_TEMP/fill/fill-dark.png" "$ROOT/docs/images/pen-fill/fill-dark.png"
if [[ "$THEMES" == All ]]; then
    cp "$CAPTURE_TEMP/fill/fill-light.png" "$ROOT/docs/images/pen-fill/fill-light.png"
fi
java -cp "$CAPTURE_TEMP:$JAR" org.trostheide.gantry.app.gui.CaptureFillScreenshots "$ROOT" "$ROOT/docs/images/pen-fill"

# Exact stored-job identity and actionable persistence feedback.
javac -cp "$JAR" -d "$CAPTURE_TEMP" scripts/gui/GuiAcceptance.java scripts/gui/WorkflowIssueAcceptance.java
java -Dsun.java2d.uiScale=1 -cp "$CAPTURE_TEMP:$JAR" WorkflowIssueAcceptance "$CAPTURE_TEMP/workflows"
cp "$CAPTURE_TEMP/workflows/historical-review.png" "$ROOT/docs/images/workspace/historical-review.png"
cp "$CAPTURE_TEMP/workflows/saved-with-error-details.png" "$ROOT/docs/images/workspace/save-feedback.png"

# Post-RC11 editable artwork and advisory overlap, through the actual Swing UI.
javac -cp "$JAR" -d "$CAPTURE_TEMP" scripts/gui/GuiAcceptance.java scripts/gui/PreparationIntegrationAcceptance.java
java -Dsun.java2d.uiScale=1 -cp "$CAPTURE_TEMP:$JAR" PreparationIntegrationAcceptance "$CAPTURE_TEMP/preparation"
for name in quick-artwork-dialog quick-artwork ink-overlap; do
    cp "$CAPTURE_TEMP/preparation/$name.png" "$ROOT/docs/images/workspace/$name.png"
done

# Current user-facing firmware, anchor and restart views. These are fresh captures;
# the dated validation evidence remains unchanged. Both phases use isolated profiles
# and the in-memory serial simulator, never a physical serial port.
javac -cp "$JAR" -d "$CAPTURE_TEMP" scripts/gui/GuiAcceptance.java scripts/gui/SerialSimulatorAcceptance.java scripts/gui/RoadmapAcceptance.java
for phase in seed recover; do
    timeout 150s java -Dsun.java2d.uiScale=1 -cp "$CAPTURE_TEMP:$JAR" RoadmapAcceptance "$ROOT" "$CAPTURE_TEMP/roadmap" "$phase"
done
for pair in \
    anchor:placement-reference \
    anchor-compact:placement-reference-compact \
    firmware:firmware-review \
    calibration-review:calibration-firmware-review \
    uncertain-reverse:partial-restart-uncertain \
    reviewed-forward:partial-restart-reviewed \
    partial-complete:partial-restart-complete; do
    cp "$CAPTURE_TEMP/roadmap/evidence/${pair%%:*}.png" "$ROOT/docs/images/workspace/${pair#*:}.png"
done
