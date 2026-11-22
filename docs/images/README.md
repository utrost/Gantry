# Gantry screenshots — current development

All 47 PNGs were regenerated on 2026-11-21 from the
`fix/prepared-preview-and-estimates` development JAR (based on `c9b1d38`),
JDK 21 / Linux / Xvfb, requested/effective scale 1. They render actual Swing
components with isolated mock profiles. Dark is the default; Light remains
available. No physical plotter was connected. Root-pane captures exclude native
borders. Current source screenshots do not describe the preserved public RC11
payload or immutable RC12 draft.

The [60-second mock walkthrough](../demos/Gantry-first-plot-mock.mp4) was regenerated
from the same JAR: six captioned application states, including the visible terminal
state. It demonstrates the application workflow and no hardware motion.

[Text dialog](workspace/quick-artwork-dialog.png),
[workspace/export feedback](workspace/quick-artwork.png) and
[overlap](workspace/ink-overlap.png) now show complete upright text. The workflow
intentionally triggers an empty-selection error, retained beside later successful
export feedback. Original RC12 failures remain available through the
[immutable candidate evidence](../release-results/1.0.0-rc.12.md) and
[orientation audit](../validation/orientation/README.md). [Current verification](../release-results/2026-11-21-prepared-route.md)
separates these software results from physical/native/user acceptance.

## Daily workspace

![Workspace with three layers](workspace-layer-preview.png)

- [Empty](workspace/empty.png), [dismissed hint](workspace/empty-dismissed.png)
- [Prepared](workspace/prepared.png), [selected artwork](workspace/selected-artwork.png)
- [Machine controls](workspace/machine.png), [disabled movement](workspace/disabled-machine.png)
- [Check](workspace/check.png), [pen change](workspace/pen-change.png), [running](workspace/running.png)
- [Stopped](workspace/stopped.png), [failure presentation](workspace/failure.png)
- [Larger workspace](workspace/large-workspace.png)
- [Light prepared](workspace/light-prepared.png), [Light check](workspace/light-check.png)
- [SVG import](workspace/svg-import.png), [raster import](workspace/raster-import.png)
- [Stored-job review](workspace/historical-review.png), [saved result with retained error](workspace/save-feedback.png)

The failure state is injected to verify presentation. Mock job captures do not
establish physical motion completion or real alarm recovery.

## Fill preparation

![Pen-aware fill preview](pen-fill/fill-dark.png)

- [Light fill preview](pen-fill/fill-light.png)
- [Processing with coverage](pen-fill/processing.png)
- [Calibration artwork](pen-fill/calibration.png)

Coverage uses a round-tip geometric approximation. Ink, pressure, speed and paper
can change the physical result. Calibration artwork remains subject to preflight.

## Setup and settings

- [Welcome](first-run-guided-practice.png)
- [Layer pen/watercolor properties](layer-watercolor-settings.png)
- [Connection](settings-connection.png)
- [Application](settings-application.png)
- [Machine geometry](settings-machine-geometry.png)
- [Pen and speeds](settings-pen-speeds.png)
- [Watercolor stations](settings-watercolor-stations.png)

## Reproduce

With Java and a graphical display (or Xvfb):

```bash
mvn package
./scripts/capture-screenshots.sh app/target/app-1.0.0.jar
./scripts/record-demo.sh dist/demo
```

The capture script refreshes the screenshot set. The demo script requires
ffmpeg and produces the captioned one-minute mock video. Both use temporary
profiles and application rendering rather than desktop capture. Inspect generated
images before committing. The full scale/density acceptance matrix is separate:
`./scripts/test-workspace-layouts.sh` exercises both themes, both densities and
100%, 125%, 150%, 200% scales. Historical dated audit images are evidence of older
revisions and are not presented as current screenshots.

The dated post-RC13 firmware/reference, placement-anchor and partial-restart
captures are kept separately with their own [2026-11-22 provenance](../validation/firmware-anchors-restart/README.md).
The 47-image gallery above predates those additions and is retained as dated evidence.
