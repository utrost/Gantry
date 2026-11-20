# Default-origin orientation finding — RC12 preparation

**FAIL**, tracked in [#93](https://github.com/utrost/Gantry/issues/93).
Implementation baseline `b972370ba8708b0c912395cb912317edea3b69a4`.

The [actual exported SVG](default-origin-label.svg) contains “Max Mustermann”,
created as a 10 mm single-stroke label with the default Top-Right machine origin,
no extra axis inversions/swap/Flip Y, 210 × 148 mm bed. It is mirrored in an
ordinary SVG viewer. Compare [workspace](../../images/workspace/quick-artwork.png)
and [overlap](../../images/workspace/ink-overlap.png): the workspace is vertically
reflected while the heatmap is horizontally reflected. Physical-page placement
also differs. These are failed observations, not accepted user output.

Reproduce after building the GUI JAR, with a graphical display or Xvfb:

```bash
javac -cp app/target/app-1.0.0.jar -d /tmp/gantry-orientation-classes \
  scripts/gui/GuiAcceptance.java scripts/gui/PreparationIntegrationAcceptance.java
java -Dsun.java2d.uiScale=1 \
  -cp /tmp/gantry-orientation-classes:app/target/app-1.0.0.jar \
  PreparationIntegrationAcceptance /tmp/gantry-orientation-results
```

Use a new output directory. The harness creates an isolated simulation profile,
never connects hardware, and emits the editable project, SVG, ZIP and three PNGs.
Its PASS message concerns workflow/metadata assertions, **not readable orientation**.
Open the SVG independently and inspect all views before interpreting a pass.

Source analysis and compatibility requirements are in #93. Do not fix this by
silently changing calibrated-profile inversions. Any executable mapping change
needs historical-job review and fresh direction/frame/plot acceptance.
