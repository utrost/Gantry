# Gantry — Architecture & Code Reference

> Audience: an AI/LLM (or engineer) that needs to reason about, modify, or extend
> this codebase without reading every file first. This document describes the
> module graph, data model, control/data flow, threading model, key invariants,
> and extension points. It is intended to be read top-to-bottom once, then used
> as a lookup. File references use `module:path` and `Class#method` notation.

---

## 1. What Gantry is

Gantry is an all-Java (Java 17, Maven multi-module) toolkit that converts SVG
artwork into pen-plotter motion and drives the plotter directly. The end-to-end
flow is:

```
SVG file ──▶ [import + optional SVG→SVG preprocessing] ──▶ ProcessorOutput (command model)
ProcessorOutput ──▶ [optimize / multipass / station-mapping] ──▶ ProcessorOutput
ProcessorOutput ──▶ [plot] ──▶ PlotterBackend ──▶ G-code over serial  (or .gcode file)
```

`ProcessorOutput` (an in-memory, JSON-serializable command model) is the **central
interchange type**. Almost every stage is a pure function
`ProcessorOutput → ProcessorOutput`, which makes the pipeline easy to test
headlessly and to reason about. Pen plotting is the first-class default path;
watercolor (paint-station mapping + brush refill) is an optional stage layered on
top.

There are two entry points that share the same core:
- **`app`** — a Swing/FlatLaf desktop GUI (`GantryApp` → `PlotterPanel`).
- **`cli`** — a headless batch converter (`SvgImportCli`).

---

## 2. Module graph

Maven modules, declared in the root `pom.xml`, with a strictly one-directional
dependency graph (no cycles). Arrows mean "depends on".

```
            ┌─────────────────────────────────────────────┐
            │                    app                       │  Swing GUI + orchestration
            └───┬───────┬───────────┬──────────┬───────────┘
                │       │           │          │
                ▼       ▼           ▼          ▼
           pipeline-  plotter   watercolor   model
            core         │          │          ▲
              │          └──────────┴──────────┘
              ▼
        svgtoolbox-core ───▶ model
              │
              ▼
            model

   cli ──▶ pipeline-core (+ svgtoolbox-core, model)
   cli, app ──▶ vectorize     (optional raster→SVG front stage; no other internal deps)
```

| Module | Package root | Responsibility | Key types |
|---|---|---|---|
| `model` | `org.trostheide.gantry.model` | Shared DTOs and the coordinate-transform math. No logic dependencies. | `ProcessorOutput`, `Layer`, `Command` (+ subtypes), `Point`, `Bounds`, `Metadata`, `CoordinateTransform` |
| `svgtoolbox-core` | `…svgtoolbox` | SVG→SVG DOM processors (operate on a Batik `org.w3c.dom.Document` in place): hatch, palette, crop, rotate, simplify, line-merge/sort, etc. | `SvgToolboxPipeline`, `Processor`, `Config`, `HatchStyle`, `processors/*`, `patterns/*` |
| `pipeline-core` | `…pipeline` | SVG→command-model import and command-model→command-model transforms. | `SvgImportStage`, `SvgImportOptions`, `PaperFormat`, `OptimizeStage`, `MultipassStage`, `PathOptimizer`, `RamerDouglasPeucker`, `ProcessorOutputIO` |
| `watercolor` | `…watercolor` | Optional colour→paint-station assignment. | `StationMapper`, `PaintStation`, `ColorUtil` |
| `plotter` | `…plotter` | Pluggable plotter backends + G-code formatting/replay/serial transport. | `PlotterBackend` (interface), `GcodeBackend`, `MockPlotterBackend`, `GcodeFileBackend`, `GcodeFormatter`, `GcodeFileReplay`, `SerialTransport`/`JSerialCommTransport` |
| `vectorize` | `…vectorize` | Optional front stage: raster image (JPG/PNG)→SVG via BoofCV edge detection + multiple tracing strategies. Emits an SVG consumed by `SvgImportStage`; depends on no other Gantry module. DrPTrace (in-project `maven-repo`) and the vendored public-domain ImageTracer (`jankovicsandras/imagetracer`) power the two whole-image tracers. | `BoofcvBatikVector`, `Main#runSingleFile`, `VectorizationStrategy` (+ `strategies/*`), `PaintByNumbersProcessor` |
| `cli` | `…cli` | Headless SVG→JSON converter, plus image→SVG[→JSON] vectorization. | `SvgImportCli`, `VectorizeCli` |
| `app` | `…app` | Swing GUI, plot orchestration, settings persistence, live visualization. | `GantryApp`, `gui/PlotterPanel`, `gui/VisualizationPanel`, `gui/SvgImportDialog`, `gui/VectorizeDialog`, `gui/EditProcessDialog`, `gui/SettingsPanel`, `plot/PlotService`, `plot/*Config`/`*Settings` |

**Invariant:** dependencies only ever point "down/right" in the table. `model`
depends on nothing internal. Never introduce a back-edge (e.g. `model` importing
`pipeline-core`).

---

## 3. The command model (`model` module)

