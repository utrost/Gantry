# Gantry — Roadmap

> **Gantry** is an all-Java toolkit that prepares SVGs for pen plotters and drives
> the plotter directly: optimize → position → process → stream/export G-code.
> **Pen-plotting is a first-class citizen**; watercolor (paint stations + refill)
> is one optional capability layered on top. Gantry merges the prep features of
> **SVGToolBox** with the processing + plotter-driving of **SVG2WaterColor**, and
> **removes the Python driver entirely** (it only existed for the now-sold AxiDraw).

---

## Why this exists

- The Python driver was only required because the AxiDraw uses the Python-only
  `pyaxidraw` library. With the AxiDraw sold and only a **GRBL/G-code plotter**
  remaining, G-code-over-serial is trivial in Java — Python is now pure overhead
  and a fragile subprocess/IPC boundary.
- **SVGToolBox** already implements (in Java 17 + Maven + Batik, the same stack)
  the geometry optimizations we'd otherwise want from `vpype`:
  `PathOptimizeProcessor` (greedy nearest-neighbor travel minimization),
  `SimplifyProcessor` (Ramer–Douglas–Peucker), `HatchProcessor` (fills → hatch),
  plus palette quantization, crop, rotate, stroke normalization.
- Merging the two and going all-Java yields: one build, one artifact, one GUI,
  no IPC text-protocol parsing, no Python environment to install, and a shared
  geometry/coordinate model (today `transforms.py` and `CoordinateTransform.java`
  are hand-synced duplicates).

## Guiding principles

1. **Pen-plotting is the default, complete path.** Watercolor is an opt-in stage,
   not a separate program. The old "Process SVG" (refill) and "Direct Draw SVG"
   (no refill) entry points collapse into **one pipeline driven by presets**.
2. **Positioning and optimization are mode-independent core stages** — they run
   the same whether or not you refill.
3. **Optimization and multipass run *before* refill-split**, because refill points
   are a function of final stroke order and length. Refill is the last semantic
   transform.
4. **Keep JSON as an interchange/save format**, not as a process boundary. The live
   path is in-memory Java objects.

---

## Pipeline (stages, each toggleable)

```
SVG → Batik DOM
   → optimize        (simplify, path-optimize)              ← first-class, default ON
   → position        (scale, fit-page, rotate, mirror, align)  ← first-class
   → flatten         (curves → polylines)
   → multipass       (optional)
   → stations+refill (watercolor only — optional)           ← the opt-in stage
   → command model   (in-memory; JSON only for save/load/interchange)
   → output          (stream to serial  OR  write .gcode file)
```

- **Pen-plot preset** = everything except `stations+refill`.
- **Watercolor preset** = adds `stations+refill`.

Same pipeline, same renderer, same output, same interactive positioning overlay
(move / scale / rotate 90° / mirror) for every job.

---

## Target architecture — fresh monorepo, multi-module Maven

```
gantry/                       (new repository)
├─ model/            shared DTOs (Point, Layer, Command…) + CoordinateTransform
├─ svgtoolbox-core/  SVG→SVG processors (PathOptimize, Simplify, Hatch, palette…)
├─ pipeline-core/    flatten, position, multipass, command model, output orchestration
│                    — PEN PLOTTING WORKS END-TO-END WITH JUST THIS MODULE
├─ watercolor/       station mapping + refill stages (optional; depends on pipeline-core)
├─ plotter/          serial G-code backend (jSerialComm) + mock + .gcode file writer
├─ app/              Swing/FlatLaf GUI + orchestration service (replaces driver.py)
├─ cli/   (optional) headless entry point for scripting/automation
└─ legacy/           old Python driver, kept as reference until cutover, then deleted
```

Module names encode the priority: removing `watercolor/` still leaves a fully
functional pen plotter.

### Tech stack
Java 17 · Maven (multi-module) · Apache Batik (SVG) · **jSerialComm** (the one new
dependency — bundled natives, cross-platform) · FlatLaf/Swing · Jackson (JSON).
All already in-stack except jSerialComm.

---

## Phased delivery

Each phase ends green and runnable. The Python driver stays as the reference
oracle until Phase 3.

