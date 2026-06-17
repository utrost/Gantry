# Gantry — Testing & Acceptance Guide

This document describes how to verify that all Gantry features work correctly,
covering both the automated test suite and manual acceptance checks.

---

## 1. Automated test suite

Run the full build from the repo root:

```bash
mvn clean install
```

Expected output: `BUILD SUCCESS` with zero failures across all modules.

### Test coverage by module

| Module | Test classes | What is covered |
|---|---|---|
| `model` | `ProcessorOutputJsonTest`, `CoordinateTransformTest` | JSON round-trip of `ProcessorOutput`; coordinate transform (rotate/swap/invert/align) in all axis combinations |
| `pipeline-core` | `SvgImportStageTest` (4 nested classes, ~11 tests) | SVG parsing and command-model extraction: simple SVG, layered SVG, fit-to-A4 scaling, refill insertion, refill-free mode, command ID sequence, transform/nested-transform baking, mirroring; `PaperFormat` parsing; `calculateFitToPageTransform` |
| `pipeline-core` | `OptimizeStageTest`, `MultipassStageTest` | RDP simplify, greedy-NN reorder, multipass command expansion |
| `plotter` | `GcodeBackendTest` | G-code formatting: init sequence, pen modes (servo/zaxis/m3m5), moveto, lineto, raw send |
| `app` | `PlotServiceTest` | Full plot orchestration: layer sequencing, refill at layer boundary, cancel mid-plot, OOB clamping, per-waypoint position callbacks |
| `svgtoolbox-core` | `ConfigBuilderTest` | Config builder defaults and overrides |
| `svgtoolbox-core` | `VisibilityProcessorTest` (4) | Remove hidden layers by colour |
| `svgtoolbox-core` | `StyleNormalizerProcessorTest` (2) | Move inline `style` attributes to presentation attributes |
| `svgtoolbox-core` | `RotateProcessorTest` (4) | Canvas rotation 45/90/180° |
| `svgtoolbox-core` | `StrokeWidthProcessorTest` (1) | Force stroke width on all elements |
| `svgtoolbox-core` | `PaletteProcessorTest` (5) | CIELAB quantisation: nearest colour, passthrough when no palette |
| `svgtoolbox-core` | `CropProcessorTest` (5) | Clip outside crop bounds; keep within; null bounds no-op |
| `svgtoolbox-core` | `LayerProcessorTest` (4) | Inkscape layer groups; resize canvas; multi-colour grouping |
| `svgtoolbox-core` | `HatchProcessorTest` (4) | Linear/cross/zigzag/wave/dot patterns; area filter; no-hatch list |
| `svgtoolbox-core` | `SimplifyProcessorTest` (2) | RDP on collinear points; tolerance threshold |
| `svgtoolbox-core` | `LinesimplifyProcessorTest` (6) | RDP on path `d` attribute; tolerance; skip closed paths |
| `svgtoolbox-core` | `LinemergeProcessorTest` (6) | Merge adjacent open paths within tolerance; reverse direction |
| `svgtoolbox-core` | `LinesortProcessorTest` (7) | Greedy NN sort; 2-opt; single shape no-op |
| `svgtoolbox-core` | `ReloopProcessorTest` (5) | Rotate closed-path start point to nearest vertex |
| `svgtoolbox-core` | `PathOptimizeProcessorTest` (3) | Group-level reorder; single shape no-op; rect elements |
| `svgtoolbox-core` | `SvgToolboxPipelineTest` (4) | Pipeline order (13 processors); conditional PathOptimize; integration: red line → Inkscape layer; progress callback count |

---

## 2. Manual acceptance checklist

Run through this checklist after a build or before a release. Use `--mock-backend`
mode (Settings → Mock backend checkbox) when a plotter is not available.

### 2.1 Build and launch

- [ ] `mvn clean install` completes with `BUILD SUCCESS` and zero test failures.
- [ ] `./scripts/start.sh` (or `start.cmd`) launches the Gantry GUI.
- [ ] GUI shows: visualisation panel (dark background, bed outline, axis labels), right-side controls, console at the bottom.

### 2.2 Settings

- [ ] Open Settings. Verify all fields are present (serial port, baud, machine width/height, origin, orientation, alignment, data rotation, pen mode, feed rates, station table).
- [ ] Change machine width to 400 and height to 300. Save. Reopen — values are persisted.
- [ ] Enable Mock backend. Verify Connect button no longer requires a serial port.

### 2.3 SVG import — basic

1. Click **Import SVG…**, choose any SVG file.
2. Import tab: set Max draw distance = 0 (no refill). Click **Import**.
   - [ ] Drawing appears in the visualisation panel.
   - [ ] Console shows: `Imported <filename>: N layer(s), M command(s)`.

3. Import with Max draw distance = 50 mm.
   - [ ] Console count of commands is higher (refill commands added).

4. Import with Fit to = A4, Padding = 10.
   - [ ] Bounding box in the canvas stays within the bed area.

5. Import with Mirror enabled.
   - [ ] Drawing is flipped horizontally.

### 2.4 SVG import — layered SVG

Use an SVG with two Inkscape layers (`inkscape:groupmode="layer"`).

