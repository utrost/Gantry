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
└─ cli/   (optional) headless entry point for scripting/automation
```

(The original projects were kept under `legacy/` as a reference oracle during
the port and removed at cutover; their history lives in their own repos.)

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
| **0. Scaffold** ✅ | New monorepo; Maven multi-module skeleton; import both codebases (git subtree to preserve history); CI; both old tools still build/run (Python under `legacy/`) | Green build of all modules; old GUI + Python driver still work |
| **1. Shared model** ✅ | Move DTOs + `CoordinateTransform` into `model/`; delete the Python↔Java transform duplication | One transform implementation used everywhere; tests pass |
| **2. Port G-code backend** ✅ | Java `GcodeBackend` on jSerialComm — faithful port of `gcode_backend.py` (reader/poller threads, pen modes, `?` status, feed override) | Java backend reproduces Python's G-code on sample JSON; realtime position + speed override verified on fake serial and on the plotter |
| **3. Port orchestration** ✅ | `driver.py` → in-process `PlotService` in `app/`; replace stdin/stdout IPC (`POS:`/`SPEED:`/layer-start) with direct callbacks/events; unify "Process" + "Direct Draw" into one preset-driven pipeline (**pen preset is the default, complete path**) | Full plot from GUI with **no Python**: jog, layer-start, speed control, eased cursor all in-process |
| **4. Optimization stage** ✅ | Insert SVGToolBox PathOptimize + Simplify pre-refill, **per-layer** so station mapping is preserved | Measurable pen-travel reduction on a sample; layer→station intact; before/after stats shown |
| **5. New features** ✅ | Multipass/pigment (`pipeline-core`, benefits pen *and* watercolor) · G-code file export + re-plot (`plotter`) · refill stays in `watercolor` | Each behind a tested toggle in the GUI |
| **6. SVG ingestion & processing pipeline** ✅ | Port the SVG→command-model pipeline (`legacy/SVG2WaterColor`'s `ProcessorService`) into `pipeline-core`/`svgtoolbox-core`, plus the SVGToolBox SVG→SVG processors not yet covered by Phase 4; add "Process SVG"/"Draw SVG" GUI entry points and a headless CLI | An SVG file can be loaded in the GUI/CLI and produce a plottable command model with no external tooling; `legacy/` no longer the only path from SVG to plot |
| **7. Cutover** ✅ | Delete `legacy/`; docs; single-artifact release | One JAR, no Python anywhere |
| **8. Hardening & watercolor completion** ✅ | Post-cutover audit fixes: plotting-safety (Stop/disconnect) ✅, watercolor completion (colour→station mapping) ✅, UX polish ✅, cleanup ✅ | Stop/disconnect always leave the machine in a safe state ✅; SVG colours drive station assignment ✅; errors are visible to the operator ✅; UX polish ✅; cleanup ✅ |
| **9. Multi-document canvas** 🚧 NOT STARTED | Replace the single-drawing canvas with a list of independently placed/edited SVG imports (`SvgItem`s), each with its own transform, selectable and removable on its own | Two+ SVGs can be imported, independently positioned/scaled/rotated/mirrored, individually removed, and combined into one plottable/exportable job |
| **10. Per-area hatch styling** 🚧 NOT STARTED | Let different regions of the *same* SVG hatch differently: surface the existing per-colour override map in the GUI, then add per-element/per-group overrides for same-colour regions that need different patterns | A single SVG with two same-colour regions can be hatched with two different patterns/angles/gaps, set up entirely from the GUI, with CLI parity |

### Phase 8 — in progress (post-cutover self-audit)

A three-track audit (GUI/UX, watercolor-feature completeness, backend
robustness) of the shipped Phase-7 build surfaced the following. Items are
tagged 🔴 critical (can damage hardware / ruin a print), 🟠 high (the
watercolor vision is structurally incomplete), 🟡 medium (UX), 🟢 low (cleanup).

**🔴 Plotting safety — STARTED (this is the first work item)**
- **Stop left the pen down.** `PlotService.cancel()` only set a flag; `plot()`
  returned early and skipped `parkAtOrigin()`/`penup()`. Now fixed: `plot()`
  lifts the pen in a `finally` on cancel, independent of backend type.
- **`haltMotion()` raced the plot thread** on the serial write lock
  (`GcodeBackend`), clearing ack queues mid-`waitForOk` and interleaving
  `$X`/`penup` with in-flight commands. Now fixed: an `aborting` flag
  short-circuits the plot thread's stragglers, a sentinel wakes any in-flight
  `waitForOk`, and recovery (`$X` + pen-up) runs single-threaded after the
  realtime soft-reset.
- **Mid-plot serial errors were swallowed** (`send()` ate `IOException`;
  `waitForOk()` timeouts were a soft warning), so an unplug looked like a
  successful plot. Now surfaced as ERROR, and backend diagnostics are teed into
  the GUI log (previously only `System.out`, invisible in the app).
- **Disconnect mid-plot** now confirms and cancels the active plot first.
- *Remaining:* full GRBL alarm/hold state handling and propagating a hard
  serial-failure up to abort the plot (not just log it).

**🟠 Watercolor completion — DONE**
- The `watercolor/` module is now real: `ColorUtil` (hex parsing + redmean
  perceptual colour distance), `PaintStation` (id + colour), and `StationMapper`
  (`assignByColor` / `nearestStation`) — the colour-driven station assignment
  that replaces fragile positional naming.
- **Colour is now read from the SVG.** `Layer` gained a `color` field;
  `SvgImportStage` resolves each layer's stroke/fill (style attr, presentation
  attr, ancestor inheritance, `#rgb`/`#rrggbb`/`rgb()`/named colours), and the
  colour is preserved through optimize/multipass/overlay-bake.