| Phase | Goal | Exit criteria |
|---|---|---|
| **0. Scaffold** | New monorepo; Maven multi-module skeleton; import both codebases (git subtree to preserve history); CI; both old tools still build/run (Python under `legacy/`) | Green build of all modules; old GUI + Python driver still work |
| **1. Shared model** | Move DTOs + `CoordinateTransform` into `model/`; delete the Python↔Java transform duplication | One transform implementation used everywhere; tests pass |
| **2. Port G-code backend** | Java `GcodeBackend` on jSerialComm — faithful port of `gcode_backend.py` (reader/poller threads, pen modes, `?` status, feed override) | Java backend reproduces Python's G-code on sample JSON; realtime position + speed override verified on fake serial and on the plotter |
| **3. Port orchestration** | `driver.py` → in-process `PlotService` in `app/`; replace stdin/stdout IPC (`POS:`/`SPEED:`/layer-start) with direct callbacks/events; unify "Process" + "Direct Draw" into one preset-driven pipeline (**pen preset is the default, complete path**) | Full plot from GUI with **no Python**: jog, layer-start, speed control, eased cursor all in-process |
| **4. Optimization stage** | Insert SVGToolBox PathOptimize + Simplify pre-refill, **per-layer** so station mapping is preserved | Measurable pen-travel reduction on a sample; layer→station intact; before/after stats shown |
| **5. New features** | Multipass/pigment (`pipeline-core`, benefits pen *and* watercolor) · G-code file export + re-plot (`plotter`) · refill stays in `watercolor` | Each behind a tested toggle in the GUI |
| **6. SVG ingestion & processing pipeline** ✅ | Port the SVG→command-model pipeline (`legacy/SVG2WaterColor`'s `ProcessorService`) into `pipeline-core`/`svgtoolbox-core`, plus the SVGToolBox SVG→SVG processors not yet covered by Phase 4; add "Process SVG"/"Draw SVG" GUI entry points and a headless CLI | An SVG file can be loaded in the GUI/CLI and produce a plottable command model with no external tooling; `legacy/` no longer the only path from SVG to plot |
| **7. Cutover** | Delete `legacy/`; docs; single-artifact release | One JAR, no Python anywhere |

### Phase 6 — done

All 13 SVGToolBox SVG→SVG processors (Visibility, StyleNormalizer, Rotate,
StrokeWidth, Palette, Simplify, Hatch + 5 patterns, Linesimplify, Linemerge,
Linesort, Reloop, Layer, Crop, PathOptimize) plus `SvgStatistics` are ported
into `svgtoolbox-core`, orchestrated by `SvgToolboxPipeline.buildPipeline`/
`process` (mirrors legacy `SvgToolboxRunner.processPipeline`, with progress
callback).

`pipeline-core`'s `SvgImportStage` gained:
- `importSvg(File, SvgImportOptions)` — unchanged, existing SVG→command-model path.
- `importSvg(File, Config, SvgImportOptions)` — runs the SVGToolBox pipeline
  against the loaded document first, then imports.
- `importSvg(Document, String, SvgImportOptions)` — for callers with an
  already-parsed document.

Headless CLI (`cli/SvgImportCli`) gained a `--toolbox` flag plus palette,
hatch, crop, rotate, stroke-width, simplify, and line-optimization options
mirroring legacy `SvgToolboxRunner`.

GUI `SvgImportDialog` gained a "Process SVG (optional)" tab exposing the same
SVGToolBox options; `PlotterPanel`'s "Import SVG..." runs the toolbox pipeline
first when enabled.

---

## Features in scope (chosen)

- **Path optimization in pipeline** — PathOptimize (min travel) + Simplify as a
  stage before refill. The biggest plot-time win and the main reason to merge.
- **Multipass / pigment buildup** — draw each stroke N times (more pigment /
  rewetting for watercolor; bolder lines for pen). Lives in `pipeline-core`.
- **Export G-code to file** — save a `.gcode` file and re-plot it, not just stream
  to the port live. Lives in `plotter`.

---

## Risks & mitigations

- **Serial permissions / native libs** — jSerialComm bundles natives; on Linux the
  user still needs to be in the `dialout` group (identical to today's pyserial, not
  worse). Verify early in Phase 2.
- **Per-layer reorder** — SVGToolBox's PathOptimize must not reorder/flatten across
  Inkscape layer groups, or it scrambles station assignment (and refill). Addressed
  in Phase 4 — and far easier to fix now that it's one codebase.
- **Float/format parity (Python↔Java)** — coordinate formatting (`%.3f`) and
  transform math must match during the parity gate. Covered by the Phase 2 diff test.
- **Scope creep** — the phase gates contain it; new features are quarantined to
  Phase 5.
- **License reconciliation** — confirm SVG2WaterColor and SVGToolBox licenses are
  compatible before the merge (Phase 0).

---

## Feature-parity audit (Phases 0–5 vs. legacy)

An in-depth audit of `legacy/SVG2WaterColor` and `legacy/SVGToolBox` against the
current Gantry modules (post Phase 5) found the plotter-driving side at full
parity (and ahead, with file export/replay and multipass), but identified one
major gap that Phase 6 above exists to close.

### ✅ Ported / at parity
- **Orchestration** (`driver.py` → `app/.../PlotService`): layer gating, soft
  clamping + OOB warnings, preflight bounds check, refill (`simple_dip` /
  `dip_swirl` / `default_station` fallback), per-waypoint position reporting,
  debug-position drift logging, cancel.
- **Coordinate transforms** (`transforms.py` → `model/CoordinateTransform`):
  rotate→swap→invertX→invertY, content bounds, 5-mode canvas alignment,
  portrait auto-swap + alignment translation.
- **G-code backend** (`gcode_backend.py` → `plotter/GcodeBackend` +
  `GcodeFormatter`): GRBL init (`$X`, `G21`, `G90`, `G92`), pen modes, realtime
  `?` status polling, feed-override realtime commands; plus new
  `.gcode` file export/replay with no Python equivalent.
- **SVGToolBox optimize/simplify** (command-model form, `pipeline-core/optimize`):
  RDP simplify, greedy-NN + 2-opt reorder, multipass (new).
- **Backend abstraction**: `PlotterBackend` (Gcode/Mock/GcodeFile/Fake) covers
  the legacy `PlotterBackend` ABC, minus AxiDraw — intentionally dropped
  (GRBL-only decision, confirmed out of scope, not a gap).

### ✅ Major gap: no SVG-ingestion pipeline (addressed by Phase 6, now closed)
Nothing in Gantry currently turns an SVG file into a `ProcessorOutput`. The
GUI's `PlotterPanel` only offers "Load Commands JSON..." — it expects
pre-generated command JSON. Missing pieces:

- From `legacy/SVG2WaterColor`'s `ProcessorService.java`: SVG parsing (Batik +
  XML fallback) with Inkscape layer detection → `Layer1`/`Layer2` + `stationId`;
  primitive normalization (rect/circle/ellipse/line/polyline/polygon → path);
  curve linearization (configurable step, default 0.5mm); size/position
  transform (fit-to-format A5/A4/A3/XL, aspect-ratio lock, mirror, padding);
  paint-capacity segmentation (auto-`REFILL` insertion at `maxDrawDistance`,
  including path-closure handling); no-refill mode for pure pen plotting; the
  headless `WatercolorProcessor` CLI (Gantry's `cli` module is still an empty
  placeholder).
