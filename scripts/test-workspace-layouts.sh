#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
JAR="${1:-app/target/app-1.0.0.jar}"
RESULTS="${2:-dist/validation/workspace}"
LAYOUT_CLASSES="$(mktemp -d -t gantry-layout-classes-XXXXXX)"
trap 'rm -rf "$LAYOUT_CLASSES"' EXIT
javac -cp "$JAR" -d "$LAYOUT_CLASSES" scripts/gui/GuiAcceptance.java scripts/gui/WorkspaceLayouts.java
for theme in Light Dark; do
    for scale in 1 1.25 1.5 2; do
        timeout 90s java -Dsun.java2d.uiScale="$scale" -cp "$LAYOUT_CLASSES:$JAR" WorkspaceLayouts \
            "$PWD" "$RESULTS/$theme-$scale" "$theme"
    done
done
