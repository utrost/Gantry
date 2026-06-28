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
(image)           (raster JPG/PNG → SVG, optional front stage — Phase 18)  ← opt-in
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
| **11. CLI/GUI parity** 🚧 NOT STARTED | Close the plot-affecting capability gaps between the headless CLI and the GUI in both directions: CLI gains G-code export, multipass, the post-import Optimize stage, and colour→station mapping; GUI gains the CLI-only per-colour hatch/stroke-width/no-hatch/min-area knobs (folded into Phase 10 Tier 1) | A batch CLI run can produce a plot-ready G-code file with multipass/station-mapping applied, with no GUI session involved; the GUI exposes every per-colour toolbox knob the CLI already has |
| **12. Per-pattern hatch parameters** ✅ | Give the non-linear hatch patterns their own tunable parameters instead of deriving everything from `gap`: wave/zigzag amplitude + wavelength, dot radius. Backward-compatible (0 = auto, keeps today's gap-derived defaults) | Wave amplitude, wave/zigzag wavelength, and dot radius are independently adjustable in both GUI dialogs and the CLI; leaving them at 0 reproduces the previous gap-derived behaviour exactly |
| **13. Guided workflow infrastructure** ✅ | A reusable step-by-step `WizardDialog` shell (progress trail, Back/Next/Skip/Cancel, per-step validation) that Phases 14–16 are built on, instead of three one-off dialogs. Also added the `Machine` menu (between Edit and Settings), giving Connect/Disconnect and Home a menu/keyboard home for the first time, plus the launchers for all three wizards | A throwaway 2-step demo wizard can be built from the shared component in under an hour; no plot-affecting logic lives in it |
| **14. Pre-plot wizard** ✅ | An optional, skippable step-by-step pre-flight before Start: connection → home → frame the job (pen-up bounding-box trace) → physical checklist (pen installed/lowered correctly, paper taped, correct layer selection) → confirm | A first-time user can run an entire job — connect through Start — without leaving the wizard, and an expert user can dismiss it and use Start directly exactly as today |
| **15. Machine setup wizard (first run)** ✅ | A guided first-run flow that walks `SettingsPanel`'s fields in a sensible order (connection → geometry → orientation/origin → pen mode/speeds) instead of presenting one long form. Shipped by re-parenting the *real* `SettingsPanel` section panels into wizard steps (no duplicated widgets), with a first-run auto-prompt and a "Run Setup Wizard…" launcher in the Settings dialog | A brand-new machine can be configured end-to-end via the wizard with zero prior knowledge of where each setting lives in `SettingsPanel`; the existing all-in-one Settings dialog is unchanged and still works for edits |
| **16. Axis calibration wizard** ✅ | Guided axis-direction sanity check (does +X/+Y on screen match +X/+Y on the machine?) and a measure-and-correct scale calibration (command a known travel distance, let the user enter what was actually measured, compute and offer to write corrected GRBL `$100`/`$101` steps/mm) | A user can detect and fix a reversed axis without reading GRBL docs, and can correct a steps/mm mismatch (e.g. commanded 200 mm, actual 195 mm) by entering one measured number, with the computed `$10x` value previewed before it's sent |
| **17. Visual station placement + watercolor test-run** ✅ | Two reinforcing halves over the same `StationConfig` data: (A) make the refill-station dots already drawn on the canvas *draggable*, and right-click-on-bed *adds* a station at that mm position, syncing live with the `SettingsPanel` station table; (B) a `Machine > Test Color Stations…` wizard that physically drives the brush to each station (pen-up dry visit → optional wet dip with the station's real behaviour/dwell/swirl), with jog-to-nudge writing corrections back to the same station — placement and verification edit one backing model | A station can be positioned by dragging its marker on the canvas (table updates live, and vice-versa) and added by right-clicking the bed; a connected operator can walk every configured station, confirm the brush lands over the right pot, nudge any that miss, and have the correction persist — all without typing raw mm coordinates |
| **18. Raster vectorization (image → SVG front stage)** ✅ | Absorb the standalone **Vectorize** (BoofCV-Batik Vectorizer) tool as a new `vectorize` module that turns a raster image (JPG/PNG) into an SVG, then hands that SVG to the *existing* `SvgImportStage` — a new optional front stage *before* `svgtoolbox-core`, opening the full **image → SVG → process → plot** path. Ported by copying source into Gantry (the Vectorize repo stays untouched); re-homed under `org.trostheide.gantry.vectorize`; wired into both the CLI and a GUI "Import Image…" entry point | A JPG/PNG can be loaded in the GUI or CLI, vectorized with a chosen strategy, and flow straight into the existing import → toolbox → plot pipeline with no external tooling; Gantry ships as one AGPLv3 artifact and the standalone Vectorize repo is unmodified |
| **19. Vectorize live-preview studio** 🚧 Tiers 1–3 landed (GUI verify pending) | Replace Phase 18's blind two-dialog Import-Image flow with a single live-preview workspace: source image and vector preview side by side, **debounced re-trace** as you change strategy/parameters, preset-first controls, and plotter-aware readouts (stroke/point counts, single-stroke vs filled). Tuned SVG still hands off to the existing `SvgImportStage`; the source image + parameters are remembered so the drawing can be **re-vectorized** later without starting over | A user can load an image and watch the trace update live as they tune (no commit-to-see), judge plottability from on-screen metrics, then import into the existing positioning/plot pipeline; re-opening a vectorized drawing restores the studio pre-populated for re-tuning |

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

### Phase 11 — CLI/GUI parity (not started)

**Problem.** A feature-by-feature audit of `SvgImportCli.java` against the
GUI (`SvgImportDialog`/`PlotterPanel`) found gaps in both directions:

- **GUI can do that CLI can't:** the post-import Optimize stage (simplify
  tolerance + stroke reorder, via `OptimizeStage`), multipass/passes
  (`MultipassStage`), colour→station mapping (`StationMapper`, the core
  watercolor feature), and G-code export. A CLI batch run today can only
  ever produce command-model JSON — it cannot produce a plot-ready G-code
  file, and it cannot apply multipass or station assignment at all.
- **CLI can do that GUI can't:** per-colour hatch overrides (`--style
  HEX:ANGLE:GAP:PATTERN`), no-hatch colour list (`--no-hatch`), minimum
  hatch area (`--min-area`), and per-colour stroke width
  (`--layer-width`). These all map directly onto fields `Config` /
  `ConfigBuilder` already has; the CLI parses and wires them, the GUI
  simply never built controls for them.

**Goal.** Close the gaps that are genuinely missing functionality, on
both sides — but only where headless/GUI parity actually makes sense.
Plotting, jogging, replay, live visualisation, and connect/serial control
are GUI-only by *design*, not by gap, and stay that way.

**Scope — CLI gains (batch/headless plot production):**
- `--gcode <path>`: run the existing `GcodeBackend` formatting logic
  against the produced `ProcessorOutput` and write a `.gcode` file
  alongside (or instead of) the JSON output. This is the highest-value
  addition — it's the difference between "CLI produces an intermediate
  file" and "CLI produces a plot-ready artifact."
- `--passes N`: run `MultipassStage` before writing output, matching the
  GUI's "Passes" spinner.
- `--optimize-tolerance`, `--optimize-reorder`: run the post-import
  `OptimizeStage` (distinct from the existing `--toolbox-simplify`, which
  runs inside the SVGToolBox pipeline, not after import) before writing
  output. Flag names need to make the distinction from the existing
  toolbox-simplify flag obvious in `--help` text, since they sound similar
  but run at different pipeline stages.
- `--map-stations`: run `StationMapper.assignByColor` so layer→station
  assignment happens without a GUI session, the same way the GUI's "Map
  Layer Colors to Stations" menu action does.
- Each of these is independent and can land/ship separately; no reason to
  batch them into one patch.

**Scope — GUI gains (already covered, cross-reference not duplicate):**
- Per-colour hatch overrides, no-hatch colours, min hatch area, and
  per-colour stroke width are **Phase 10 Tier 1** in all but name — that
  phase's "surface the existing per-colour override map in the GUI" scope
  already covers `--style`/`--no-hatch`/`--min-area`; add `--layer-width`
  (per-colour stroke width) to Tier 1's scope rather than tracking it here
  separately, since it's the same kind of colour-keyed `Config` map and
  the same GUI table naturally extends to a second column for it.

**Out of scope:** porting any plotting/serial/jog/replay/visualization
capability to the CLI — these require a live machine session and have no
headless analogue; CLI stays a batch SVG→artifact converter, not a
plotting client.

**Risks:**
- `--gcode` needs a `PaperFormat`/machine-settings source for the CLI
  (origin, axis orientation, pen mode, feed rates) that today only exists
  in the GUI's persisted `Settings`. The CLI either needs its own
  settings file/flags mirroring `SettingsPanel`'s fields, or to load the
  same settings file the GUI writes — the latter is less flag-bloat but
  couples the CLI to a GUI-written file existing first. Needs a decision
  before `--gcode` can be implemented; this is the one genuinely new
  piece of plumbing in this phase (everything else reuses an existing
  stage/processor with no new dependencies).
- `--optimize-tolerance`/`--optimize-reorder` vs. `--toolbox-simplify`
  risk user confusion (two different "simplify" knobs at two different
  pipeline stages) — needs clear `--help` text and a `docs/USER_GUIDE.md`
  note distinguishing them, not just a code-level distinction.
- Low regression risk overall: every CLI addition here calls an existing,
  already-tested stage (`MultipassStage`, `OptimizeStage`, `StationMapper`,
  `GcodeBackend`) — this phase is wiring, not new logic, aside from the
  settings-source question above.

---

### Phase 12 — Per-pattern hatch parameters ✅

**Problem.** Every hatch pattern was driven by just two knobs — `angle` and
`gap` (`HatchStyle(angle, gap, patternName)`). The non-linear patterns
derived their remaining shape parameters as fixed ratios of `gap`, with no
way to tune them independently:
- `WaveHatchPattern`: amplitude = `gap/3`, wavelength = `gap×2`.
- `ZigZagHatchPattern`: amplitude = `gap/2`, wavelength = `gap`.
- `DotHatchPattern`: dot radius reused the global stroke width, spacing =
  `gap`.

So you could not, e.g., make tall narrow waves or large sparse dots without
also changing line spacing.

**Goal.** Give wave/zigzag an independent amplitude and wavelength and give
dot an independent radius, exposed in both GUI dialogs and the CLI, without
changing behaviour for anyone who doesn't touch the new controls.

**Design.**
- Extend the `HatchStyle` record with three optional fields: `amplitude`,
  `wavelength`, `dotRadius`. A value of `0` means "auto" — fall back to the
  exact gap-derived default used before, so existing JSON/CLI/GUI flows are
  byte-for-byte unchanged. A backward-compatible 3-arg constructor
  (`angle, gap, patternName`) delegates with all three set to `0`, matching
  the established record-evolution pattern (`Layer`'s legacy constructor).
- Each pattern reads `style.xxx() > 0 ? style.xxx() : <old gap-derived
  default>`, so the "auto" path is the previous formula verbatim.
- GUI: both `SvgImportDialog` and `EditProcessDialog` gain "Amplitude
  (0 = auto)", "Wavelength (0 = auto)", "Dot radius (0 = auto)" spinners,
  following the existing "0 = off/none/auto" convention already used for
  stroke-width override and simplify tolerance. `EditProcessDialog` also
  remembers them across opens like the other hatch fields.
- CLI: `--hatch-amplitude`, `--hatch-wavelength`, `--dot-radius` flags,
  defaulting to `0` (auto).

**Out of scope:** distinct amplitude/wavelength *semantics* per pattern
(wave and zigzag share the two fields; their differing default ratios are
preserved only on the auto path). Good enough — when set explicitly, both
interpret amplitude/wavelength the obvious way.

---

### Phases 13–16 — Guided workflows (not started)

**Why this group exists.** Phases 0–12 are all about *preparing geometry*
correctly. None of them help with the surrounding physical process: is the
right pen in the holder, is the paper actually taped where the machine
thinks it is, does "+X on screen" actually mean "+X on the table," is the
machine's steps/mm even correct out of the box? Today that's all tribal
knowledge the operator has to carry in their head, every time, with the only
machine-side help being a static three-line banner (`PlotterPanel`'s "Step
1/2/3" guidance text) and a single `$H` homing command — no checklist, no
guided setup, no calibration aid. Three different audiences need three
different guided flows, so this lands as one shared infrastructure phase plus
three independent wizards built on it:

- **A first-time owner** setting up a brand-new machine (Phase 15).
- **Anyone, occasionally** — when an axis seems backwards or a measured
  distance doesn't match the commanded one (Phase 16).
- **Every operator, every job** — the pre-flight before clicking Start
  (Phase 14), which is the direct evolution of the "frame the job" idea
  discussed earlier: framing is one *step* inside this wizard, not a
  standalone feature.

All three are explicitly **optional** — an expert operator who knows their
machine should be able to ignore every wizard and use Connect/Home/Start
exactly as today. These are guard rails, not a replacement for the existing
controls.

**GUI/UX — where these live.** Surfaced by frequency, not all crammed into
one place:
- **A new top-level `Machine` menu**, between `Edit` and `Settings`. Today
  `Connect` and `Home` exist *only* as canvas buttons with no menu/keyboard
  path at all — `Machine` fixes that and becomes the one discoverable home
  for every physical-plotter action: `Connect`/`Disconnect`, `Home`, then
  `Setup Wizard…`, `Calibrate Axes…`, and `Pre-Plot Checklist…`. This menu
  is itself a Phase 13 deliverable, since it's the shared entry point all
  three wizards hang off.
- **Pre-plot wizard (14)** also gets a `Pre-flight…` button right beside
  Start/Pause/Stop, since it's the only one of the three run on *every*
  job — it earns main-screen real estate; the other two don't. A Settings
  toggle ("Run pre-plot checklist before plotting," default on for fresh
  installs) optionally routes the existing `Start` button through it, so
  beginners get guard rails by default while `Start` still plots directly
  for anyone who turns the toggle off — never a second code path, the
  wizard calls the exact same `PlotService` methods.
- **Setup wizard (15)** and **Calibrate wizard (16)** are once-per-machine/
  diagnostic, not every-job, so they don't get permanent canvas buttons —
  they live in the `Machine` menu and as launcher buttons at the top of
  `SettingsPanel` ("Run Setup Wizard…" / "Calibrate Axes…"), since anyone
  who opened Settings to fix geometry is exactly who wants the guided path
  to the same fields. Setup additionally gets a non-blocking first-run
  prompt ("New machine? Run the Setup Wizard.") when `config.json` is
  missing or all-default, so the people who most need it don't have to go
  hunting for a menu item.
- Expert paths are never removed: Connect/Home/Start/Settings keep working
  exactly as they do today, with or without any wizard ever being opened.

---

### Phase 13 — Guided workflow infrastructure ✅

**Problem.** Each of Phases 14–16 is a multi-step, stateful, "walk the user
through N things in order, let them go back, let them bail out" flow.
Building three of those as independent one-off `JDialog`s would mean writing
the same Back/Next/progress-trail/cancel-confirmation plumbing three times
(`SvgImportDialog`/`EditProcessDialog`'s tab pattern doesn't fit — those are
flat tabs with no ordering or step validation, not a sequence).

**Goal.** One small, reusable `WizardDialog` (or similarly named) component
that owns step navigation, a progress trail ("Step 2 of 5"), Back/Next/
Skip/Cancel, and per-step validation (can't advance until the step's
precondition is met) — with zero plotting/hardware knowledge of its own.
Each concrete wizard (14/15/16) supplies a list of step panels plus
validators; the shell just sequences them.

**Scope:**
- `WizardDialog` (shell): renders the current step's panel, a left-side or
  top progress trail, and Back/Next/Skip/Cancel wired to a `WizardStep`
  interface (`JComponent panel()`, `boolean canAdvance()`, `void onEnter()`/
  `onLeave()` hooks for steps that need to kick off an action — e.g. "send
  $H and wait" — when shown).
- A step can mark itself **optional** (shows Skip) or **gating** (Next
  disabled until `canAdvance()` is true — e.g. "click Connect" or "enter the
  measured distance").
- No persistence, no hardware calls, no plot-domain logic in this layer —
  keep it generic so it's trivially unit-testable without a serial port or
  Swing event thread tricks beyond what `WizardDialogTest` needs.
- **New top-level `Machine` menu** in `PlotterPanel.buildMenuBar()`, between
  `Edit` and `Settings` — the shared entry point all three wizards hang off.
  Moves `Connect`/`Disconnect` and `Home` in as proper menu items (today
  they exist only as canvas buttons with no menu/keyboard path), and adds
  `Setup Wizard…`, `Calibrate Axes…`, and `Pre-Plot Checklist…` items that
  launch the wizards built in Phases 14–16.

**Exit criteria.** A throwaway 2-step demo wizard (e.g. "type your name" →
"confirm") can be assembled from the shared component in under an hour, with
a test covering: Next disabled until `canAdvance()`, Skip bypasses a step
marked optional, Cancel closes without side effects, Back returns to a
step's previously-entered state.

**Risks:** over-engineering a generic framework before a second concrete use
case exists to validate the abstraction. *Mitigation:* build this phase
*alongside* Phase 14 (the first concrete consumer), not in isolation —
let the pre-plot wizard's real needs shape the interface instead of
guessing it up front.

---

### Phase 14 — Pre-plot wizard ✅

**Problem.** Before every plot, an operator silently runs through a mental
checklist that Gantry has no idea exists: is the plotter connected and
homed, is the right pen physically in the holder and lowered/raised
correctly for the configured pen mode, is the paper taped down where the
machine thinks the origin is, is the layer selection actually what's
intended for this run? Today, getting any of these wrong just produces a
ruined plot — there's no machine-assisted way to verify any of them, and
"frame the job" (tracing the bounding box pen-up before committing) doesn't
exist at all.

**Goal.** An optional, fully skippable step-by-step flow that takes an
operator from "plotter connected" to "plot started" with verification at
each physical handoff point, built on Phase 13's `WizardDialog`.

**Scope — steps (in order, each individually skippable):**
1. **Connect** — gating step; if already connected, auto-advances.
2. **Home** — runs the existing `backend.home()` (`GcodeBackend.java:254`);
   gating on success, surfaces the existing error path if homing fails.
3. **Frame the job** — pen-up move around the bounding box of the composed
   output (reusing `visPanel.getRawBounds()` and the existing
   `CoordinateTransform`), repeatable ("Trace again") so the operator can
   re-check after adjusting paper; optional/skippable, since not every job
   needs the physical check (e.g. a re-run of a job already verified once).
4. **Physical checklist** — a plain checklist panel, *not* machine-verified
   (Gantry has no sensor for any of this): "Correct pen installed for the
   configured pen mode," "Pen lowered/raised correctly" (text adapts to the
   configured `penMode` — servo angle vs. Z-axis lift — so the prompt
   matches what the operator should actually see happen), "Paper taped down
   at the framed area," "Correct layers selected" (echoes the current
   Layers checklist state from `PlotterPanel` so it's a confirmation, not a
   duplicate control).
5. **Confirm & start** — summary of layers/passes/estimated time (reusing
   `TimeEstimator`), then calls the existing `Start` action.

**Out of scope:** anything that requires new hardware sensing (e.g.
detecting whether the pen is *actually* lowered) — the checklist step is
explicitly operator-attested, not machine-verified, and should say so rather
than implying a guarantee it can't make.

**Risks:**
- Must not become a second code path for connect/home/start — every step
  should call the *exact same* `PlotService`/`PlotterPanel` methods the
  non-wizard buttons call, so behaviour (and bugs) stay in one place.
- "Frame the job" needs a safe travel speed and to stay within
  `machineWidth`/`machineHeight` — reuse whatever bounds-clamping logic
  already protects jogging, don't write new clamping logic for this one
  feature.

**Implementation note.** Shipped as `WizardDialog`/`WizardStep`
(`app/.../gui/`) plus five `PreflightXxxStep` inner classes in
`PlotterPanel`, launched from `Machine > Pre-Plot Checklist…`. Verified
live end-to-end against the mock backend: Connect polls for the async
connect to finish, Home reuses the existing confirm dialog, Frame the job
sends a real pen-up rectangle via `backend.moveto`, the checklist gates on
all four boxes, and Finish hands off to the existing `onStartPlot()` (the
toolbar's Start button does the actual plotting — confirmed by watching the
status banner switch to "Plotting..." after Finish). "Frame the job" now
runs the selected layers through the same transform/alignment pipeline the
plot uses (`PlotService.computeFrameBounds`, sharing the corner-transform
helper with the existing pre-flight bounds check) and soft-clamps each
corner to the bed, so the trace can never command the head outside the
machine even when the drawing overhangs — verified against a deliberately
overhanging A4-fit job, where the raw corners (Y up to 233.5 on a 200mm
bed) were clamped to a rectangle fully inside the bed.

A `Pre-flight…` button now sits beside `Start`/`Stop` in the main Plot
section (every-job real estate, as this GUI/UX note originally called
for), and a new "Run Pre-Plot Checklist before Start" setting (default
on, in Settings > Connection) routes the existing `Start` button through
the wizard automatically; turning it off restores the direct one-click
Start behaviour from before this phase.

---

### Phase 15 — Machine setup wizard (first run) ✅

**Problem.** `SettingsPanel` (`SettingsPanel.java:17-369`) is a single long
scrollable form covering connection, machine geometry, orientation, pen
mode/speeds, and refill stations — accurate and complete, but it assumes the
user already knows what every field means and in what order to fill them
in. There is no first-run flow at all: a brand-new install just opens
straight to the main window with default `config.json` values, and a
new machine owner has to discover Settings and work out the right order
themselves (the `docs/USER_GUIDE.md` "First start" section currently carries
that burden in prose).

**Goal.** A guided first-run flow, built on Phase 13, that walks the same
underlying `GantryConfig`/`GcodeOptions` fields `SettingsPanel` already
edits, in a deliberate order, with live feedback where it helps (e.g. jog
buttons right there at the geometry step so width/height can be sanity
checked against real machine movement, not just typed in blind).

**Scope — steps (in order):**
1. **Connection** — port, baud, mock-backend toggle; same fields as
   `SettingsPanel`'s Connection section, gating on a successful test
   connect.
2. **Machine geometry** — width/height, with inline jog buttons (reusing
   `PlotterPanel`'s existing jog action) so the operator can move to a
   physical edge and confirm the entered dimension matches reality, instead
   of the wizard *asserting* a number it can't verify on its own.
3. **Origin & orientation** — machine origin corner, landscape/portrait,
   axis invert/swap — presented with a live preview of "this is where (0,0)
   will be and which way +X/+Y point" (reusing `VisualizationPanel`'s
   existing bed-outline rendering, not a new preview widget).
4. **Pen mode & speeds** — servo vs. Z-axis vs. M3/M5, the mode-specific
   sub-fields, draw/travel feed rates, pen-down delay.
5. **Done** — writes the same `config.json` via the existing `ConfigStore`;
   from here on `SettingsPanel` remains the tool for *editing* any of this
   later. The wizard is an onboarding path, not a replacement UI.

**Out of scope:** refill-station setup (Settings' Refill Stations table) —
that's watercolor-specific and already has its own dedicated UI; folding it
into a generic "first run" flow would force every plain pen-plotter owner
through watercolor questions that don't apply to them.

**Risks:** duplicating `SettingsPanel`'s field logic instead of reusing it.
*Mitigation:* each wizard step should embed the *same* sub-panel
`SettingsPanel` uses for that section (extracting them into shared
components if they're not already separable) rather than re-implementing
spinners/combos a second time — same lesson as Phase 6's
`ToolboxOptionsPanel` extraction.

**Implementation note.** Built exactly to the mitigation above:
`SettingsPanel` now keeps its four section panels as fields with
package-private accessors (`connectionPanel()`/`geometryPanel()`/
`penPanel()`), and `onSetupWizard()` re-parents those *same* live panels
into `PanelStep`s of a `WizardDialog` (Welcome → Connection → Geometry &
origin → Pen & speeds → Done). There is exactly one set of settings
widgets; on Finish the wizard calls the same `toConfig()`/`ConfigStore.save`
/`applyConfigToVis()` path as the all-in-one dialog. A first-run
auto-prompt (fires when `config.json` is absent) offers the wizard on a
fresh install, and a "Run Setup Wizard…" button at the top of
`Settings > Preferences…` hands off to it (closing the all-at-once dialog
first so the two never fight to save). Verified live end-to-end against the
mock backend: each step shows the real fields, changing Machine Width to
350 in the Geometry step and clicking Finish updated the saved
`config.json` and the live "Bed: 350x200" status banner. Not yet wired: the
*inline jog buttons* at the geometry step and the *live origin/orientation
preview* the scope describes — the wizard currently points the operator at
the main-window Jog pad and live bed outline instead of embedding copies;
embedding those is a worthwhile follow-up but not required for the
core onboarding flow.

---

### Phase 16 — Axis calibration wizard ✅

**Problem.** Gantry has no machine-direction sanity check beyond trusting
the `invertX`/`invertY`/`swapXY` settings the operator typed in, and no
concept of physical scale calibration at all — `GcodeOptions` has no
steps-per-mm field, and `GcodeBackend` never reads or writes GRBL's `$$`
settings (confirmed: no `$100`/`$101`/`$102` read or write path exists
today). If a machine's steps/mm is slightly off out of the box (a commanded
200 mm move actually measuring 195 mm), or an axis is wired backwards, the
only way to find out today is a ruined plot.

**Goal.** Two guided, independent checks an operator can run without
reading GRBL documentation:
1. **Direction check** — confirm "+X on screen" matches "+X on the table"
   (and same for Y), surfacing a one-click fix into the existing
   `invertX`/`invertY`/`swapXY` settings rather than requiring the operator
   to edit GRBL settings directly.
2. **Scale calibration** — command a known travel distance, let the
   operator measure the actual physical distance with a ruler and enter it,
   compute the corrected steps/mm, and preview the resulting `$100=`/
   `$101=` value before sending it to the controller.

**Scope — direction check:**
- Jog a small, fixed distance (e.g. 20 mm) on one axis at a time; ask "did
  the pen move toward you or away?" (plain-language, not "+X or -X," since
  that's exactly the confusion this step exists to resolve).
- If the answer doesn't match the configured `invertX`/`Y`, offer to flip
  the corresponding setting in `GantryConfig` directly — this is pure
  software-side correction (today's existing inversion flags), no GRBL
  write involved, so it's low-risk and reuses an existing mechanism.

**Scope — scale calibration:**
- Command a deliberately large, known move (e.g. 200 mm) at a safe travel
  speed; prompt the operator to measure the actual distance moved with a
  ruler/tape and type in what they measured.
- Compute `correctedStepsPerMm = currentStepsPerMm × (commandedMm /
  measuredMm)`. This needs `GcodeBackend` to gain the ability to (a) read
  the controller's current `$100`/`$101` via `$$` (parse the response,
  which it has never had to do before — new parsing logic, not just a new
  command) and (b) send a `$100=<value>` write — both genuinely new
  plumbing, not wiring onto something that already exists.
- Preview the computed value and require an explicit confirm before
  writing it to the controller — a wrong write here silently changes how
  every future move is scaled, so this should never be a silent autocorrect.
- Repeat per axis (X and Y independently, since belt stretch/pulley
  mismatches are rarely identical on both).

**Out of scope:** Z-axis steps/mm calibration (servo-mode and most Z-lift
setups don't need it the way X/Y travel does); skew/orthogonality
calibration (are X and Y perfectly perpendicular) — a real but much rarer
problem, and a different, harder calibration entirely.

**Risks:**
- This is the riskiest of the three wizards because it's the only one that
  *writes to the controller's persistent settings* (`$100=`/`$101=` survive
  power cycles) rather than just to Gantry's own `config.json` — a mistake
  here outlives the Gantry session. *Mitigation:* always show the current
  value, the computed new value, and require explicit confirmation; log the
  old value so an operator can manually revert via `$100=<old value>` if the
  computed correction was wrong (e.g. mismeasurement).
- Needs new GRBL-response parsing (`$$` settings dump) that nothing in
  `GcodeBackend` does today — should land with its own focused unit tests
  against canned GRBL `$$` output before any wizard UI is built on top of it,
  same sequencing lesson as Phase 9's "data-model-first, GUI-second."

**Implementation note.** The `$$`-parsing lived up to the "data-model-first"
note: it's a standalone `GrblSettings` helper (`plotter/`) with
`findSetting`/`writeCommand`/`correctedStepsPerMm`, built on the *existing*
`PlotterBackend.sendRaw` (which already collects multi-line GRBL responses
until `ok`/`error`) — no new backend interface method was needed. The wizard
(`Machine > Calibrate Axes…`) is Intro → Direction check → X-axis scale →
Y-axis scale → Done. The direction step jogs via the existing `jog()` action
and flips `config.invertX`/`invertY` (pure software, applied on Finish); the
scale steps read the current `$10x` on entry, command a known move, take the
measured distance, preview `corrected = current × commanded/measured`, and
write `$10x=` only on explicit button press (the old value stays logged for
manual revert). To make all of this verifiable headless, `MockPlotterBackend`
now emulates GRBL: `$$` reports simulated `$100`/`$101` and `$100=`/`$101=`
writes update them. Verified live end-to-end against the mock: entering the
X step read `$100=80.000`; with commanded 100 / measured 95 the preview
computed `84.211`; clicking Write sent `$100=84.211`, the mock applied it,
and the re-read confirmed the new value; ticking "flip X" persisted
`invertX:true` to `config.json` on Finish. Not done: the live origin/
orientation preview is shared with Phase 15's deferral; Z-axis and
orthogonality calibration remain out of scope as planned.

---

### Phase 17 — Visual station placement + watercolor test-run ✅

**Problem.** Refill stations are positioned today by typing raw mm `x`/`y`
into the "Refill Stations" `JTable` in `SettingsPanel` and guessing — there
is no visual feedback that the numbers put the brush over the actual pot, and
no way to verify it short of starting a real watercolor plot (the #1 way such
a plot gets ruined: the brush dips into the wrong pot, or misses the pot and
the rim). The stations are *already drawn* on `VisualizationPanel` as labelled
dots (`physicalToScreen(station.x, station.y)`), and the canvas already has a
full mouse drag + hit-test + handle infrastructure (used to reposition the
drawing overlay) — but stations were display-only, not interactive.

**Goal — two reinforcing halves over the same `StationConfig` backing data,
never a second coordinate representation:**

1. **Half A — visual placement (design-time, no hardware).** Drag a station
   dot on the canvas to reposition it; right-click empty bed → "Add station
   here" drops a new station at that mm coordinate. The `SettingsPanel`
   station table stays the authoritative editor and live-syncs both ways
   (drag the dot → table row updates; edit the row → dot moves). Both read/
   write the one shared `config.stations` map.
2. **Half B — watercolor test-run wizard (`Machine > Test Color Stations…`).**
   Built on the existing `WizardDialog`/`WizardStep`/`PanelStep` shell. For
   each selected station: pen-up dry visit (`penup()` → `moveto(x, y)`) with a
   "brush is over *station N (red)* — does it line up?" gate, an optional wet
   test that runs the station's *real* refill behaviour (`pendown(zDown)`,
   dwell `dwellMs`, swirl `swirlRadius` for `dip_swirl`/`rinse`, lift), and
   jog-to-nudge that writes the corrected x/y straight back into the same
   `StationConfig`. Reuses the exact backend primitives the real refill path
   uses — a guided ordering, never a second code path.

**Scope — Half A:**
- Extend `VisualizationPanel`'s `hitTestHandle` to also hit-test the station
  markers; on drag, convert screen→mm via the absolute inverse of
  `physicalToScreen` (a straightforward affine invert; the panel already has
  `screenDeltaToMm` for deltas) and fire a change event back to `SettingsPanel`.
- Right-click context menu (already present on the canvas) gains "Add station
  here," which inserts a `StationConfig` at the clicked mm position.
- `SettingsPanel` and the canvas sync through the shared station map — no
  third copy of the coordinates.

**Scope — Half B:**
- "Pick stations" step (default all configured), a per-station dry-visit step
  (pen-up move + confirm/nudge), an optional wet-test step, and a save-on-
  finish summary that persists any nudged coordinates.
- Headless-verifiable: `MockPlotterBackend` already emulates `moveto`/
  `penup`/`pendown`, so the whole wizard drives end-to-end with no plotter.

**Out of scope:** automatic pot detection (camera/vision); Z-only "dip depth"
calibration beyond what the wet test surfaces; multi-brush carousels.

**Sequencing.** Half A lands first (pure GUI, headless-verifiable, no
hardware), Half B on top — same "make the data interactive, then drive it"
ordering as Phases 15→16.

---

### Phase 18 — Raster vectorization (image → SVG front stage) 🚧 NOT STARTED

**Problem.** Gantry starts from an SVG. Everything upstream of that — turning a
photo, scan, logo or sketch into vector paths — happens in a *separate* tool,
the standalone **Vectorize** project (BoofCV-Batik Vectorizer). A user who has
a raster image today must run Vectorize by hand, save an SVG, then come back to
Gantry and import it. The two tools already share the exact same stack
(Java 17 · Maven · Swing/FlatLaf · Apache Batik) and meet at a clean data
boundary: **Vectorize's output (SVG) is precisely Gantry's input.** Vectorize
is the missing *front* stage of the pipeline, not a competing one.

**Decisions taken (from the integration analysis):**
- **Integration depth — new module.** Port Vectorize as a self-contained
  `vectorize` Maven module that emits an SVG; that SVG is fed into the existing
  `SvgImportStage`. Gantry's core (`svgtoolbox-core`, `pipeline-core`,
  `plotter`) is **untouched** — the boundary stays "a generated SVG," not a new
  internal coordinate model.
- **The Vectorize repo stays untouched.** The port is a one-way *copy* of the
  Vectorize source into Gantry (re-homed to `org.trostheide.gantry.vectorize`);
  no changes are pushed to, and no git history is rewritten in, the standalone
  Vectorize repository. It continues to live and evolve on its own.
- **Licensing — adopt AGPLv3 for Gantry.** Vectorize is AGPLv3 and Gantry
  currently has no `LICENSE` file. The combined project becomes AGPLv3: add an
  AGPLv3 `LICENSE` at the Gantry root and the corresponding notices.
- **DrPTrace — whatever's cleanest.** The `bezier` strategy depends on the
  vendored DrPTrace JARs (`lib/net.plantabyte.drptrace-2.0.0.jar`, today wired
  via Maven `system` scope, which a multi-module reactor + fat-jar assembly
  does *not* bundle). Preferred: make those JARs reproducibly available to the
  build (install into the local repo / repackage) so the `bezier` strategy
  survives in the shipped artifact. Fallback, if that fights the reactor build:
  drop the single `bezier` strategy and ship the other seven. No silent loss —
  the outcome is recorded here when the phase lands.

**Goal — image → SVG → process → plot, in one tool, with Gantry's core
unchanged.** Two reinforcing halves, same "build the engine, then drive it"
ordering as Phases 13→14 and 15→16.

1. **Half A — `vectorize` module + CLI (headless, no GUI).** Copy in the
   Vectorize engine and strategies, re-home the package/groupId, reconcile
   dependencies, and expose a headless `image → SVG` converter that chains into
   the existing `SvgImportCli`.
2. **Half B — GUI integration.** A `PlotterPanel` "Import Image…" action that
   runs vectorization, writes an SVG, and hands it straight to the existing
   SVG-import path (the same path "Import SVG…" already uses). A focused
   controls dialog — not the whole standalone Vectorize GUI — exposing strategy
   choice and the core parameters.

**Scope — Half A:**
- New `vectorize` module added to the parent `<modules>` list, *before*
  `svgtoolbox-core` in the dependency order. Copy `BoofcvBatikVector`, the
  `VectorizationStrategy` interface and its eight strategies (`dp`, `line`,
  `raw`, `convexhull`, `bezier`, `bezier2`, `centerline`, `pbn`), the
  `algorithms` helpers (`CurveFitter`, `SkeletonTracer`), Paint-by-Numbers,
  auto-Canny, and the SVG/export utilities. Re-home `org.trostheide.vectorizer`
  → `org.trostheide.gantry.vectorize`; groupId `com.structuredexplorer` →
  `org.trostheide.gantry`.
- Add BoofCV 0.44 + georegression + imagetracerjava (and the DrPTrace handling
  above) to the parent `dependencyManagement`. Align the shared deps to
  Gantry's versions: Batik 1.17 → **1.19**, commons-cli 1.5 → **1.6** (both
  trivial). Keep the BoofCV footprint confined to this module so removing
  `vectorize/` still leaves a fully functional plotter (same priority encoding
  as `watercolor/`).
- A headless entry point (`cli`): `image → SVG`, reusing Vectorize's strategy
  options, that either writes an SVG or chains directly into `SvgImportCli` so
  one command goes image → plottable command model.

**Scope — Half B:**
- `PlotterPanel` gains an "Import Image…" action alongside "Import SVG…". It
  opens a vectorize dialog (strategy + key parameters, Batik live preview as in
  the standalone tool if cheap to reuse), produces an SVG, then routes it
  through the *existing* `SvgImportDialog`/`SvgImportStage` flow — so the
  toolbox-processing tab, scaling, refill, etc. all apply unchanged.
- Presets carried over where they map cleanly (Line Art, Logo, Sketch, …).

**Out of scope:** porting the entire standalone Vectorize Swing application
(menus, snapshot/undo stack, theme toggling) verbatim — Gantry already owns the
window chrome; only the vectorization controls are needed. Live re-vectorize on
every slider tick beyond a simple debounced preview. Any change to the
downstream pipeline — the SVG boundary is deliberately the seam.

**Sequencing.** Half A lands first (module + CLI, headless-verifiable on the
`testimages`/`testdata` samples, no GUI), Half B on top. License + dependency
reconciliation is part of Half A's exit criteria so the reactor build stays
green from the first commit.

**Status — Half A.**
- ✅ `vectorize` module scaffolded; all Vectorize source ported and re-homed
  `org.trostheide.vectorizer` → `org.trostheide.gantry.vectorize`; parent
  `<modules>` + `dependencyManagement` wired (BoofCV 0.44, georegression,
  org.json, drptrace). Batik aligned to 1.19, commons-cli to 1.6. DrPTrace JARs
  vendored under `vectorize/lib/` (system scope).
- ✅ AGPLv3 `LICENSE` added at the Gantry root.
- ✅ **ImageTracer (`bezier2`) dependency resolved without JitPack.** The fork
  `com.github.brixomatic:imagetracerjava` is JitPack-only and unreachable from
  restricted/CI networks, so the upstream public-domain (The Unlicense) source
  file `jankovicsandras/imagetracer/ImageTracer.java` is vendored in-tree
  instead. The fork-only `getPalette()`/`SVGUtils` calls in `BoofcvBatikVector`
  were mapped onto the upstream `imageToSVG(image, options, null)` entry point
  (it builds the palette internally), keeping full `bezier2` parity with no
  external repository. All eight strategies retained.
- ✅ **Module builds green in the reactor** — `mvn -pl vectorize -am test`:
  86/86 tests pass on the `testimages/` fixtures (copied in). Full reactor
  `mvn install` is green across all nine modules.
- ✅ **CLI `image → SVG [→ commands]` wired** — `cli/VectorizeCli` is a thin
  front controller that delegates the image→SVG step to the `vectorize` CLI and,
  when arguments contain a `--` separator, chains the produced SVG into
  `SvgImportCli` (injecting it as `-i`) so one command goes image → command
  JSON. No options duplicated. Verified end-to-end on `test_circles.png`
  (image → SVG → 1-layer/18-command JSON).

**Status — Half B.**
- ✅ **GUI "Import Image (vectorize)…" wired** — new `File` menu item in
  `PlotterPanel` (Ctrl+Shift+I). Flow: pick a raster image → focused
  `VectorizeDialog` (strategy picker + the core parameters, controls enabled per
  strategy) → the image is traced to a temporary SVG via the non-exiting
  `Main.runSingleFile(String[])` entry → that SVG enters through the **same**
  `SvgImportDialog`/`SvgImportStage` path as "Import SVG", so scaling, refill and
  SVGToolBox processing all apply unchanged. The generated SVG is the only seam.
- ✅ `vectorize` added as an `app` dependency; `Main.runSingleFile` exposed as a
  `System.exit`-free programmatic entry for embedders.
- ✅ Reactor `mvn clean install` green across all nine modules; the GUI engine
  path (`runSingleFile` → `importSvg`) headless-verified end-to-end on the
  `dp`, `centerline` and `pbn` strategies (valid SVG → command model each).

**Status — packaging.**
- ✅ **DrPTrace bundles into the fat jars.** The vendored `drptrace` /
  `drptrace-utils` jars moved from `system`-scoped paths under `vectorize/lib/`
  into an in-project Maven repository at `<root>/maven-repo` (the `gantry-local`
  `file://` repository in the root `pom.xml`, keyed off the reactor-root
  property), and are now ordinary `compile` dependencies. The shade plugin
  therefore bundles them into both the `cli` and `app` fat jars (33 classes
  each). Verified by running the `bezier` strategy from the `cli` fat jar alone
  (no reactor classpath): 10 paths traced, valid SVG written.