- From `legacy/SVGToolBox` (Gantry's `svgtoolbox-core` is still a placeholder):
  `VisibilityProcessor`, `StyleNormalizerProcessor`, `RotateProcessor` (canvas
  rotation), `StrokeWidthProcessor`, `PaletteProcessor` (CIELAB quantization),
  `HatchProcessor` (linear/cross/zigzag/wave/dot patterns, per-color overrides,
  area filtering, no-hatch list), `LinesimplifyProcessor`,
  `LinemergeProcessor`, `LinesortProcessor` (2-opt), `ReloopProcessor`,
  `LayerProcessor` (color→Inkscape-layer organization, viewBox auto-fit),
  `CropProcessor` (custom/A4/Letter presets).
- **GUI**: legacy had 3 tabs — *Process SVG* (SVG→JSON with refill), *Draw SVG*
  (SVG→JSON, no refill), *Plot*. Gantry's `PlotterPanel` only covers the *Plot*
  tab equivalent (plus Optimize/Multipass/Export added in Phases 4–5).

### ✅ Verified during Phase 6
- Manual jog / pen up-down / interactive raw G-code server mode — `PlotterPanel`
  has jog buttons (with step size), Pen Up/Down buttons, and a raw G-code
  console (`sendRaw`), covering `driver.py`'s `MOVE`/`PEN`/`RAW`
  interactive-server commands. At parity.
- Station config `z_down` (Z-axis dip depth) — present per-station in both
  legacy `config.py`'s `STATIONS` dict and Gantry's `StationConfig.zDown`, but
  **unused by `perform_refill`/`performRefill` in both** (refill dip uses the
  global `pendown()` Z, not a per-station value). Gantry's `performRefill`
  matches legacy `perform_refill` exactly (move → pendown → sleep → penup,
  optional `dip_swirl`). At parity — no fix needed.

This gap previously meant `legacy/` was the **only** path from an SVG file to
a plottable command model. Phase 6 closes this gap: SVG ingestion + the full
SVGToolBox processor pipeline are now available in `pipeline-core`/
`svgtoolbox-core` with GUI and CLI entry points. Phase 7 (cutover) can proceed.

## Deferred decisions

- **History-import method** — recommended: `git subtree` (simpler than `filter-repo`).
- **Keep a CLI?** — both old tools had one; cheap to retain for automation.
- **Package namespace** — keep `org.trostheide.gantry.*`.
- **CI provider / release packaging** — decide in Phase 0.

---

## Source projects

- **SVG2WaterColor** — Java/Swing GUI, watercolor processing + refill, Python driver
  (GRBL G-code streaming, realtime position, feed-rate override). To be absorbed;
  Python removed.
- **SVGToolBox** (https://github.com/utrost/SVGToolBox) — Java 17 + Maven + Batik,
  SVG→SVG optimization processors (PathOptimize, Simplify, Hatch, palette, …).
  To be absorbed as `svgtoolbox-core`.
