# Gantry — Testing & Acceptance Guide

This document describes how to verify that all Gantry features work correctly,
covering both the automated test suite and manual acceptance checks.

> **Recording results:** copy [`TEST_RESULTS_TEMPLATE.md`](TEST_RESULTS_TEMPLATE.md)
> per run and fill in PASS/FAIL/BLOCKED/N-A per script ID.
>
> **Test data:** the input SVGs every script references live in
> [`../testdata/`](../testdata/) (already in the repo — no need to hand-author
> them). See `testdata/README.md` for what each file is for.

### Known limitations / out of scope (don't file these as bugs)

- **Hardware-marked scripts on the mock backend are smoke tests**, not proof of
  real motion/serial behaviour — repeat them on a real plotter before a hardware
  release.
- **CLI per-colour hatch override (`--style`)** is not wired up; only the GUI
  Process-SVG tab exposes per-colour hatch styling.
- **No undo/redo** for canvas edits (drawing moves, station placement) — changes
  apply immediately; revert by re-importing or editing Settings.
- The **mock backend emulates** `$$`/`$100`/`$101`, pen and motion commands well
  enough for flow testing, but does not model real timing, acceleration, or
  limit-switch behaviour.

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
| `pipeline-core` | `SvgImportStageTest` (4 nested classes, 28 tests) | SVG parsing and command-model extraction: simple SVG, layered SVG, fit-to-A4 scaling, refill insertion, refill-free mode, command ID sequence, transform/nested-transform baking, mirroring; full-page background rect dropped when real content coexists; single-shape SVG (the rect *is* the content) is never dropped; `PaperFormat` parsing; `calculateFitToPageTransform`; plain (non-Inkscape) top-level `<g>` groups are split into separate layers when ≥2 contain drawables, but a lone group still collapses to one "Default" layer |
| `pipeline-core` | `OptimizeStageTest` (7), `MultipassStageTest` | RDP simplify, greedy-NN reorder, multipass command expansion; stroke welding (touching segments merge into one polyline, reverse-when-only-end-touches, disjoint stay separate, zero tolerance disables) |
| `plotter` | `GcodeBackendTest` | G-code formatting: init sequence, pen modes (servo/zaxis/m3m5), moveto, lineto, raw send |
| `app` | `PlotServiceTest` | Full plot orchestration: layer sequencing, refill at layer boundary, cancel mid-plot, OOB clamping, per-waypoint position callbacks |
| `app` | `TimeEstimatorTest` (7) | Travel/draw distances use their respective feed rates; refill travel + fixed dip overhead; unknown station falls back to default; pen-down settle overhead charged once per `DrawCommand` and driven by the configurable `penDownDelayMillis` (0 removes it); multi-layer totals; `H:MM:SS` formatting |
| `vectorize` | `IntegrationTest`, `BoofcvBatikVectorTest`, `StrategiesTest`, `PaintByNumbersTest` (86) | Raster→SVG engine: contour extraction, all eight strategies, polyline/Bézier geometry, auto-Canny, crop, SVG optimisation, Paint-by-Numbers quantisation/regions/labels |
| `cli` | `VectorizeCliTest` (2) | `VectorizeCli` wiring: image→SVG, and the `--`-separated image→SVG→command-JSON chain (argument split, SVG-path derivation, `-i` injection) |
| `svgtoolbox-core` | `ConfigBuilderTest` | Config builder defaults and overrides |
| `svgtoolbox-core` | `VisibilityProcessorTest` (4) | Remove hidden layers by colour |
| `svgtoolbox-core` | `StyleNormalizerProcessorTest` (2) | Move inline `style` attributes to presentation attributes |
| `svgtoolbox-core` | `RotateProcessorTest` (4) | Canvas rotation 45/90/180° |
| `svgtoolbox-core` | `StrokeWidthProcessorTest` (1) | Force stroke width on all elements |
| `svgtoolbox-core` | `PaletteProcessorTest` (5) | CIELAB quantisation: nearest colour, passthrough when no palette |
| `svgtoolbox-core` | `CropProcessorTest` (5) | Clip outside crop bounds; keep within; null bounds no-op |
| `svgtoolbox-core` | `LayerProcessorTest` (5) | Inkscape layer groups; resize canvas; multi-colour grouping; pre-existing layers nested under a hatch wrapper `<g>` are preserved, not re-bucketed by colour |
| `svgtoolbox-core` | `HatchProcessorTest` (5) | Linear/cross/zigzag/wave/dot patterns; area filter; no-hatch list; explicit-vs-auto dot radius |
| `svgtoolbox-core` | `SimplifyProcessorTest` (2) | RDP on collinear points; tolerance threshold |
| `svgtoolbox-core` | `LinesimplifyProcessorTest` (6) | RDP on path `d` attribute; tolerance; skip closed paths |
| `svgtoolbox-core` | `LinemergeProcessorTest` (6) | Merge adjacent open paths within tolerance; reverse direction |
| `svgtoolbox-core` | `LinesortProcessorTest` (7) | Greedy NN sort; 2-opt; single shape no-op |
| `svgtoolbox-core` | `ReloopProcessorTest` (5) | Rotate closed-path start point to nearest vertex |
| `svgtoolbox-core` | `PathOptimizeProcessorTest` (3) | Group-level reorder; single shape no-op; rect elements |
| `svgtoolbox-core` | `SvgToolboxPipelineTest` (4) | Pipeline order (13 processors); conditional PathOptimize; integration: red line → Inkscape layer; progress callback count |