**Phase 18 is complete.** All eight strategies — including `bezier` (DrPTrace)
and `bezier2` (vendored ImageTracer) — work from the distributed `cli`/`app`
artifacts. Reactor `mvn clean install` is green across all nine modules and the
86 `vectorize` tests pass. Image → SVG → process → plot is available headless
(CLI) and in the GUI (`File ▸ Import Image (vectorize)…`).

---

### Phase 19 — Vectorize live-preview studio (best-UX raster tuning) 🚧 NOT STARTED

**Problem.** Phase 18 made image→SVG *possible*, but the UX is "tune blind": two
sequential modal dialogs (`VectorizeDialog` → `SvgImportDialog`) with **no
preview**. You pick a strategy and parameters, click through, and only see the
trace *after* it has been imported onto the canvas — and if it's wrong, you
start over. Vectorization is inherently iterative: you nudge tolerance / colour
count / Canny thresholds and re-trace until it looks right. The standalone
Vectorize tool solved this with a live `JSVGCanvas` preview + debounced
re-processing; Gantry's port dropped that and kept only the parameter form.

The single highest-value improvement is therefore a **live preview** — see the
result update as you tune. For an explore-and-adjust task this argues for a
*workspace* (stay on one surface and iterate), not a linear wizard (Back/Next
fights the loop). Presets provide the guided on-ramp instead.

