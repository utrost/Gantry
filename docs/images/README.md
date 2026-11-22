# Gantry screenshots — current development

All **40 dark-mode PNGs** were regenerated on 2026-11-22, including seven new
firmware/reference, placement and partial-restart views. The gallery now contains
54 images; the 14 Light examples retain their previous captures. Current guides
use Dark examples. The [capture manifest](capture-manifest.json) records each Dark
image's dimensions and SHA-256, the source baseline and capture time.

Capture JAR: `1.0.0-rc.14`, SHA-256 `c797e337739f950208fd0ba1b495b3c7d42dc219f50c2b91432ebb6f4c871c85`.
Source baseline: `4880da3161e1a4777559560a5db14b249883afb7` (documentation updates after RC14; application
implementation unchanged). JDK 21 / Linux / Xvfb, requested/effective scale 1.
Screenshots render actual Swing components with isolated lightweight-mock or
serial-simulator profiles. No physical plotter was connected. Root-pane captures
exclude native borders. Simulated limits and coordinate ranges are examples;
verify the actual values for your controller.

The [60-second mock walkthrough](../demos/Gantry-first-plot-mock.mp4) retains its
previous RC14 capture. This refresh updates still images. Published RC14 assets
and dated validation evidence remain unchanged; new offline-help images are
included when building the next documentation ZIP or installer.

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

## Firmware, placement and partial restart

- [Center reference retained through resize/rotation](workspace/placement-reference.png)
- [Compact 1024 × 768 workspace](workspace/placement-reference-compact.png)
- [Read-only firmware and work-origin review](workspace/firmware-review.png)
- [Review reachable inside calibration](workspace/calibration-firmware-review.png)
- [Uncertain reverse proposal after mid-stroke Stop](workspace/partial-restart-uncertain.png)
- [Forward selection and three explicit confirmations after reopening](workspace/partial-restart-reviewed.png)
- [Completed retained strokes after Start and pen confirmation](workspace/partial-restart-complete.png)

These are fresh serial-simulator captures. The restart examples run in two
separate application processes; saved uncertainty and selected strokes are real
application states. Controller simulation does not prove ink, paper registration,
endstops or native fractional scaling.

## Reproduce

With a built application, Java and a graphical display (or Xvfb):

```bash
./scripts/capture-screenshots.sh app/target/app-1.0.0.jar Dark
```

`Dark` refreshes the 40 dark-mode PNGs and preserves Light images. Omit the second
argument, or use `All`, to regenerate both themes. The script includes the
firmware/anchor/restart views and uses temporary profiles without physical ports.
It performs the real simulated Stop and separate-process recovery before copying
the seven feature images. Review the full-size images and update capture provenance
when publishing a refreshed gallery.

The separate `./scripts/record-demo.sh dist/demo` script regenerates the video and
requires ffmpeg. The scale/density acceptance matrix is also separate:
`./scripts/test-workspace-layouts.sh` checks both themes, both densities and requested
100%, 125%, 150%, 200% scales. Historical dated audit captures retain their original
[2026-11-22 provenance](../validation/firmware-anchors-restart/README.md).