---

## 2. Manual test scripts (human-executed)

This section is a complete, step-by-step regression suite for a human tester.
Run it after a build or before a release. Each script has a stable **ID** so
results can be recorded (e.g. in a spreadsheet) and referenced in bug reports.

### How to use this suite

- **Pass/fail:** a script passes only if **every** checkbox in it is satisfied.
  Record `PASS` / `FAIL` / `BLOCKED` / `N/A` per script ID, with notes.
- **Mock vs hardware:** scripts marked *(mock OK)* can be run end-to-end with the
  **Mock backend** (Settings → Mock backend) and need no plotter. Scripts marked
  *(hardware)* exercise real serial/motion and should be repeated on a real
  plotter before a hardware release; the mock can stand in for a smoke test.
- **Order:** scripts are grouped by area. Within a release run, do the groups
  top-to-bottom — later groups assume you know how to import a drawing and connect.
- **Reset between scripts:** unless a script says otherwise, start each one from a
  freshly launched app with a known `config.json` (see TS-A1). Deleting
  `config.json` resets to first-run defaults.

### Test data to prepare first

Create or obtain these input files once; several scripts reuse them. Keep them in
a `testdata/` folder.

| File | What it must contain | Used by |
|---|---|---|
| `single-rect.svg` | A single `<rect>` (no surrounding page border) | TS-G1 |
| `simple.svg` | A handful of paths/lines, one colour, no layers | most import/plot scripts |
| `layers2.svg` | Two Inkscape layers (`inkscape:groupmode="layer"`), different stroke colours | TS-H1, TS-N1 |
| `many-segments.svg` | Many short, end-to-end touching `<line>` segments (e.g. a polyline exported as separate lines) | TS-L2 |
| `colours.svg` | Several distinct stroke colours (for colour→station mapping) | TS-N1 |
| `framed.svg` | Content plus a full-page background `<rect>` border (Inkscape page outline) | TS-G1 |

---

### Group A — Build, launch & layout

#### TS-A1 — Clean build and launch *(mock OK)*
1. From the repo root run `mvn clean install`.
   - [ ] Ends with `BUILD SUCCESS` and **zero** test failures across all modules.
2. Run `./scripts/start.sh` (or `start.cmd` on Windows).
   - [ ] The Gantry GUI window opens without an error dialog or stack trace in the terminal.
3. Inspect the window.
   - [ ] Menu bar shows **File · Edit · Machine · Settings · Help**.
   - [ ] Left: the **Live View** visualisation (dark background, bed outline, orange origin dot, red +X / green +Y axis labels).
   - [ ] Right: the control column — **Jog**, **Overlay / Position**, **Plot**, **Raw G-code**.
   - [ ] Bottom: the **Console**.
   - [ ] Every control-column section is flush-left; nothing is centered or clipped at the right edge (the **Start** and **Stop** buttons are fully visible).
   - [ ] The step-guidance banner under the toolbar uses the dark slate-blue theme colour (not yellow/cream) and is readable.

---

### Group B — First run & Setup Wizard

#### TS-B1 — First-run Setup Wizard offer *(mock OK)*
1. Quit the app. Delete (or rename) `config.json` in the working directory.
2. Launch the app.
   - [ ] A **"Welcome to Gantry"** dialog appears stating it looks like the first run and offering to run the guided Machine Setup wizard.
3. Click **No**.
   - [ ] The dialog closes and the main window is usable; no wizard opens.
4. Quit, delete `config.json` again, relaunch, and this time click **Yes**.
   - [ ] The **Machine Setup** wizard opens (continue into TS-B2).
5. Quit and relaunch **without** deleting `config.json`.
   - [ ] No welcome dialog appears on subsequent runs (offer is first-run only).

#### TS-B2 — Machine Setup Wizard happy path *(mock OK)*
1. Open **Machine > Setup Wizard...** (or continue from TS-B1).
   - [ ] Step 1 **Welcome** shows an intro that wraps cleanly (no horizontal scrollbar).
2. Click **Next**.
   - [ ] Step 2 **Connection** shows the real connection fields (serial port, baud, **Mock backend**) — the same widgets as Settings.
   - [ ] Tick **Mock backend**.
3. **Next** → Step 3 **Geometry & origin**.
   - [ ] Shows machine width/height, origin corner, orientation, alignment, data rotation, padding, the invert/swap overrides.
   - [ ] Set width = 250, height = 180, origin = Bottom-Left.
4. **Next** → Step 4 **Pen & speeds**.
   - [ ] Shows pen mode, pen up/down, Z up/down, feed rates, Pen Down Delay.
5. **Next** → Step 5 **Done**, then **Finish**.
   - [ ] Console logs `Machine setup saved and applied.`
   - [ ] The Live View bed outline updates to the new 250×180 geometry with the origin in the bottom-left.
6. Open **Settings > Preferences...**.
   - [ ] The values entered in the wizard are present (proving the wizard and Settings share one config and persisted to `config.json`).
7. Re-open the wizard and click **Cancel** on any step.
   - [ ] No config change is logged or persisted (Cancel discards).