**Goal — a dedicated "Vectorize" workspace where you tune against a live preview,
judge plottability on screen, then hand off to the existing pipeline unchanged.**

**Scope — Tier 1: the live-preview tuning surface** (replaces `VectorizeDialog`):
- **Side-by-side**: source image (left, zoom/pan) and vector preview (right) via
  Batik `JSVGCanvas`; synchronised zoom; an overlay/toggle to compare trace
  against source. (`batik-swing` is already on the classpath via the `vectorize`
  module.)
- **Debounced background re-trace**: any control change schedules a trace on a
  cancellable `SwingWorker` (port/adapt the standalone tool's
  `VectorizationWorker`), cancelling any in-flight run; the UI never blocks.
  Trace a downscaled/ROI image while tuning, full resolution on commit.
- **Preset-first controls**: a preset row (Line art · Photo-detailed ·
  Photo-simplified · Logo · Sketch · Paint-by-Numbers · Centerline-for-plotting)
  sets strategy + sensible parameters; advanced, strategy-aware parameters are
  revealed progressively. Auto-Canny and colour-aware-edges toggles.
- **ROI crop** directly on the source preview (reuses the engine's `--crop`).

**Scope — Tier 2: plotter-aware readouts** (what makes Gantry's better than a
generic tracer — it's a *plotter* front end):
- Live metrics under the preview: path count, point/segment count, colour count,
  and plot-relevant numbers — estimated pen-down strokes and rough pen-travel,
  single-stroke (centerline) vs double-stroked outline.
- A gentle warning when a strategy yields doubled outlines that waste pen travel,
  nudging toward `centerline` for line work.
- Optional **"as it will plot"** preview: render the vector the way the plotter
  draws it (stroke order/travel) rather than as filled shapes, and surface a
  rough plot-time/complexity hint *before* import.

**Scope — Tier 3: hand-off and re-tune:**
- **Import** applies fit-to / refill / curve-step / toolbox (reuse the
  `SvgImportDialog` panel content as a second tab) and feeds the existing
  `SvgImportStage` → command model, completely unchanged.
- **Remember source image + parameters** with the imported drawing so a
  `Re-vectorize…` action (analogous to today's *Re-process Source SVG*) reopens
  the studio pre-populated to re-tune without re-importing from scratch.

**Out of scope:** a fully docked, non-modal workspace fused into the main canvas
(this phase is a dedicated modal studio, consistent with `SettingsPanel`); manual
per-path editing of the traced result; GPU acceleration; batch/folder
vectorization in the GUI (the CLI already does batch). The linear *wizard*
framing is explicitly rejected for the tuning surface — guidance comes from
presets, not Back/Next — though the shell may still bookend the workspace with a
choose-image entry and a confirm/import step.

**Sequencing.** Tier 1 (live preview + presets) is the core value and ships
first, replacing `VectorizeDialog` while keeping the engine and import plumbing
from Phase 18. Tier 2 (plotter-aware readouts / plot-order preview) and Tier 3
(re-vectorize round-trip) layer on top. Headless behaviour and the CLI are
unchanged; this phase is GUI-only.

**Reuse.** The standalone Vectorize project already implements the two hard
parts — `VectorizationWorker` (background trace with cancel + progress) and a
`JSVGCanvas` live preview with debounced updates — so Tier 1 is largely a
port/adapt of proven code rather than a green-field build.

**Status — Tier 1 implemented (GUI verification pending).**
- ✅ `app/gui/VectorizeStudioDialog` — source `ImagePanel` and a `JSVGCanvas`
  vector preview side by side; a 400 ms debounced, cancellable `SwingWorker`
  re-traces via `Main.runSingleFile` on any control change; preset row
  (Line art / Sketch / Centerline / Logo / Photo ×2 / Paint by Numbers),
  strategy-aware parameters, crop-region toggle, and a `strategy · N path(s)`
  readout. Returns the same `Result` shape, so `PlotterPanel.onImportImage` just
  swaps the dialog and the rest of the import flow is unchanged. The blind
  `VectorizeDialog` is removed; `batik-swing` added to the `app` module.
- ✅ Reactor build green; `app`/`cli` tests pass; the engine path it drives
  (`Main.runSingleFile`) is covered by `VectorizeCliTest`.

**Status — Tier 2 (plotter-aware readouts) implemented.**
- ✅ `StudioMetrics` computes layers / strokes / points and, crucially, a
  **scale-invariant travel ratio** (pen-up travel ÷ total motion) from the
  command model the trace imports to — the studio shows
  `strategy · N layer(s) · M strokes · P pts · X% travel`, computed in the
  background worker after each trace. Covered by `StudioMetricsTest` (4 tests).
- ✅ A data-driven plottability **hint**: centerline is praised as single-stroke;
  a trace with >50% pen-up travel is nudged toward Centerline / fewer colours.
- ⏳ The "as it will plot" stroke-order preview is deferred (heavier, pure GUI).

**Status — Tier 3 (re-vectorize round-trip) implemented.**
- ✅ `PlotterPanel` remembers the source image + vectorize args on import and
  enables **Edit ▸ Re-vectorize Image…**, which reopens the studio on the same
  image pre-populated with those parameters (`VectorizeStudioDialog.applyArgs`,
  the inverse of `buildParams`) so the trace can be re-tuned without
  re-importing. The import flow is refactored into a shared `vectorizeAndImport`.

**Verification.** Reactor build green; `app` tests pass (22, incl. the new
`StudioMetrics` math). The live GUI surface across all three tiers — preview
updates, debounce, presets, crop, the readouts/hint, and the re-vectorize
round-trip — needs a manual run (TESTING.md **TS-U1**/**TS-U2**) before Phase 19
is marked done; it cannot be exercised headlessly.

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
- **Vectorize** (BoofCV-Batik Vectorizer, https://github.com/utrost/vectorize) —
  Java 17 + Maven + Batik + BoofCV, raster image (JPG/PNG) → SVG via edge
  detection and multiple tracing strategies (Canny/contour, centerline,
  whole-image Bézier, Paint-by-Numbers). AGPLv3. To be absorbed as the
  `vectorize` front-stage module (Phase 18) by copying its source into Gantry;
  the standalone Vectorize repository stays untouched and continues on its own.
