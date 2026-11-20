# Gantry screenshots — RC12 preparation

All 47 PNGs were captured locally on 2026-11-20 from the versioned RC12 JAR
(implementation baseline `b972370`, JDK 21 / Linux / Xvfb, requested/effective
scale 1). They render actual Swing components using isolated mock profiles.
Dark is the default; Light remains available. Canvas appearance is independent.
No physical plotter was connected. Root-pane captures exclude native borders.

The [one-minute mock walkthrough](../demos/Gantry-first-plot-mock.mp4) was also
refreshed from this build. It contains six captioned application states and no
hardware motion. The final capture waits for the visible terminal state rather
than capturing an earlier backend/history-complete moment. These captures do not establish native desktop or user acceptance.

New preparation views: [text dialog](workspace/quick-artwork-dialog.png),
[workspace/export feedback](workspace/quick-artwork.png) and
[advisory overlap](workspace/ink-overlap.png). **Visual review found reversed
text and inconsistent view orientation**, tracked in
[#93](https://github.com/utrost/Gantry/issues/93). These three images are current
defect evidence, not accepted examples of readable output. The workspace also
retains an intentionally triggered empty-selection error alongside successful
export feedback. See the [candidate record](../release-results/1.0.0-rc.12.md).

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