#### TS-B3 — Launching the wizard from Settings *(mock OK)*
1. Open **Settings > Preferences...**.
   - [ ] A **Run Setup Wizard...** button is shown at the top of the dialog.
2. Click **Run Setup Wizard...**.
   - [ ] The Settings dialog closes and the **Machine Setup** wizard opens (no "two dialogs fighting to save" — only the wizard is now live).
3. Cancel the wizard.
   - [ ] Returns to the main window with no change.

---

### Group C — Settings & configuration

#### TS-C1 — Settings fields present and persisted *(mock OK)*
1. Open **Settings > Preferences...**.
   - [ ] All fields present: serial port, baud, machine width/height, origin, orientation, alignment, data rotation, padding X/Y, invert/swap/flip overrides, pen mode, pen up/down, Z up/down, draw/travel feed rates, **Pen Down Delay (ms)**, **Run Pre-Plot Checklist before Start**, and the refill-station table.
2. Change machine width to 400 and height to 300; click OK/Save.
3. Quit and relaunch; reopen Settings.
   - [ ] Width = 400, height = 300 persisted.
4. Set **Pen Down Delay (ms)** = 0, Save, reopen.
   - [ ] Persisted at 0. (Hardware: lines start clean with no ink dot.)

#### TS-C2 — Mock backend toggle *(mock OK)*
1. In Settings, tick **Mock backend**, Save.
   - [ ] **Connect** no longer requires a serial port; clicking it connects to the simulated backend.