This is the data that flows through the whole system. All of these are Java
`record`s except `Command` (a `sealed abstract class`).

```
ProcessorOutput
├── Metadata metadata        (source, generatedAt, stationId, units, totalCommands, Bounds)
└── List<Layer> layers
        └── Layer            (id, stationId, color, List<Command>)
                └── Command  (sealed: MoveCommand | DrawCommand | RefillCommand)
```

- **`ProcessorOutput(Metadata, List<Layer>)`** — `model:ProcessorOutput.java`. Root
  object; also the JSON document root.
- **`Layer(String id, String stationId, String color, List<Command> commands)`** —
  `model:Layer.java`. Maps to an Inkscape layer. `color` is the layer's source
  colour as `#rrggbb` (from SVG stroke/fill) or `null`; it drives watercolor
  station assignment. Has a **backward-compatible 3-arg constructor**
  `Layer(id, stationId, commands)` (color = null) and a wither
  `withStationId(newId)`. This is the canonical pattern for evolving a record
  without breaking callers — follow it when adding fields.
- **`Command`** — `model:command/Command.java`. Sealed base, `permits
  MoveCommand, DrawCommand, RefillCommand`. Jackson polymorphism via a `"op"`
  discriminator property (`@JsonTypeInfo`/`@JsonSubTypes`, names `MOVE`/`DRAW`/
  `REFILL`). Every command has an `int getId()` (sequential).
  - **`MoveCommand(id, x, y)`** — pen-up travel to an absolute (x,y) in mm.
  - **`DrawCommand(id, List<Point> points)`** — pen-down polyline; the points are
    already flattened (no curves). A single `DrawCommand` is one continuous
    stroke.
  - **`RefillCommand(id, stationId)`** — request to refill/dip the brush at a
    logical station; inserted by import when `maxDrawDistance > 0`, and the
    `stationId` may be rewritten by the watercolor `StationMapper`.
- **`Point(double x, double y)`**, **`Bounds(minX, minY, maxX, maxY)`** (with
  `Bounds.empty()` sentinel using ±`Double.MAX_VALUE`).
- **Units:** millimeters throughout the command model. `Metadata.units` is `"mm"`.

### Coordinate spaces (critical — most bugs live here)

`model:CoordinateTransform.java` is the **single source of truth** for all
coordinate math. There are distinct spaces:

1. **SVG/content space** — raw SVG user units, Y-down (SVG origin top-left).
2. **Logical/command space** — mm, Y-up. `SvgImportStage` flips Y about the
   content's vertical center on import so drawings come in upright (see §4).
3. **Machine/motor space** — physical plotter coordinates, produced by
   `CoordinateTransform.transformPoint(...)` applying, in order:
   `rotate → swap → invertX → invertY` (configurable per machine).
4. **Screen space** — `physicalToScreen(...)` for the live preview (Y-down,
   top-left origin). Canvas alignment is solved in this final projected space,
   so Top/Bottom/Left/Right retain their visible meaning for every machine origin,
   axis swap/inversion, portrait orientation, and final Flip Y.
5. **Overlay space** — the interactive positioning transform
   (`applyOverlayRaw(...)`: mirror → quarter-turn → uniform scale → translate,
   all about the content center). The GUI preview and the JSON "bake" call the
   **same** method so what-you-see equals what-you-plot.

`transformPoint`/`inverseTransformPoint` are exact inverses; keep them so.

---

## 4. SVG import (`pipeline-core:svgimport/SvgImportStage.java`)

`SvgImportStage` (a `final` class, all static methods) converts an SVG file into a
`ProcessorOutput`. It uses **Apache Batik** for SVG parsing and path geometry.

Public entry points (`SvgImportStage#importSvg`, overloaded):
- `importSvg(File, SvgImportOptions)` — plain import.
- `importSvg(File, Config, SvgImportOptions)` — run the SVGToolBox pipeline
  (§5) against the parsed `Document` first, then import.
- `importSvg(Document, String, SvgImportOptions)` — core; operates on an
  already-parsed DOM.

Pipeline inside `importSvg(Document, …)`:
1. **`identifyLayers`** — find Inkscape layers (`<g inkscape:groupmode="layer">`),
   each becoming a `LayerContext(layerName, stationId, color, rootNode)`. If none
   are found, falls back to `identifyPlainGroupLayers`: each top-level `<g>` that
   contains at least one drawable becomes its own layer, covering non-Inkscape
   exporters that group content into plain `<g id="layer_N">` elements without
   the Inkscape namespace attribute. That fallback only splits when there are
   ≥2 such candidate groups, so a single top-level group still collapses to one
   layer. No layers at all (from either tier) ⇒ one synthetic fallback layer
   using `options.defaultStationId`.
   Layer colour is read by `resolveLayerColor` → `resolveElementColor` →
   `normalizeColor` (handles hex, `rgb(...)`, and a `NAMED_COLORS` table).