- [ ] Console reports `2 layer(s)`.
- [ ] Visualisation shows two separate colour groups.

### 2.5 SVGToolBox pre-processing

1. Click **Import SVG…**, select the **Process SVG** tab.
2. Check **Run SVGToolBox processing**.
3. Set Hatch to enabled, pattern = cross, angle = 45, gap = 8. Click **Import**.
   - [ ] Hatching is visible in the visualisation.
4. Enable **Linesort** + **Reloop**. Import.
   - [ ] No error; console shows processing output lines (e.g. "Linesort: path order optimized").
5. Enable **Print statistics**.
   - [ ] Console shows `--- Statistics ---`, element count, total length in metres.

### 2.6 Canvas controls

- [ ] **Drag** the drawing — it moves.
- [ ] **Drag a corner handle** — drawing scales uniformly.
- [ ] **Rotate 90°** — drawing rotates 90°.
- [ ] **Mirror** — drawing flips horizontally.
- [ ] **Reset Position** — drawing snaps back to canvas-alignment position.
- [ ] Canvas HUD (top-left overlay) updates to show current position/scale/rotation.

### 2.7 Optimise loaded commands

- [ ] Import an SVG. Click **Optimize Loaded Commands**.
  - [ ] Console confirms optimisation ran; command count may decrease slightly.
- [ ] Set Simplify tolerance = 1.0, Reorder strokes = enabled. Optimise again.
  - [ ] Console confirms; command count is ≤ before.

### 2.8 Jog controls

*(Requires Connect with Mock backend enabled)*

- [ ] Click **Connect**. Status label shows "Connected".
- [ ] Press ▲ — console shows a `G1` move command.
- [ ] Change step to 1 mm. Press ◄ — move is 1 mm.
- [ ] **Pen Down** — console shows pen-down G-code. **Pen Up** — pen-up G-code.
- [ ] Type a raw G-code command (e.g. `?`) in the raw field and press Enter — response appears in console.
- [ ] Click **Home (limit switches)** — a confirmation dialog appears. Cancel — nothing happens.
- [ ] Click **Home** again, confirm — console logs "Homing..." then "Homed. Origin zeroed at (0, 0)." (mock backend simulates the cycle instantly; on real hardware this runs GRBL's `$H`).

### 2.9 Plotting (mock backend)

1. Import an SVG with Max draw distance = 0.
2. Connect (mock). Click **Start Plot**.
   - [ ] Console shows layer start messages and draw commands streaming.
   - [ ] Cursor dot in visualisation moves along the drawing path in real time.
   - [ ] Plot completes; console shows completion message.

3. Import with Max draw distance = 50 (adds refill commands). Plot.
   - [ ] Plotter pauses at each REFILL. Console shows `--- Refilling at <station> ---`.
   - [ ] **Confirm Layer** resumes plotting.

4. Start a plot and click **Stop**.
   - [ ] Plot stops within one command.

5. Set **Passes** = 3. Plot.
   - [ ] Each stroke is drawn 3 times (command count is tripled vs passes = 1).

### 2.10 G-code export and replay

- [ ] Import an SVG. Click **Export G-code** — save as `test.gcode`.
  - [ ] File is created and contains `G21`, `G90`, pen-mode commands, `G0`/`G1` moves.
- [ ] Click **Replay G-code**, select `test.gcode` (mock backend).
  - [ ] Console streams G-code lines; cursor moves in visualisation.

### 2.11 Save and load command JSON

- [ ] Import an SVG. Click **Save Commands** — save as `test.json`.
- [ ] Click **Load Commands**, select `test.json`.
  - [ ] Drawing reappears in visualisation with the same layer and command count.

### 2.12 CLI headless conversion

```bash
java -jar cli/target/cli-1.0-SNAPSHOT.jar \
  -i path/to/drawing.svg \
  -o /tmp/out.json \
  --fit-to A4 \
  --max-dist 500
```

- [ ] Command exits 0.
- [ ] Console: `Wrote N layer(s), M command(s) to /tmp/out.json`.
- [ ] `out.json` is valid JSON loadable by the GUI (Load Commands).

```bash
java -jar cli/target/cli-1.0-SNAPSHOT.jar \
  -i path/to/drawing.svg \
  -o /tmp/out2.json \
  --toolbox --linesort --reloop --toolbox-stats
```

- [ ] Console shows SVGToolBox output lines.
- [ ] Statistics block (`--- Statistics ---`) appears.
- [ ] `out2.json` is valid and loadable.

---

## 3. Running legacy projects (reference)

The legacy projects build independently and remain unchanged:

```bash
mvn -f legacy/SVG2WaterColor/pom.xml clean package
mvn -f legacy/SVGToolBox/pom.xml clean package
```

Both should produce `BUILD SUCCESS`. They are kept as a reference oracle and
are not modified until Phase 7 (cutover).

---

## 4. What is not yet automated

The following are covered by manual checks above but do not have automated unit tests:

- GUI rendering and interactive canvas controls (Swing, not unit-testable without a display).
- Live serial communication with the plotter.
- G-code export file content correctness beyond basic format (see `GcodeBackendTest` for format coverage).
- Per-color hatch-override `--style` flag in CLI.

These are candidates for integration tests or manual regression checks before each release.