2. Untick Mock backend, Save (leave disconnected).
   - [ ] Connect now expects a real serial port (don't connect unless hardware present).

#### TS-C3 — Refill-station table *(mock OK)*
1. In Settings, locate the **Refill stations** table.
   - [ ] Columns: Name, X, Y, Z Down, Behavior (`simple_dip`/`dip_swirl`/`rinse`), Color, Dwell (ms), Swirl (mm).
2. Add a station named `Red` at X=10, Y=10, Behavior `simple_dip`, Color `#FF0000`; Save and reopen.
   - [ ] The station persists with all field values.

---

### Group D — Machine menu & connection

#### TS-D1 — Machine menu contents *(mock OK)*
1. Open the **Machine** menu.
   - [ ] Items present: **Connect**, **Home**, then (after a separator) **Pre-Plot Checklist...**, **Setup Wizard...**, **Calibrate Axes...**, **Test Color Stations...**.
   - [ ] Hovering each shows a descriptive tooltip.

#### TS-D2 — Connect/Disconnect label toggle *(mock OK)*
1. Ensure Mock backend is on and disconnected.
2. Click the main **Connect** button (or **Machine > Connect**).
   - [ ] Status label shows "Connected".
   - [ ] Both the toolbar button **and** the **Machine > Connect** menu item now read **Disconnect**.
   - [ ] The Jog pad and other connection-required controls become enabled.
3. Click **Disconnect**.
   - [ ] Both the button and menu item revert to **Connect**; connection-required controls disable again.

#### TS-D3 — Home from the Machine menu *(hardware; mock OK for flow)*
1. Connect (mock). Choose **Machine > Home** (or **Home (limit switches)** in the Jog section).
   - [ ] A confirmation dialog appears (the head will move). Click **Cancel** — nothing happens.
2. Trigger **Home** again and confirm.
   - [ ] Console logs the homing cycle and origin-zeroed message (mock simulates instantly; hardware runs GRBL `$H`).
   - [ ] Home is disabled while a plot is running.

---

### Group E — Calibrate Axes wizard

#### TS-E1 — Guard when disconnected *(mock OK)*
1. Ensure you are **disconnected**. Choose **Machine > Calibrate Axes...**.
   - [ ] A dialog explains you must connect first (calibration drives the machine and reads/writes GRBL settings); the wizard does **not** open.

#### TS-E2 — Direction check *(hardware; mock OK for flow)*
1. Connect (mock). Open **Machine > Calibrate Axes...**.
   - [ ] Step 1 **Intro** explains the two phases (direction, then scale) and warns to clear the bed / raise the pen.
2. **Next** → **Direction check**.
   - [ ] Two rows: **Jog +X** and **Jog +Y**, each with a "Moved the wrong way (flip X/Y)" checkbox.
   - [ ] Each checkbox is pre-ticked to the current invertX/invertY config value.
3. Click **Jog +X**.
   - [ ] Console shows a move; on hardware the head moves to the right as the on-screen +X arrow points. Tick "wrong way" if it didn't.
4. Repeat for **Jog +Y** (head should move up / +Y).

#### TS-E3 — Scale calibration read/compute/write *(hardware; mock OK)*
*(The mock backend emulates GRBL `$$`, `$100=`, `$101=`, so this runs end-to-end on mock.)*
1. Continue to step **X-axis scale**.
   - [ ] On entry, **Current steps/mm** populates from the machine's `$100` (mock reports `80.000`).
2. Set **Commanded (mm)** = 100, click **Move X by commanded distance**.
   - [ ] Console logs the commanded 100 mm X move.
3. Set **Measured (mm)** = 95, click **Compute corrected steps/mm**.
   - [ ] Preview reads approximately `Corrected: 84.211 steps/mm (was 80.000)` (= 80 × 100 / 95).
4. Click **Write $100 to the machine**.
   - [ ] Console logs the `$100=84.211` write; **Current steps/mm** updates to the corrected value.
5. **Next** → **Y-axis scale**.
   - [ ] Current steps/mm reads `80.000` from `$101` (independent of the X write).
   - [ ] This step can be **skipped** (it's optional) — Skip/Next without writing is allowed.
6. **Next** → **Done** → **Finish**.
   - [ ] Console logs `Axis calibration finished.`
7. Re-open the wizard's X-axis step.
   - [ ] Current steps/mm now reads the corrected value (the write persisted to the controller).

#### TS-E4 — Direction flip persists *(mock OK)*
1. In **Calibrate Axes** → Direction check, tick "Moved the wrong way" for X, then complete the wizard with **Finish**.
2. Open **Settings**.
   - [ ] **Extra Invert X** is now ticked (the flip was applied and saved).

---

### Group F — Pre-Plot Checklist / Pre-flight

#### TS-F1 — Guard with nothing loaded *(mock OK)*
1. With no drawing loaded, click the **Pre-flight...** button (next to Start) or **Machine > Pre-Plot Checklist...**.
   - [ ] A dialog says to load or import a drawing first; no wizard opens.

#### TS-F2 — Full checklist flow *(hardware; mock OK)*
1. Import `simple.svg` (TS-G2). Connect (mock). Click **Pre-flight...**.
   - [ ] Step **Connect** shows connection status; **Next** is enabled because you're connected (if you start disconnected, a **Connect** button appears and **Next** stays disabled until it connects).
2. **Next** → **Home** (optional). Click **Home**; status updates. **Next**.
3. **Frame the job** (optional).
   - [ ] Click **Frame the job** — console logs `Framed job bounds: (minX, minY) to (maxX, maxY)`; the head traces a pen-up rectangle. Can be repeated.
4. **Next** → **Physical checklist**.
   - [ ] Four checkboxes (correct pen for the configured pen mode, pen lifts/lowers, paper taped at the framed area, correct layers/stations). The pen-type wording matches the configured pen mode (servo/zaxis/m3m5).
   - [ ] **Next** is **disabled** until all four are ticked.
5. Tick all four. **Next** → **Confirm & start**.
   - [ ] Summary explains Finish triggers the same Start Plot action.
6. Click **Finish**.
   - [ ] The plot starts (console shows layer/draw output, exactly as a normal Start Plot).

#### TS-F3 — Frame stays inside the bed *(mock OK)*
1. Import a drawing and drag/scale it so part overhangs the bed edge.
2. Run **Pre-flight... → Frame the job** (or Frame from the wizard).
   - [ ] The logged framed bounds are **clamped to the bed** (no coordinate exceeds the machine width/height) — the trace never commands the head off the bed even though the drawing overhangs.

#### TS-F4 — Start-Plot toggle integration *(mock OK)*
1. In **Settings**, ensure **Run Pre-Plot Checklist before Start** is **ticked** (default). Save.
2. With a drawing loaded and connected, click **Start Plot**.
   - [ ] The **Pre-Plot Checklist** wizard opens instead of plotting immediately.
   - [ ] Cancelling the wizard does **not** start a plot.
3. In Settings, **untick** the toggle. Save. Click **Start Plot**.
   - [ ] Plotting begins immediately, with no wizard.

---

### Group G — SVG import (basic)

#### TS-G1 — Import button gating & page-border handling *(mock OK)*
1. Click **File > Import SVG (artwork)...**, choose `framed.svg`.
   - [ ] With **Fit to** on "-- Select size --", the **Import** button is disabled (grey).
   - [ ] Pick **Fit to = A4** — Import enables and turns **green**.
   - [ ] Pick **Fit to = Custom** with a blank size — Import disables; enter `210x297` — it re-enables/greens.
   - [ ] **Curve step** defaults to `0.1` mm.
2. Import `framed.svg` with Max draw distance = 0.
   - [ ] The full-page background border rectangle is dropped; the pen does not trace the outer frame.
3. Import `single-rect.svg`.
   - [ ] The single rect **is** drawn (a lone shape is the content and must not be discarded as a page border).

#### TS-G2 — Basic import & refill insertion *(mock OK)*
1. Import `simple.svg`, Max draw distance = 0.
   - [ ] Drawing appears in the Live View.
   - [ ] Console: `Imported simple.svg: N layer(s), M command(s)`.
2. Re-import with Max draw distance = 50 mm.
   - [ ] Command count is higher (refill commands inserted).
3. Re-import with Fit to = A4, Padding = 10.
   - [ ] The bounding box stays within the bed area.
4. Re-import with **Mirror** ticked.
   - [ ] The drawing is flipped horizontally vs the un-mirrored import.

---

### Group H — SVG import (layered)

#### TS-H1 — Layered import & layer checklist *(mock OK)*
1. Import `layers2.svg`.
   - [ ] Console reports `2 layer(s)`; the Live View shows two colour groups.
   - [ ] The **Layers** checklist shows one tickbox per layer (id + source colour), each tinted with the layer's preview colour; all start ticked.
2. Untick one layer.
   - [ ] That layer **ghosts** (dims) while ticked layers stay full colour; the drawing does not move on the bed; the `Est:` time drops to reflect only ticked layers.
3. Use **All** / **None**.
   - [ ] They tick/untick every layer at once.
4. With a subset ticked, **Start Plot** (mock) and **Export G-code**.
   - [ ] Only the ticked layers are plotted/exported. Re-ticking all restores the full preview/estimate.
5. Untick **all** layers, then Start Plot / Export.
   - [ ] Both do nothing and the console reports no layers selected.
6. Toggle **Colour layers**.
   - [ ] Ticked (default): each layer a distinct colour, dark/black layers brightened so they stay visible. Unticked: all layers one uniform colour.

---

### Group I — SVGToolBox pre-processing

#### TS-I1 — Hatch patterns *(mock OK)*
1. **Import SVG** → **Process SVG** tab → tick **Run SVGToolBox processing**.
2. Hatch enabled, pattern = cross, angle = 45, gap = 8. Import.
   - [ ] Cross-hatching is visible (not plain linear).
3. Pattern = `dot`, **Dot radius** = 3. Import.
   - [ ] Dots appear and are larger than with Dot radius = 0 (auto).
4. Pattern = `wave`, **Amplitude** = 10. Import.
   - [ ] Waves are taller than at Amplitude = 0 (auto).
   - [ ] Leaving Amplitude / Wavelength / Dot radius at 0 reproduces gap-derived defaults.

#### TS-I2 — Optimisation processors & statistics *(mock OK)*
1. Process SVG tab: enable **Linesort** + **Reloop**. Import.
   - [ ] No error; console shows processing output lines.
2. Enable **Print statistics**. Import.
   - [ ] Console shows `--- Statistics ---`, element count, total length in metres.

---

### Group J — Re-process source SVG

#### TS-J1 — Re-process loop *(mock OK)*
1. Import an SVG (so a source SVG exists). Choose **Edit > Re-process Source SVG...**.
   - [ ] A dialog with the **same option set** as the Process SVG import tab opens.
2. Change hatch settings and click **Apply**.
   - [ ] The drawing updates in place without re-importing from scratch.
   - [ ] Field values are remembered across this dialog and the import dialog (no reset).
3. Open a `.json` command file (no source SVG), then choose **Edit > Re-process Source SVG...**.
   - [ ] It reports there is no source SVG to re-process (only available after Import SVG).

---

### Group K — Canvas positioning controls

#### TS-K1 — Direct manipulation *(mock OK)*
1. Import a drawing.
   - [ ] **Drag** the drawing — it moves; the X/Y position fields update live.
   - [ ] **Drag a corner handle** — it scales uniformly.
   - [ ] **Rotate 90°** — rotates 90° clockwise.
   - [ ] **Mirror** — flips horizontally.
   - [ ] **Reset Position** — snaps back to the canvas-alignment position from Settings.
   - [ ] The HUD shows current position / scale / rotation / alignment.
2. Enter explicit **X** / **Y (mm from origin)** values and click **Set**.
   - [ ] The bounding-box corner nearest the origin moves to those coordinates.

#### TS-K2 — Live View context menu *(mock OK)*
1. **Right-click** the Live View with a drawing loaded.
   - [ ] Context menu: **Add station here**, then (after a separator) **Remove Drawing**, **Reset Position**, **Rotate 90°**, **Mirror**.
2. **Remove Drawing**.
   - [ ] Canvas clears; console logs `Removed the loaded drawing.`; a subsequent Start Plot / Export reports nothing loaded.
3. Right-click again with nothing loaded.
   - [ ] The drawing-specific items (**Remove Drawing**, **Reset Position**, **Rotate 90°**, **Mirror**) are greyed out, but **Add station here** stays enabled (it does not depend on a loaded drawing).

---

### Group L — Optimise loaded commands

#### TS-L1 — Optimise dialog *(mock OK)*
1. Import an SVG. **Edit > Optimize Commands (JSON)...**.
   - [ ] Dialog prompts for Tolerance / Reorder / Merge; OK runs it.
   - [ ] Console confirms and reports `strokes X -> Y`; command count may drop.
2. Set Tolerance = 1.0, Reorder = enabled, run again.
   - [ ] Command count is ≤ before.
3. With nothing loaded, open the menu item.
   - [ ] Shows "Open a Commands (JSON) file or Import SVG first." instead of the dialog.

#### TS-L2 — Stroke welding *(mock OK)*
1. Import `many-segments.svg`. Set **Merge = 0.2**, optimise.
   - [ ] `strokes X -> Y` shows a large drop (touching segments welded into continuous lines).
2. Fresh-import the same file, set **Merge = 0**, optimise.
   - [ ] Stroke count unchanged (welding disabled).

---

### Group M — Jog & raw G-code

#### TS-M1 — Jog & pen *(hardware; mock OK)*
1. Connect (mock).
   - [ ] Press **▲** — console shows a `G1` move.
   - [ ] Set step = 1 mm, press **◄** — move is 1 mm.
   - [ ] **Pen Down** / **Pen Up** — console shows the matching pen G-code.
   - [ ] Arrow keys / numpad 8-2-4-6 jog the same as the buttons (when no text field is focused).
   - [ ] Shift+▲ / Shift+▼ raise/lower the pen.
2. **Speed −** / **+** / **Reset**.
   - [ ] The feed-rate override percentage changes and is shown in the HUD as `Speed: N%`.

#### TS-M2 — Raw G-code field *(hardware; mock OK)*
1. Type `?` in the Raw G-code field and press Enter.
   - [ ] A response appears in the console.

---

### Group N — Watercolor / colour→station mapping

*(For placing/adjusting stations on the canvas and physically verifying their
positions with the test-run wizard, see [Group T](#group-t--visual-station-placement--test-run-wizard).)*

#### TS-N1 — Map Colors to Stations *(mock OK)*
1. In Settings, configure two stations with distinct **Color** values (e.g. `#FF0000`, `#0000FF`) and `simple_dip` behaviour. Save.
2. Import `colours.svg` (multiple stroke colours).
3. **Edit > Map Layer Colors to Stations**.
   - [ ] Each drawing colour is routed to the nearest station colour; the mapping is logged to the console.
   - [ ] Station markers in the Live View are tinted with their assigned colours.
4. (zaxis pen mode) Give a station a **Z Down** depth.
   - [ ] On a plot, that station dips to its own Z depth (servo/m3m5 fall back to the global pen-down position).

---

### Group O — Plotting

#### TS-O1 — Basic plot *(hardware; mock OK)*
1. Import `simple.svg`, Max draw distance = 0. Connect (mock). **Start Plot** (cancel the Pre-Plot wizard's gate via TS-F4 if the toggle is on, or set it off first).
   - [ ] Console shows `=== Layer ... ===` start lines and streaming draw commands prefixed with `>`.
   - [ ] The cursor dot in the Live View moves along the path in real time.
   - [ ] Plot completes with a completion message.
   - [ ] While plotting, jog/pen/edit/load/export controls are disabled; only Confirm Layer, Pause/Resume, Stop, Speed ±/Reset stay active.

#### TS-O2 — Refill / confirm-layer pauses *(hardware; mock OK)*
1. Import with Max draw distance = 50 (adds refills). Plot.
   - [ ] The plotter pauses at each layer boundary; console shows a refill/confirm message.
   - [ ] At layer end the head raises the pen and returns to origin (parked for a pen swap).
   - [ ] **Confirm Layer** resumes the next layer.

#### TS-O3 — Pause / Resume / Stop *(hardware; mock OK)*
1. Start a plot. Click **Pause**.
   - [ ] Motion halts and the pen raises; the button now reads **Resume**.
2. Click **Resume**.
   - [ ] Plotting continues from where it stopped.
3. Start again and click **Stop**.
   - [ ] The plot cancels within one command, the pen raises, and queued motion is halted (not just new commands).

#### TS-O4 — Multipass *(mock OK)*
1. Set **Passes** = 3. Plot.
   - [ ] Each stroke is drawn 3 times (command count ~tripled vs Passes = 1).

---

### Group P — Time estimate

#### TS-P1 — Pre-plot estimate *(mock OK)*
1. Import a drawing.
   - [ ] The Plot section shows `Est: M:SS` (or `H:MM:SS`).
   - [ ] Hovering shows a per-layer breakdown.
2. Lower **Pen Down Delay** in Settings and re-import.
   - [ ] The estimate shrinks (settle overhead per draw drops).
3. Press **Speed +** a few times.
   - [ ] The estimate scales with the realtime feed-rate override.

#### TS-P2 — Live tracking *(mock OK)*
1. Start a plot.
   - [ ] The label switches to `Elapsed: … / Est: …`, plus the current layer's elapsed/estimated time once it starts drawing.
   - [ ] Time spent waiting at **Confirm Layer** does not count against the layer estimate.
   - [ ] On finish/stop it reverts to the pre-plot estimate.

---

### Group Q — File output, replay & persistence

#### TS-Q1 — G-code export & replay *(mock OK)*
1. Import an SVG. **File > Export G-code (for plotter)...** → save `test.gcode`.
   - [ ] File created and contains `G21`, `G90`, pen-mode commands, `G0`/`G1` moves.
2. **File > Replay G-code...** → select `test.gcode` (mock).
   - [ ] Console streams the G-code; the cursor moves in the Live View.

#### TS-Q2 — Save/Open command JSON *(mock OK)*
1. Import an SVG. **File > Save Commands (JSON)...** → `test.json`.
2. **File > Open Commands (JSON)...** → `test.json`.
   - [ ] The drawing reappears with the same layer and command count.

#### TS-Q3 — File-chooser folder memory *(mock OK)*
1. Open/save a file from a non-default folder, then open another chooser (Import SVG, Open/Save Commands, Export/Replay G-code).
   - [ ] Each chooser reopens in the **last** folder used, including after an app restart.

---

### Group R — Help & About

#### TS-R1 — User Guide dialog *(mock OK)*
1. **Help > User Guide...**.
   - [ ] A dialog opens showing the guide as **formatted** text (headings, tables, code blocks) — not raw markdown — and does not crash.
   - [ ] The left-hand **Contents** list is populated with section names (H1–H3).
2. Click several different Contents entries (e.g. "Machine menu", "Plotting", "Troubleshooting").
   - [ ] The right pane scrolls to the matching section each time.
3. Click **Open in browser/editor**.
   - [ ] `docs/USER_GUIDE.md` opens externally (or the action is logged if no external app is available).
4. Click **Close**.
   - [ ] The dialog closes.

#### TS-R2 — About dialog *(mock OK)*
1. **Help > About Gantry...**.
   - [ ] An About dialog shows the product name and version (`1.0-SNAPSHOT`) and closes cleanly.

---

### Group S — CLI headless

#### TS-S1 — Basic conversion
```bash
java -jar cli/target/cli-1.0-SNAPSHOT.jar \
  -i testdata/simple.svg \
  -o /tmp/out.json \
  --fit-to A4 \
  --max-dist 500
```
- [ ] Exits 0.
- [ ] Console: `Wrote N layer(s), M command(s) to /tmp/out.json`.
- [ ] `out.json` loads in the GUI via **Open Commands (JSON)**.

#### TS-S2 — Toolbox pipeline + stats
```bash
java -jar cli/target/cli-1.0-SNAPSHOT.jar \
  -i testdata/simple.svg \
  -o /tmp/out2.json \
  --toolbox --linesort --reloop --toolbox-stats
```
- [ ] Console shows SVGToolBox output lines and a `--- Statistics ---` block.
- [ ] `out2.json` is valid and loadable.
- [ ] `--help` prints the full flag list.

#### TS-S3 — Vectorize an image (image → SVG → commands)
Use any PNG/JPG as `IMAGE` below.
```bash
# image -> SVG only
java -cp cli/target/cli-1.0-SNAPSHOT.jar org.trostheide.gantry.cli.VectorizeCli \
  -i IMAGE -o /tmp/v.svg -s dp --canny-auto

# image -> SVG -> command JSON in one command
java -cp cli/target/cli-1.0-SNAPSHOT.jar org.trostheide.gantry.cli.VectorizeCli \
  -i IMAGE -o /tmp/v.svg -s centerline -- -o /tmp/v.json --fit-to A4
```
- [ ] The first command writes a valid `/tmp/v.svg` (opens in a browser) and no `/tmp/v.json`.
- [ ] The second writes both; `/tmp/v.json` loads in the GUI via **Open Commands (JSON)**.
- [ ] `VectorizeCli` with no arguments prints usage; omitting `-o` before `--` errors clearly.

---

### Group T — Visual station placement & test-run wizard

These scripts cover Phase 17: placing/adjusting refill stations directly on the
Live View canvas, and the **Test Color Stations** wizard that drives the head to
each one. Canvas placement and the wizard edit the **same** `StationConfig`
backing model as the Settings → Refill stations table, so changes in one surface
appear in the other.

#### TS-T1 — Add a station by right-clicking the bed *(mock OK)*
1. Start from a known config (no stations needed). **Right-click** an empty spot on the Live View bed.
   - [ ] The context menu's **Add station here** item is enabled (even with no drawing loaded — see TS-K2).
2. Click **Add station here**.
   - [ ] A new station marker appears at (approximately) the clicked position.
   - [ ] The console logs the added station with its name and mm coordinates.
   - [ ] The new coordinates correspond to the clicked bed point under the current origin/orientation (e.g. under a Top-Right origin with inverted axes, a click in the upper-middle of the bed yields the expected signed mm — placement is in machine space, not raw pixels).
3. Open **Settings > Preferences...** → Refill stations table.
   - [ ] The station added on the canvas is present in the table with the same coordinates and sensible defaults (behaviour `simple_dip`, default dwell/swirl).
4. Quit and relaunch.
   - [ ] The added station persists (it was saved to `config.json`).

#### TS-T2 — Drag a station marker to reposition it *(mock OK)*
1. With at least one station present, **drag its marker** across the Live View.
   - [ ] The marker follows the cursor; on release the station settles at the new position.
   - [ ] The console logs the moved station's new mm coordinates.
   - [ ] Hovering a marker shows a hand/move cursor (so it's discoverable as draggable); dragging a marker takes precedence over the drawing's move/scale handles when both overlap.
2. Open Settings → Refill stations table.
   - [ ] The dragged station's X/Y match the new on-canvas position (canvas → table sync).
3. In the Settings table, change that station's X/Y to new values, Save.
   - [ ] The marker on the Live View moves to the typed position (table → canvas sync).

#### TS-T3 — Test-run wizard guards *(mock OK)*
1. **Disconnect** (or never connect). Choose **Machine > Test Color Stations...**.
   - [ ] A dialog says to connect first (the test run drives the head); the wizard does **not** open.
2. Connect (mock). Ensure there are **no** stations configured (clear the table / start from a station-free `config.json`). Choose **Machine > Test Color Stations...**.
   - [ ] A dialog says no stations are configured and points to "Add station here" / Settings; the wizard does **not** open.

#### TS-T4 — Full test-run: move / wet / nudge / persist *(hardware; mock OK)*
1. Connect (mock). Configure (or add on canvas) **two** stations — e.g. `Red` (`simple_dip`) and `Blue` (`dip_swirl`, swirl 3 mm). Choose **Machine > Test Color Stations...**.
   - [ ] Step 1 **Intro** explains Move/Wet/nudge and reminds you to mount the brush and place the pots.
2. **Next** → the first station's step.
   - [ ] The header shows the station name, its colour (or "no colour set"), and its behaviour.
   - [ ] A **Position: (X, Y) mm** label shows the station's current coordinates.
3. Click **Move here (pen up)**.
   - [ ] Console logs `--- Over station (X mm / Y mm), pen up ---`; the cursor dot moves to that station with the pen **up** (a dry visit — no dip).
4. Click **Wet test (dip)**.
   - [ ] Console logs the wet-test banner and runs the station's real dip: pen down → dwell → pen up, plus a swirl trace for `dip_swirl`/`rinse` stations (the same motion a real plot's refill uses).
5. Set the nudge **Step** spinner (e.g. 2 mm) and click **+X** once, then **+Y** once.
   - [ ] The **Position** label increases by the step in X then Y; console shows a pen-up relative move for each nudge (head moves and the stored coordinate tracks it together).
6. Click **Move here (pen up)** again.
   - [ ] The head moves to the **nudged** position (confirming nudges feed back into the move target).
7. **Next** → the second station. Click **Skip** instead of testing it.
   - [ ] The wizard advances past it without moving the head (a station step is optional).
8. **Next** → **Done** → **Finish**.
   - [ ] Console logs `Station test run finished; positions saved.`
9. Open Settings → Refill stations table (and/or reopen the wizard).
   - [ ] The first station's coordinates reflect the nudge applied in step 5; the **skipped** station is unchanged; all other fields (behaviour, colour, Z down, dwell, swirl) are preserved.
   - [ ] Quitting and relaunching keeps the corrected position (saved to `config.json`).
10. Re-run the wizard and **Cancel** on a station step after nudging.
   - [ ] No position change is saved (only **Finish** persists nudges).

---

### Group U — Image import (vectorize)

Covers Phase 18: bringing a raster image into Gantry via **File > Import Image
(vectorize)…**. The traced SVG flows through the same import as a hand-authored
SVG, so this group focuses on the vectorize step itself.

#### TS-U1 — Import an image and trace it *(mock OK)*
1. **File > Import Image (vectorize)…** (Ctrl+Shift+I) and choose a PNG/JPG (a logo or line drawing works well).
   - [ ] A **Vectorize** dialog opens with a **Strategy** dropdown and parameter fields.
2. Change the **Strategy** between e.g. *Line art (dp)*, *Centerline*, *Colour fills (ImageTracer)*, *Paint by Numbers*.
   - [ ] Only the parameters relevant to the selected strategy are enabled (e.g. Canny low/high only for the dp family, ImageTracer colours only for `bezier2`).
3. Pick *Line art (Douglas–Peucker)*, leave defaults, click **Vectorize**.
   - [ ] The **Import SVG** dialog opens next (the same one as Import SVG) — pick a **Fit to** size and import.
   - [ ] The drawing appears in the Live View; the console logs `Vectorized <image> (<strategy>): N layer(s), M command(s)`.
4. Cancel the **Vectorize** dialog (instead of clicking Vectorize) on a fresh attempt.
   - [ ] Nothing is imported and no error is shown.
5. After a successful import, confirm normal downstream behaviour.
   - [ ] Positioning, the Layers checklist, Optimize, Export G-code and Start Plot (mock) all work exactly as for an imported SVG.
   - [ ] No `edges_debug*.png` files are left in the launch directory (gitignored; see also the standalone Vectorize `--debug` behaviour).

---

## 3. Coverage map & gaps

### Feature → script index

| Feature area | Scripts |
|---|---|
| Build / launch / layout | TS-A1 |
| First run & Setup Wizard | TS-B1, TS-B2, TS-B3 |
| Settings & stations | TS-C1, TS-C2, TS-C3 |
| Machine menu & connection | TS-D1, TS-D2, TS-D3 |
| Calibrate Axes wizard | TS-E1, TS-E2, TS-E3, TS-E4 |
| Pre-Plot Checklist / Pre-flight | TS-F1, TS-F2, TS-F3, TS-F4 |
| SVG import | TS-G1, TS-G2, TS-H1 |
| Image import (vectorize) | TS-U1, TS-S3 |
| SVGToolBox & re-process | TS-I1, TS-I2, TS-J1 |
| Canvas positioning | TS-K1, TS-K2 |
| Visual station placement & test-run wizard | TS-T1, TS-T2, TS-T3, TS-T4 |
| Optimise commands | TS-L1, TS-L2 |
| Jog / raw G-code | TS-M1, TS-M2 |
| Watercolor mapping | TS-N1 |
| Plotting / passes / pause | TS-O1, TS-O2, TS-O3, TS-O4 |
| Time estimate | TS-P1, TS-P2 |
| Export / replay / persistence | TS-Q1, TS-Q2, TS-Q3 |
| Help / About | TS-R1, TS-R2 |
| CLI | TS-S1, TS-S2, TS-S3 |

### Not covered by automated unit tests

These rely on the manual scripts above (Swing/serial/file behaviour that isn't unit-testable without a display or hardware):

- GUI rendering, the interactive canvas, and all wizard flows (Setup, Calibrate Axes, Pre-Plot Checklist, Test Color Stations).
- Visual station placement on the canvas (drag-to-move markers, right-click "Add station here", and canvas↔Settings-table coordinate sync).
- The Help/User Guide dialog rendering and table-of-contents navigation.
- Live serial communication and real motion on hardware.
- G-code export file content beyond basic format (see `GcodeBackendTest`).
- Per-colour hatch-override `--style` flag in the CLI.

These are candidates for future integration tests; until then, run the relevant
`TS-*` scripts before each release (the hardware-marked ones on a real plotter).