2. **Bounds scan** — `calculateGlobalBounds` over all layers. A `skipPageBorderFilter`
   flag is computed via `countDrawables(...) <= 1`: **if the whole document has at
   most one drawable, the page-border heuristic (step 4) is disabled** so a
   single-shape SVG isn't discarded (regression-tested in
   `SvgImportStageTest#singleShapeSvgIsNotDroppedAsPageBorder`).
3. **Transform assembly** — builds an `AffineTransform globalTx`:
   - optional fit-to-size scale (`calculateScaleTransform` /
     `calculateFitToPageTransform`) from `SvgImportOptions.targetWidth/Height`
     and `keepAspectRatio`;
   - optional position offset (`posX/posY`) and `mirror`;
   - a **Y-flip about the content's vertical center**, concatenated last (so it
     applies first, in raw content space) — this is the SVG-Y-down →
     command-Y-up correction.
4. **`generateCommandsForLayer`** — for each drawable element:
   - geometry via `ShapeParser`/Batik `PathIterator` **flattened at
     `curveStep`** (so only `SEG_MOVETO`/`SEG_LINETO` appear — no curves reach the
     command model);
   - `applyElementTransform` bakes the element's own `transform=""` ancestry
     (`getAccumulatedTransform`) plus `globalTx`;
   - **`isPageBorderRect`** (unless `skipPageBorderFilter`) drops a full-page
     background/frame rect — it must coexist with other content to be filtered;
   - strokes accumulate into `DrawCommand`s; when `maxDrawDistance > 0`, cumulative
     stroke length is tracked and a `RefillCommand` is emitted whenever the budget
     is exceeded, with `interpolate(...)` splitting a long segment at the exact
     refill point.
5. Returns `ProcessorOutput` with computed `Bounds` and `totalCommands`.

`SvgImportOptions` (`pipeline-core:svgimport/SvgImportOptions.java`) is a record
carrying `maxDrawDistance` (≤0 disables refill), `defaultStationId`, `curveStep`,
`targetWidth/Height`, `keepAspectRatio`, `posX/posY`, `mirror`. Factories:
`defaults()` and `fitToFormat(...)` (subtracts `padding` on all sides from a
`PaperFormat`).

---

## 5. SVG→SVG preprocessing (`svgtoolbox-core`)

Optional DOM-mutating stage that runs **before** import when a `Config` is
supplied. Each step implements `Processor { void process(Document, Config) }`
and mutates the Batik `Document` in place.

`SvgToolboxPipeline#buildPipeline(Config)` assembles a fixed order
(`svgtoolbox-core:SvgToolboxPipeline.java`):

```
Visibility → StyleNormalizer → Rotate → StrokeWidth → Palette
  → Simplify → Hatch
  → Linesimplify → Linemerge → Linesort → Reloop
  → Layer → Crop
  → [PathOptimize]   (only if config.optimizePaths())
```

`LayerProcessor` re-buckets shapes into colour-keyed Inkscape layer groups
(it runs **after** `Hatch`, so hatch line groups are what it actually sees).
It skips shapes that already live under an existing layer group — checked by
walking the **full ancestor chain**, not just the immediate parent, because
hatch output wraps each shape's lines in an extra per-shape `<g>` that sits
between the shape and its original layer. Checking only the immediate parent
would treat hatched content as "unorganized" and re-bucket it purely by
colour across the whole document, merging distinct original Inkscape layers
that happen to share a colour (e.g. two layers both hatched in black) into
one — this was a real bug, fixed via `LayerProcessor#isInsideExistingLayer`.

`process(doc, config, progress?)` runs them in order, optionally reporting
progress via `ProgressCallback`, and prints stats (`SvgStatistics`) if
`config.printStats()`.

`Config` (`svgtoolbox-core:Config.java`) is a large record with a `Builder`,
holding every toolbox knob: `enableHatching`, `globalStyle` (a `HatchStyle`),
`overrides` (`Map<String,HatchStyle>` keyed by fill-hex — per-colour hatch),
`noHatchColors`, `minHatchArea`, palette, hidden layers, crop bounds, rotation,
the line-* tolerances, etc.

The shared `ToolboxOptionsPanel` used by Import SVG and Edit > Process SVG
includes a per-colour hatch table. `SvgFillColors` pre-populates it from
explicit `#RRGGBB` fill attributes/inline styles when a source file is
available; selected rows are converted directly to `Config.overrides`, while
"Use global" rows are omitted.

### Hatching (relevant to recent work)

- **`HatchStyle(angle, gap, patternName, amplitude, wavelength, dotRadius)`** —
  `svgtoolbox-core:HatchStyle.java`. The last three are **per-pattern parameters
  with `0 = auto`** (fall back to gap-derived defaults). A backward-compatible
  3-arg constructor sets them to 0. `of(angle, gap)` → linear.
- **`HatchProcessor`** — `processors/HatchProcessor.java`. For each fillable
  shape (not in `noHatchColors`, above `minHatchArea`): computes the world-space
  shape, picks a `HatchPattern` by `style.patternName()`, and replaces the shape
  with a `<g stroke=color>` of generated lines/paths/circles. **The pattern is
  read from `style.patternName()`** (from `globalStyle` or a per-colour
  `override`), *not* from `Config.hatchPattern` (which is a vestigial field — do
  not rely on it for selection).
