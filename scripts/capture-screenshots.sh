#!/usr/bin/env bash
# Capture the real GUI in a temporary mock profile. Requires a visible desktop or Xvfb.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$PWD"
JAR="${1:-app/target/app-1.0.0.jar}"
if [[ ! -f "$JAR" ]]; then
    echo "Build a GUI JAR first, or pass its path as the first argument." >&2
    exit 1
fi
CAPTURE_TEMP="$(mktemp -d -t gantry-screenshots-XXXXXX)"
trap 'rm -rf "$CAPTURE_TEMP"' EXIT
javac -cp "$JAR" -d "$CAPTURE_TEMP" scripts/screenshots/CaptureScreenshots.java
java -Dsun.java2d.uiScale=1 -cp "$CAPTURE_TEMP:$JAR" CaptureScreenshots "$ROOT" "$ROOT/docs/images" "$CAPTURE_TEMP/profile"

# Workspace states in both themes, import dialogs and physical fill tools.
javac -cp "$JAR" -d "$CAPTURE_TEMP" scripts/gui/GuiAcceptance.java scripts/gui/WorkspaceLayouts.java scripts/gui/ImportLayouts.java scripts/gui/PenFillAcceptance.java scripts/screenshots/CaptureFillScreenshots.java
for theme in Dark Light; do
    java -Dsun.java2d.uiScale=1 -cp "$CAPTURE_TEMP:$JAR" WorkspaceLayouts "$ROOT" "$CAPTURE_TEMP/$theme" "$theme" Comfortable
    for shot in "$CAPTURE_TEMP/$theme/"*.png; do
        name="$(basename "$shot")"
        if [[ "$theme" == Light ]]; then name="light-$name"; fi
        cp "$shot" "$ROOT/docs/images/workspace/$name"
    done
done
cp "$ROOT/docs/images/workspace/prepared.png" "$ROOT/docs/images/workspace/dark-prepared.png"
cp "$ROOT/docs/images/workspace/machine.png" "$ROOT/docs/images/workspace/disabled-machine.png"
cp "$ROOT/docs/images/workspace/light-machine.png" "$ROOT/docs/images/workspace/light-disabled-machine.png"
java -cp "$CAPTURE_TEMP:$JAR" ImportLayouts "$ROOT" "$ROOT/docs/images/workspace"
java -cp "$CAPTURE_TEMP:$JAR" org.trostheide.gantry.app.gui.PenFillAcceptance "$ROOT/docs/images/pen-fill"
java -cp "$CAPTURE_TEMP:$JAR" org.trostheide.gantry.app.gui.CaptureFillScreenshots "$ROOT" "$ROOT/docs/images/pen-fill"