- **Colour→station mapping:** `Edit ▸ Map Layer Colors to Stations` (and an
  automatic pass right after import when station colours are configured) routes
  each layer — and its `RefillCommand`s — to the nearest-colour pot. Layers with
  no matchable colour keep their original station, so nothing is dropped.
- **Brush rinse between colours:** a station with behavior `rinse` (or named
  `rinse`/`water`) is visited and swirled before each new colour layer.
- **Refill is now configurable and a real swirl:** `StationConfig` gained
  `color`, `dwellMs` and `swirlRadius`; `dip_swirl`/`rinse` trace an actual
  circle of the configured radius instead of the old hard-coded ±2 mm X jiggle;
  dwell replaces the magic 500 ms. All editable in Settings ▸ Refill Stations
  (new Color / Dwell / Swirl columns), back-compatible with old `config.json`.
- **Per-station dip depth (`zDown`) now drives real Z motion.** `PlotterBackend`
  gained `pendown(double zDown)`; on a Z-axis machine the dip/swirl lowers the
  pen to the station's configured depth (`G1 Z<zDown>`), while servo/M3 pens and
  mocks fall back to the normal pen-down. A station `zDown` of 0 means "unset" →
  use the global pen-down depth, so existing setups are unaffected.

**🟡 UX polish — DONE**
- Genuine failures (load/import/save/export/reprocess) now raise an error
  dialog via a shared `error()` helper as well as logging; workflow
  preconditions use a friendly `info()` dialog.
- Import and Process SVG run off the EDT through a `runBusy()` SwingWorker with
  a wait cursor, so the UI no longer freezes during heavy transforms.
- `onLoadCommands` now clears `lastImportedSvgFile`/`lastImportOptions` (and any
  undo), fixing the stale-state bug where *Edit ▸ Process SVG* reprocessed a
  previously-imported SVG after loading a `.json`.
- Save/Export confirm before overwriting an existing file.
- Menu mnemonics + accelerators (Ctrl+O/I/S/E/Q, Ctrl+Z); single-level **Undo**
  for the destructive transforms (Optimize / Process SVG / Map Colors).
- *Remaining (deferred, low value):* a recent-files list, and opening the User
  Guide now uses the desktop handler with a path fallback.

**🟢 Cleanup — DONE**
- Removed stale Python-era Javadoc (`driver.py`, "mapped in Python config") from
  `RefillCommand`, `GantryConfig`, and `PlotSettings`.
- Extracted the duplicated A4/Letter paper-size constants out of
  `SvgImportDialog`/`EditProcessDialog` into a shared `PaperSizes` helper.
- Extracted the duplicated green/red button colours in `PlotterPanel` into
  `ACTION_GREEN`/`ACTION_RED` constants.
- Removed dead code: `SettingsPanel.setEnabledWhileEditing`/`setEnabledDeep`
  (no callers).
- "User Guide" now uses the desktop handler with a path fallback (done as
  part of the UX-polish batch).