- **`patterns/*`** — `HatchPattern` strategy interface; `Linear`, `Cross` (two
  linear passes 90° apart), `ZigZag`, `Wave`, `Dot`. Wave/ZigZag read
  `style.amplitude()`/`style.wavelength()` (auto: derived from `gap`); Dot reads
  `style.dotRadius()` (auto: stroke width).
- `getStyleFor(hex, config)` returns the per-colour override if present, else
  `config.globalStyle()` — the seam where per-area styling (roadmap Phase 10)
  plugs in.

---

## 6. Command-model transforms (`pipeline-core:optimize/`)

All pure `ProcessorOutput → ProcessorOutput` (or `Layer → Layer`):

- **`OptimizeStage#optimize(output, simplifyTolerance, reorderStrokes[, mergeTolerance])`**
  — per layer: RDP-simplifies polylines (`RamerDouglasPeucker`), optionally reorders
  strokes greedily (nearest-neighbour, via `PathOptimizer`) to cut pen-up travel, and
  (when `mergeTolerance > 0`) **welds** consecutive strokes whose endpoints touch into a
  single continuous polyline — reversing a segment when only its far end matches. Welding
  runs over the chosen order, so reordering first lets more neighbours line up. This both
  removes redundant pen-up/travel/pen-down cycles between segments that an SVG expressed as
  separate `<line>`/`<path>` elements, and (because every pen-down briefly dwells, see §8)
  eliminates the ink dot a wet pen leaves at each segment start. The 3-arg overload disables
  welding (`mergeTolerance = 0`). `computeStats(output)` returns `Stats(travelDistanceMm,
  pointCount, strokeCount)` for before/after reporting. Preserves layer id/station/color.
- **`MultipassStage#apply(output, passes)`** — duplicates each layer's draw
  commands `passes` times (pigment/ink build-up). Identity at `passes <= 1`.
- **`PathOptimizer`** — greedy nearest-neighbour stroke ordering.
- **`RamerDouglasPeucker`** — polyline simplification.

These are distinct from the *toolbox* line-simplify/sort processors (which run at
the SVG-DOM level, §5). Two different "simplify" stages exist at two pipeline
levels — keep them straight.

### IO

`ProcessorOutputIO` (`pipeline-core:io/`) uses a Jackson `ObjectMapper` with
`JavaTimeModule` (for `Instant`) to `load(File)`/`save(output, File)` the command
model as JSON. The polymorphic `Command` round-trips via the `"op"` field.

---

## 7. Watercolor (`watercolor`)

Optional and isolated. `StationMapper#assignByColor(output, List<PaintStation>)`
walks each layer, finds the nearest paint station to the layer's `color`
(CIELAB distance via `ColorUtil`), and returns a new `ProcessorOutput` with each
layer's `stationId` (and its `RefillCommand`s) rewritten to that station.
`PaintStation` describes a physical pot (id, colour, location, dip depth, etc.).
Nothing else depends on `watercolor`, so pen-only builds ignore it entirely.

---

## 8. Plotter backends (`plotter`)

`PlotterBackend` (`plotter:PlotterBackend.java`) is the abstraction the plot
orchestrator drives. Core methods: `connect/disconnect`, `moveto(x,y)` (pen-up
rapid), `lineto(x,y)` (pen-down draw), `move(dx,dy)` (relative jog),
`penup/pendown`, plus **default** methods (safe no-ops / fallbacks) for
`pendown(zDown)`, `queryPosition()`, `sendRaw(cmd)`, `adjustSpeed(dir)`,
`home()`, `haltMotion()`. Implementing a new backend only requires the core
methods.

Implementations:
- **`GcodeBackend`** — real serial GRBL-style backend. Owns a `SerialTransport`
  (`JSerialCommTransport` over jSerialComm) and two daemon threads: a
  **reader** loop (drains responses → `ackQueue`/`rawQueue`) and a **poller**
  loop (periodic `?` status → position/speed callbacks). Pen control via
  `GcodeOptions.penMode` (`servo` / `zaxis` / `m3m5`). `home()` runs GRBL `$H`
  and zeroes the origin; `haltMotion()` sends a realtime soft-reset to abort
  buffered motion (used by Stop). Formatting is delegated to `GcodeFormatter`.
- **`MockPlotterBackend`** — logs/simulates motion instantly; used for GUI/CI
  testing without hardware (`--mock-backend` / Settings checkbox).
- **`GcodeFileBackend`** — writes G-code to a `.gcode` file instead of a serial
  port (export). `GcodeFileReplay` streams an existing `.gcode` back through a
  backend for re-plotting.

`GcodeOptions` holds serial + machine + pen + feed-rate config (`serialPort`,
`baudRate`, `penMode`, `feedRateDraw/Travel`, `penServoUp/Down`, `zUp/zDown`,
`machineWidth/Height`, poll interval, boot delay).

---

## 9. Plot orchestration (`app:plot/PlotService.java`)

`PlotService(backend, settings)` executes a `ProcessorOutput` against a
`PlotterBackend`. It is backend-agnostic and runs on a dedicated plot thread
(spawned by the GUI). Key pieces:

- **Callbacks/hooks** (all optional, defaulted):
  - `LayerGate#await(layer)` — blocks before each layer (default `IMMEDIATE`); the
    GUI uses it to implement "Confirm Layer" pauses (pen/paper changes, refills).
  - `logCallback`, `commandedPositionCallback(x,y)` (drives the live cursor),
    `layerStartedCallback(layer)`.
- **Lifecycle/threading:** `cancel()`, `pause()`, `resume()` use a
  `volatile boolean cancelled/paused` + a `pauseLock` monitor. `plot(...)` checks
  `cancelled` between commands and at layer boundaries. On cancel, a `finally`
  block always lifts the pen (`backend.penup()`) so it never bleeds ink on the
  paper while stopped — a hardware-safety invariant.
- **`plot(output)` flow:** resolve machine W/H from `settings`; compute
  `contentBounds`; compute an alignment `offset` (either an explicit
  preview-supplied `alignmentOffsetOverride`, or `CoordinateTransform.calculateAlignmentOffset`
  from `canvasAlign`); `checkPreflightBounds` (warns/guards against out-of-bed
  geometry); then per layer: `layerGate.await` → optional rinse (between colours)
  → `executeLayer`.
- **`executeLayer`** translates each `Command` into backend calls, running every
  point through `transformAndClamp` → `doTransform`
  (`CoordinateTransform.transformPoint`) → `softClamp` (clamps to bed, counts
  out-of-bounds). `RefillCommand` → `performRefill` → `dip(station)` (with optional
  swirl). Reports commanded position for the live cursor at intervals.

---

## 10. GUI (`app:gui/`)

Swing + FlatLaf dark theme. `GantryApp#main` sets up `FlatDarkLaf`, builds a
`PlotterPanel`, attaches its menu bar, and shows the frame.

- **`PlotterPanel`** (~1,180 lines) — the composition root for the main window.
  It lays out the preview, console, extracted control panels, menus, and workflow
  entry points, then wires their callbacks to document and plot controllers.
  The completed decomposition and its safety invariants are tracked in
  `docs/REFACTORING.md`. Menu actions include Import SVG (artwork), Re-process Source
  SVG (`EditProcessDialog`), Optimize Commands, Map Layer Colors to Stations,
  Open/Save Project, Open/Export Flattened Commands, Export/Replay G-code, and
  persistent Recent Plot Jobs. Every File/Edit menu item names its format in the
  label and has a tooltip (`tip()` helper) explaining what it reads or writes.
  The Plot section's **Layers** checklist
  (`layerChecks`, one `JCheckBox` per layer, rebuilt by `refreshLayerSelector()`
  whenever `currentOutput` is replaced) selects any subset of layers to
  preview/plot/export: `selectedOutput()` narrows `currentOutput` to the ticked
  `Layer`s before `preparePlotOutput()` bakes the overlay and applies multipass, so
  Start Plot / Export / the time estimate all operate on just those layers (the
  per-pen workflow). The ticked set is pushed to the preview via
  `VisualizationPanel.setSelectedLayers(...)`. File handling lives in
  `DocumentFileWorkflow`/`GcodeFileWorkflow`; guided machine operations live in
  `PreflightWorkflow`, `SetupWorkflow`, `CalibrationWorkflow`, and
  `StationTestWorkflow`; jog, plot, overlay, and raw-command widgets are separate
  panels.
- **`DocumentSession`** — Swing-free owner of the current command model, selected
  layers, source provenance, bounded multi-level undo/redo, dirty state, and
  plot/export preparation. `GantryProjectIO` persists that state plus canvas
  placement, passes, and import/vectorizer provenance in `.gantry` files.
- **`PlotJobController`** — Swing-free owner of the connected `PlotterBackend`,
  active `PlotService`, plot worker, pause/resume/cancel, completion cleanup,
  progress state, and re-plot eligibility.
- **`VisualizationPanel`** (~960 lines) — the live-canvas scene and transform
  facade. It retains the public canvas API and delegates painting to
  `CanvasRenderer`, mouse gestures to `CanvasInteractionController`, hit testing,
  snapping, viewport inversion, and resize calculations to
  `CanvasInteractionGeometry`, and popup construction to `CanvasContextMenu`.
  Deterministic colour and segment calculations live in the directly tested
  `CanvasPalette` and `CanvasGeometry`. The canvas draws the bed, drawing, moving
  cursor, and interactive positioning overlay
  (`overlayOffsetX/Y`, `overlayScale`, `overlayRotation`, `overlayMirror` — a
  **single global transform**, i.e. exactly one drawing today; multi-document is
  deliberately deferred pending a validated workflow). Drag/scale/rotate/mirror + a right-click context menu. Uses
  `CoordinateTransform.applyOverlayRaw` + `physicalToScreen` so preview matches
  plotted output. Tracks each rendered stroke's source layer (`pathLayer`) so
  `setSelectedLayers(indices)` can draw a chosen subset of layers in full colour and
  ghost the rest; alignment/bounds stay computed over **all** paths so the selected
  layers keep their true bed position.
  Each layer is drawn in its own colour (`layerColors`, resolved by `displayColorFor`
  from the layer's source `#rrggbb`, brightened against the dark canvas via
  `ensureReadable` and falling back to `FALLBACK_PALETTE` for unknown/near-black
  colours); `setColorByLayer(false)` reverts to a single uniform colour.