### Phase 9 — Multi-document canvas (not started)

**Problem.** The canvas currently holds exactly one drawing. `PlotterPanel`
has a single `currentOutput` (`ProcessorOutput`), and `VisualizationPanel`
has a single overlay transform (`overlayOffsetX/Y`, `overlayScale`,
`overlayRotation`, `overlayMirror`) applied to all of it. There's no way to
import a second SVG without replacing the first, and no concept of
selecting "which drawing" the Reset/Rotate/Mirror/Remove actions (added in
Phase 8's UX-polish batch) apply to. Real layouts — a sticker sheet, several
small motifs sharing a page, mixed-source artwork — need several
independently placed SVGs in one job.

**Goal.** Let the user import, position, edit and remove multiple SVGs on
one canvas, each independently, and combine them into one plottable /
exportable job — without disturbing the single-document workflow that
exists today (it should keep working unchanged for the common case of one
drawing).

**Scope — data model (do this first, headless, fully unit-tested before any GUI work):**
- A new `SvgItem` (working name) wrapping one imported `ProcessorOutput`
  plus its own placement: offset, scale, rotation (0/90/180/270), mirror,
  z-order, and a stable id. This is exactly today's per-panel overlay state,
  pulled out into a per-item record/class so there can be more than one.
- `PlotterPanel.currentOutput` (singular) becomes a `List<SvgItem>` plus a
  "selected item" id. A `compose()`/`flatten()` step merges every item's
  baked layers into one `ProcessorOutput` at plot/export time — reusing the
  existing `bakeOverlay()` logic per-item instead of once globally.
- Decide how layer/station ids are kept unique across items that started
  life as independent imports (e.g. namespace `Layer.id` by item, or dedupe
  at compose time) — this is the trickiest correctness question and should
  be settled with tests before touching `VisualizationPanel`.
- Saving/loading: either (a) extend the command-model JSON with an item
  list (breaking format change, needs a migration/back-compat path like
  `Layer`'s old constructor), or (b) keep single-document save/load as the
  "flattened" export and add a separate multi-document project file. Needs
  a decision before implementation — leaning (b) to avoid touching the
  interchange format used by the CLI and external tooling.

**Scope — GUI:**
- `VisualizationPanel`: replace the single overlay transform with
  per-`SvgItem` transforms; click-to-select an item (its bounding box/handles
  draw only when selected); drag/resize/rotate/mirror act on the selected
  item only.
- Right-click context menu (added in Phase 8) gains "Bring to Front/Back" /
  "Duplicate" alongside the existing Remove/Reset/Rotate/Mirror, now scoped
  to the selected item instead of "the" drawing.
- A simple item list/sidebar (or just relying on click-to-select on canvas)
  so an item can be selected even when off-screen or stacked under another.
- `Import SVG...` adds a new item instead of replacing the canvas; "Remove
  Drawing" removes only the selected item, not the whole canvas.
- Undo (single-level today) needs to cover add/remove/transform of
  individual items, not just whole-document swaps.

**Out of scope for this phase:** per-item SVGToolBox pre-processing
(Process SVG stays a whole-document operation for now), and any kind of
z-order-aware overlap/collision handling beyond simple draw order.

**Risks:**
- This touches the core canvas/transform code (`VisualizationPanel`,
  `PlotterPanel`'s document state) more than any change since Phase 7
  cutover — higher regression risk than the Phase 8 UX batch. Should land
  as data-model-first, GUI-second, each independently tested, not as one
  large patch.
- Multi-item undo and multi-item station/colour mapping are where the
  complexity actually lives; the canvas interaction (click-to-select,
  per-item drag handles) is mechanical by comparison.
- **Undo model mismatch.** Today's undo is a single whole-document snapshot;
  per-item add/remove/transform needs a real command/history stack, which is
  a different data structure, not an extension of the current one.
  *Mitigation:* ship v1 with a deliberately small undo scope (e.g. "undo
  last add/remove only," no per-transform undo) rather than building a full
  command stack up front; revisit if it proves insufficient in practice.
- **Z-order affects plot/export order, not just visuals.** Out-of-scope
  visual overlap handling doesn't remove the need for a defined plot
  sequence — without explicit item z-order, plot/export order would be
  whatever order items happen to sit in the list, which is surprising and
  not user-controllable. *Mitigation:* the `SvgItem.zOrder` field already
  scoped above should double as plot/export sequence, with the GUI
  ordering controls (Bring to Front/Back) driving both the visual stack and
  the plot order from one source of truth.
- **Selection scope is ambiguous for existing whole-document actions.**
  Optimize Loaded Commands, Process SVG, and Map Layer Colors to Stations
  (all Edit-menu actions today) currently operate on "the" document; Phase 9
  doesn't yet say whether they should apply only to the selected item or
  stay whole-document/compose-time operations. *Mitigation:* default all
  three to operating on the composed/flattened output (matching today's
  behavior) unless/until a specific need for per-item processing is
  identified — avoids scope creep into per-item SVGToolBox pipelines, which
  is already explicitly out of scope above.

---

### Phase 10 — Per-area hatch styling (not started)

**Problem.** `HatchProcessor` already supports per-*colour* hatch overrides:
`Config.overrides()` is a `Map<String, HatchStyle>` keyed by fill hex, and
`getStyleFor()` falls back to `globalStyle` when no override matches. This
is wired into the CLI (an untested `--style` flag, per `docs/TESTING.md`
§3) but has no GUI surface at all — `ConfigBuilder`/`SvgImportDialog` only
expose one global pattern/angle/gap. Worse, the override key is *colour*,
not *element* or *region*: two shapes that happen to share a fill colour
can never hatch differently today, even though that's a common real case
(e.g. two same-colour leaves that should cross-hatch and linear-hatch
differently for shading).

**Goal.** Let a single imported SVG hatch different regions differently,
end to end from the GUI, in two layers of capability:
1. Per-colour overrides (mechanism already exists) — make them usable
   without hand-writing CLI flags.
2. Per-element/per-group overrides for regions that share a colour —
   genuinely new capability, needed for the same-colour case.

**Scope — Tier 1: surface existing per-colour overrides in the GUI.**
- Add a colour→style table to the Process SVG dialog (or its own dialog):
  rows are the distinct fill colours found in the loaded SVG (already
  enumerable — `PaletteProcessor`/`VisibilityProcessor` already walk fills
  for similar purposes), each row picks pattern/angle/gap or "use global."
- `ConfigBuilder` already accepts `overrides(Map<String, HatchStyle>)` —
  this is wiring, not new model work. `SvgToolboxPipelineTest` /
  `HatchProcessorTest` already cover the processor; add a GUI-facing test
  (e.g. a `ConfigBuilderTest` case asserting the table maps to the right
  `Config.overrides()`).
- CLI: keep `--style` as-is; add the missing test flagged in
  `docs/TESTING.md` §3 ("Per-color hatch-override `--style` flag in CLI").
- This tier is low-risk, additive, and unblocks real usage of an existing
  but-dormant feature — good candidate to land first and independently.

**Scope — Tier 2: per-element/per-group overrides (same-colour regions).**
- New override key beyond colour. Two realistic options:
  - **Group/layer id.** `LayerProcessor` already groups elements into
    Inkscape-style `<g>` layers; let a hatch override target a layer id
    instead of (or in addition to) a colour. Reuses an existing grouping
    mechanism — lowest-effort option, but only works if the user has
    already organised the SVG into layers/groups that line up with the
    desired hatch regions.
  - **Direct selection.** Click-to-select a shape on the canvas (natural
    pairing with Phase 9's click-to-select work on `VisualizationPanel`)
    and assign it a hatch style by element id. More general, but needs a
    stable per-element id surviving from SVG parse through to
    `HatchProcessor`, and canvas UI that doesn't exist yet for "select a
    sub-shape" (Phase 9 only scopes selecting whole `SvgItem`s).
  - Recommend starting with layer/group-id overrides (reuses existing
    model, no new canvas interaction) and treating direct-shape selection
    as a follow-on if layer/group granularity proves too coarse in
    practice.
- `Config.overrides()`'s key type would need to widen from "colour hex
  string" to "colour hex OR group id," or split into a second map
  (`groupOverrides()`) — needs a decision before implementation; a second
  map is probably cleaner since the two key spaces (colour vs. id) don't
  collide and `getStyleFor()` can just check group-id first, then colour,
  then global.
- `HatchProcessor.shouldSkipColor`/`getStyleFor` both currently key off
  `target.getAttribute("fill")` read at hatch time — group-id lookup needs
  the element's containing layer/group, which `LayerProcessor` already
  computes earlier in the pipeline; the override lookup must run after
  layering, not before (pipeline order matters here, same caution as
  Phase 4's "per-layer station mapping" lesson).

**Out of scope for this phase:** true sub-path masking (hatching part of
a single path differently from another part of the *same* path) — this
phase operates at element/group granularity, not within a single shape.

**Risks:**
- Tier 2's key-widening touches `Config`, `ConfigBuilder`, and
  `HatchProcessor` — all have existing test coverage
  (`ConfigBuilderTest`, `HatchProcessorTest`) that should keep passing
  unchanged for the colour-only path; new tests should cover the
  group-id path in isolation before wiring it into the GUI.
- If Phase 9 (multi-document canvas) lands first, "per-element selection"
  there is a natural reuse point for Tier 2's direct-selection option —
  worth sequencing Tier 2 after Phase 9 rather than building two separate
  selection mechanisms in parallel.
- Tier 1 alone (GUI surface for existing colour overrides) delivers most
  of the user-visible value for SVGs that already use colour to delineate
  regions (the common case for hand-prepared plot art); Tier 2 should be
  validated against a real use case before committing to the group-id
  vs. direct-selection design choice.

---

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

- **Serial permissions / native libs** ✅ — jSerialComm bundles natives; on Linux the
  user still needs to be in the `dialout` group (identical to old pyserial). Verified
  in Phase 2.
- **Per-layer reorder** ✅ — SVGToolBox's PathOptimize operates per `<g>` group, so
  it cannot reorder across Inkscape layers. Station assignment and refill are safe.
  Addressed in Phase 4.
- **Float/format parity (Python↔Java)** ✅ — coordinate transform math matches;
  `CoordinateTransformTest` covers all axis combinations.
- **Scope creep** ✅ — contained by phase gates; new features (multipass, G-code
  export) were quarantined to Phase 5.
- **License reconciliation** ✅ — confirmed compatible before merge (Phase 0).
- **Phase 7 cutover** ✅ — `legacy/` removed; the original SVGToolBox and
  SVG2WaterColor sources remain available in their own repositories.

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

### ✅ SVG-ingestion pipeline — closed in Phase 6

`pipeline-core`'s `SvgImportStage` converts an SVG file into a `ProcessorOutput`
command model (Batik + XML fallback parsing, Inkscape layer detection, primitive
normalisation, curve linearisation, fit-to-format, mirror, refill-split).

`svgtoolbox-core` contains all 13 SVGToolBox SVG→SVG processors:
`VisibilityProcessor`, `StyleNormalizerProcessor`, `RotateProcessor`,
`StrokeWidthProcessor`, `PaletteProcessor` (CIELAB quantisation),
`HatchProcessor` (linear/cross/zigzag/wave/dot, per-colour overrides, area
filter, no-hatch list), `SimplifyProcessor`, `LinesimplifyProcessor`,
`LinemergeProcessor`, `LinesortProcessor` (2-opt), `ReloopProcessor`,
`LayerProcessor`, `CropProcessor`, plus `PathOptimizeProcessor` and
`SvgStatistics`. All are orchestrated by `SvgToolboxPipeline`.

`cli/SvgImportCli` is a full headless converter with `--toolbox` flag exposing
all SVGToolBox options.

`SvgImportDialog` has an "Import" tab (scaling, refill, curve step, paper
format) and a "Process SVG (optional)" tab (all SVGToolBox options), wired into
`PlotterPanel`'s "Import SVG…" button.

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

- **History-import method** ✅ — used `git subtree`; both legacy projects imported
  with full commit history under `legacy/`.
- **Keep a CLI?** ✅ — retained as `cli/SvgImportCli`; full headless SVG→JSON
  converter with all SVGToolBox options.
- **Package namespace** ✅ — `org.trostheide.gantry.*`.
- **CI provider / release packaging** — not yet decided; pending Phase 7.

---

## Source projects

- **SVG2WaterColor** — Java/Swing GUI, watercolor processing + refill, Python driver
  (GRBL G-code streaming, realtime position, feed-rate override). To be absorbed;
  Python removed.
- **SVGToolBox** (https://github.com/utrost/SVGToolBox) — Java 17 + Maven + Batik,
  SVG→SVG optimization processors (PathOptimize, Simplify, Hatch, palette, …).
  To be absorbed as `svgtoolbox-core`.