- **`ToolboxOptionsPanel`** — the single shared editor for the full SVGToolBox
  option set (style / hatch / geometry / optimize), embedded verbatim by both
  `SvgImportDialog` and `EditProcessDialog` so the two can't drift apart.
  `buildConfig()` produces the `Config` (throwing `IllegalArgumentException` on
  bad stroke width / palette / crop), and a private static `State` snapshot
  persists every field across reopens and across both dialogs.
- **`SvgImportDialog`** — import options (size/padding/mirror) plus, in its
  "Process SVG" tab, a `ToolboxOptionsPanel` under a master "Run SVGToolBox
  processing" toggle. Builds an `SvgImportOptions` and (if toolbox or hatch
  enabled) a `Config` via the panel. **Note:** enabling hatching implies running
  the toolbox pipeline (a listener ticks the master toggle, and `onOk` builds the
  config when either is on).
- **`EditProcessDialog`** — "Edit > Re-process Source SVG": re-runs the toolbox pipeline
  against the originally imported file. Its whole body is a `ToolboxOptionsPanel`,
  so it now exposes the same options as the import tab (it previously offered only
  a Crop/Hatch/Palette/Rotate/Optimize subset).
- **`HelpDialog`** — "Help > User Guide...": renders `docs/USER_GUIDE.md` in-app
  instead of shelling out to whatever the OS associates with `.md` files. Parses
  the markdown once with `commonmark` (+ `commonmark-ext-gfm-tables`, so the
  guide's reference tables render as real `<table>`s) into HTML shown in a
  `JEditorPane`, with a clickable table-of-contents `JList` (built from H1-H3
  headings) down the left. Anchor ids are generated by hand — GitHub-style slugs,
  deduped with `-1`/`-2` suffixes — and injected into the rendered HTML via a
  custom `AttributeProvider`, because `JEditorPane.scrollToReference` only
  matches `<a name="...">` tags and ignores `id` attributes entirely; clicking a
  TOC entry instead looks the target element up directly via
  `HTMLDocument.getElement(id)` and scrolls to it with `modelToView`/
  `scrollRectToVisible`. The markdown is bundled into the jar at `/docs/USER_GUIDE.md`
  (see `app/pom.xml`'s extra `<resource>`) so the dialog works regardless of the
  working directory; falls back to reading `docs/USER_GUIDE.md` off disk, then to
  a "not found" placeholder, if the classpath resource is missing.
- **Machine menu & guided wizards** (`PlotterPanel`, roadmap Phases 13–17) — the
  **Machine** menu is the shared entry point for the operator-facing flows that
  hang off a connection: **Connect/Disconnect** (label toggles with state, mirrored
  on the toolbar button), **Home**, and the four wizards below. All wizards are
  built on one small shell: `WizardDialog` (a `CardLayout` step host with
  Back/Next/Skip/Finish and per-step gating) + the `WizardStep` interface
  (`title/panel/canAdvance/isOptional/onEnter/onLeave`) + `PanelStep` (a `WizardStep`
  adapter that just hosts a supplied `JComponent` under a title, used to re-parent
  existing panels into a wizard without duplicating widgets). **Invariant: every
  wizard step calls the exact same backend/PlotService/SettingsPanel code the
  non-wizard buttons use — a wizard is a guided *ordering* of existing actions,
  never a second code path.**
  - **Setup Wizard** (`onSetupWizard`, Phase 15) — walks the `SettingsPanel`
    sections in first-run order (connection → geometry/origin → pen/speed) by
    `PanelStep`-wrapping the panel's **real** section accessors
    (`connectionPanel()/geometryPanel()/penPanel()`), so there is exactly one set
    of settings widgets. On Finish it commits/persists/applies config exactly like
    `Settings > Preferences…`. Offered once automatically on a fresh install
    (`maybeOfferFirstRunSetup`, gated on `firstRun = !configFile.exists()` captured
    *before* `ConfigStore.load`), and reachable from a "Run Setup Wizard…" button
    inside the Settings dialog (which disposes Settings first so two copies of the
    fields never fight to save).
  - **Pre-Plot Checklist / Pre-flight** (`onPreflightWizard`, Phase 14) — connect →
    home (optional) → **frame the job** (optional) → operator-attested physical
    checklist (gates Next until all ticked) → confirm; Finish calls the same
    `onStartPlot()`. `frameJob()` traces the job's bounding box pen-up through
    `PlotService.computeFrameBounds`, which runs the selected layers through the
    **same transform/alignment/soft-clamp pipeline as the plot**, so the trace can
    never command the head off the bed even if the drawing overhangs. Reachable
    from a **Pre-flight…** button next to Start, from the Machine menu, and run
    automatically before Start when `config.preflightBeforeStart` (the "Run Pre-Plot
    Checklist before Start" Settings toggle, default on) is set. The connect step
    polls on a `javax.swing.Timer` to re-enable Next when the background connect
    thread finishes.
  - **Calibrate Axes** (`onCalibrateAxesWizard`, Phase 16) — requires a live
    connection (drives motion and reads/writes GRBL settings). Two halves: a
    **direction check** (`CalibDirectionStep`: jog +X/+Y via the existing `jog()`,
    flip `config.invertX/invertY` if the head moved the wrong way) and a per-axis
    **scale calibration** (`CalibScaleStep`, optional: reads current steps/mm via
    `sendRaw("$$")` + `GrblSettings.findSetting` on entry, commands a known move,
    takes the measured distance, previews `GrblSettings.correctedStepsPerMm`
    = current × commanded/measured, and writes `$100`/`$101` via
    `GrblSettings.writeCommand` + `sendRaw`). The GRBL `$$`/`$100=`/`$101=` plumbing
    reuses the existing `PlotterBackend.sendRaw` (multi-line read until `ok`); no
    backend-interface change was needed, and `MockPlotterBackend` emulates the
    settings store so the whole round-trip is exercisable headless.
  - **Test Color Stations** (`onTestStationsWizard`, Phase 17 Half B) — requires a
    live connection and ≥1 configured station. One optional `StationTestStep` per
    `config.stations` entry: **Move here** (pen-up dry visit) and **Wet test** both
    delegate to new `PlotService.dryVisitStation`/`wetTestStation`, where the wet
    test calls the *same* private `dip()` a real refill uses (so the test can never
    diverge from a plot's refill); the **−X/+X/−Y/+Y** nudge buttons `move()` the
    head and track the same delta in the step's stored coordinates, written back into
    `config.stations` on Finish. The companion **Half A** (visual placement) lives in
    `VisualizationPanel`: station markers are hit-tested (`hitTestStation`) and
    draggable, with `screenToPhysical` inverting the paint transform (un-translate/
    scale, then the reverse of `physicalToScreen`); a `StationEditListener` reports
    drags and the "Add station here" context-menu action back to `PlotterPanel`,
    which rewrites `config.stations` and re-pushes via `applyConfigToVis` so the
    canvas and the `SettingsPanel` station table never diverge.
- **`SettingsPanel`** — machine/serial/pen/feed/station configuration; persisted
  via `plot/ConfigStore` (+ `GantryConfig`, `StationConfig`, `PlotSettings`,
  `PlotService`-facing settings). `TimeEstimator` estimates plot duration —
  per layer it sums `travelDist/feedRateTravel + drawDist/feedRateDraw` plus
  fixed overheads: `REFILL_SECONDS` (0.5s) per `RefillCommand`, matching
  `PlotService.performRefill`'s dwell, and `gcode.penDownDelayMillis / 1000`
  per `DrawCommand`, matching `GcodeBackend.pendown()`'s settle sleep (charged
  once per pen-down regardless of `penMode` — servo and Z-axis moves both
  pay it). On hatch-dense drawings with many short strokes, the pen-down
  overhead can dominate the distance-based terms, so it must not be dropped
  when re-deriving this formula. The dwell is configurable via
  `GcodeOptions.penDownDelayMillis` (default 80ms, settable to 0): it lets a
  slow servo/Z finish lowering before drawing, but kept short because a long
  dwell leaves a wet pen pooling an ink dot at each line's start — the
  `OptimizeStage` weld pass (§6) is the structural fix, this is the per-pen
  tuning knob. `CommandFile` handles command-JSON load/save plumbing.

The GUI runs long operations off the EDT (a `runBusy(...)` helper) and the plot on
its own thread, marshaling UI updates back via Swing.

---

## 11. CLI (`cli:SvgImportCli.java`)

Apache Commons CLI parser; headless SVG→JSON. Flags mirror the import + toolbox
options: `--fit-to`/`--padding`/`--curve-step`/`--mirror`/`--max-dist`/`--station`
for import; `--toolbox` + per-processor flags (`--hatch`, `--pattern`,
`--hatch-angle/-gap`, the new `--hatch-amplitude/--hatch-wavelength/--dot-radius`,
`--style` per-colour overrides, `--no-hatch`, `--min-area`, `--layer-width`,
`--rotate`, `--crop`, `--palette`, `--linesimplify/-merge/-sort`, `--reloop`,
`--optimize`, `--toolbox-stats`). Post-import flags provide simplification,
reordering, merging, and `--passes N`. A shared JSON batch config can supply
station mappings and G-code settings; `--map-stations` applies refill mapping
and `--gcode FILE` emits a machine artifact alongside command JSON.

The GUI and CLI intentionally differ at the machine-control boundary:
plotting/jog/replay remain GUI-only. Both expose per-colour hatch styles; the
CLI additionally retains automation-oriented no-hatch, minimum-area, and
per-colour stroke-width controls.

---

## 12. Testing

`mvn clean install` builds and tests everything (`BUILD SUCCESS`, zero failures
expected). Coverage is strongest where logic is pure:
- `model` — JSON round-trip, coordinate transforms.
- `pipeline-core` — `SvgImportStageTest` (4 nested classes, ~26 tests: parsing,
  fit-to-page, refill, page-border drop **and** single-shape exemption, transform
  baking, mirroring), plus optimize/multipass.
- `svgtoolbox-core` — one test class per processor; `HatchProcessorTest` covers
  patterns, area filter, no-hatch, and explicit-vs-auto dot radius.
- `plotter` — `GcodeBackendTest`/`GcodeFileBackendTest` (G-code format, pen modes).
- `app` — `PlotServiceTest` (layer sequencing, refill, cancel, OOB clamp,
  position callbacks), `TimeEstimatorTest`.

Not unit-tested (manual checklist in `docs/TESTING.md`): Swing rendering/interaction,
live serial and full G-code file-content correctness.

---

## 13. Conventions, invariants, and gotchas (for safe edits)

- **`ProcessorOutput` is the contract.** Prefer adding a pure
  `ProcessorOutput → ProcessorOutput` stage over threading state through the GUI.
- **Evolve records with a secondary constructor + wither**, as `Layer` does, so
  existing positional callers and JSON keep working. Do the same for `HatchStyle`
  (already done) and any future `Config`/`Metadata` change.
- **All coordinate math goes through `CoordinateTransform`.** Don't inline
  rotate/flip/clamp logic in the GUI or PlotService. The preview and the JSON bake
  must call the *same* overlay method.
- **The command model has no curves** — everything is flattened to line segments
  at import (`curveStep`). Downstream code can assume `DrawCommand.points` are
  polylines.
- **Hatch pattern comes from `HatchStyle.patternName()`**, not
  `Config.hatchPattern`. Per-colour styling keys off fill-hex in
  `Config.overrides` via `HatchProcessor#getStyleFor`.
- **Page-border filter is content-relative** and self-disables for single-shape
  documents. Changing `isPageBorderRect`/`countDrawables` risks reintroducing the
  "invisible single rect" bug — keep `SvgImportStageTest` green.
- **Hardware safety:** any plot-abort path must leave the pen up. `PlotService.plot`
  guarantees this in `finally`; preserve it. GRBL aborts also fire
  `haltMotion()` (realtime soft-reset).
- **Threading:** backend reader/poller are daemon threads inside `GcodeBackend`;
  the plot runs on its own thread in the GUI; `cancelled/paused` are `volatile`
  with a `pauseLock` monitor. Never block the EDT on a plot or serial call.
- **Module direction is sacred** (§2). Adding a dependency edge that points the
  wrong way (e.g. `model` → `pipeline-core`) is the one structural change to
  refuse.

---

## 14. Where to look first, by task

| Task | Start here |
|---|---|
| Change how SVGs become commands | `pipeline-core:svgimport/SvgImportStage.java` |
| Add/modify an SVG→SVG effect | `svgtoolbox-core:processors/*` + register in `SvgToolboxPipeline#buildPipeline` |
| Add a hatch pattern | `svgtoolbox-core:patterns/*` + `HatchProcessor` switch |
| Change pen-travel optimization | `pipeline-core:optimize/OptimizeStage`, `PathOptimizer` |
| Add a plotter type | implement `plotter:PlotterBackend` |
| Change G-code output | `plotter:GcodeFormatter` / `GcodeBackend` |
| Change plotting/safety/sequencing | `app:plot/PlotService.java` |
| Change canvas rendering | `app:gui/CanvasRenderer.java` + `VisualizationPanel.java` scene/transform API |
| Change canvas gestures or hit testing | `CanvasInteractionController.java` / `CanvasInteractionGeometry.java` |
| Change menus/controls/orchestration | `PlotterPanel.java` plus the relevant extracted `*Panel` or `*Workflow` |
| Change/add a guided wizard | the relevant `PreflightWorkflow` / `SetupWorkflow` / `CalibrationWorkflow` / `StationTestWorkflow` + `WizardDialog`/`WizardStep`/`PanelStep` |
| Change refill-station test-run / dip behaviour | `app:plot/PlotService.java` (`dryVisitStation`/`wetTestStation`/`dip`) |
| Change canvas station placement (drag / add) | `CanvasInteractionController.java`, `CanvasInteractionGeometry.java`, and `VisualizationPanel.StationEditListener` |
| Change GRBL settings read/write (steps/mm) | `plotter:GrblSettings.java` (+ `MockPlotterBackend` emulation) |
| Change persisted settings | `app:plot/ConfigStore` + `*Config`/`*Settings` |
| Headless/batch behavior | `cli:SvgImportCli.java` |
| Coordinate/transform math | `model:CoordinateTransform.java` |

See `ROADMAP.md` for the active product plan and `docs/ROADMAP_HISTORY.md` for
the deprecated phase diary. `docs/LESSONS_LEARNED.md` records recurring bugs,
design principles, and gotchas distilled from building the system.
