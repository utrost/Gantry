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
```

| Module | Package root | Responsibility | Key types |
|---|---|---|---|
| `model` | `org.trostheide.gantry.model` | Shared DTOs and the coordinate-transform math. No logic dependencies. | `ProcessorOutput`, `Layer`, `Command` (+ subtypes), `Point`, `Bounds`, `Metadata`, `CoordinateTransform` |
| `svgtoolbox-core` | `…svgtoolbox` | SVG→SVG DOM processors (operate on a Batik `org.w3c.dom.Document` in place): hatch, palette, crop, rotate, simplify, line-merge/sort, etc. | `SvgToolboxPipeline`, `Processor`, `Config`, `HatchStyle`, `processors/*`, `patterns/*` |
| `pipeline-core` | `…pipeline` | SVG→command-model import and command-model→command-model transforms. | `SvgImportStage`, `SvgImportOptions`, `PaperFormat`, `OptimizeStage`, `MultipassStage`, `PathOptimizer`, `RamerDouglasPeucker`, `ProcessorOutputIO` |
| `watercolor` | `…watercolor` | Optional colour→paint-station assignment. | `StationMapper`, `PaintStation`, `ColorUtil` |
| `plotter` | `…plotter` | Pluggable plotter backends + G-code formatting/replay/serial transport. | `PlotterBackend` (interface), `GcodeBackend`, `MockPlotterBackend`, `GcodeFileBackend`, `GcodeFormatter`, `GcodeFileReplay`, `SerialTransport`/`JSerialCommTransport` |
| `cli` | `…cli` | Headless SVG→JSON converter. | `SvgImportCli` |
| `app` | `…app` | Swing GUI, plot orchestration, settings persistence, live visualization. | `GantryApp`, `gui/PlotterPanel`, `gui/VisualizationPanel`, `gui/SvgImportDialog`, `gui/EditProcessDialog`, `gui/SettingsPanel`, `plot/PlotService`, `plot/*Config`/`*Settings` |

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
   top-left origin).
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
   each becoming a `LayerContext(layerName, stationId, color, rootNode)`. No
   layers ⇒ one synthetic fallback layer using `options.defaultStationId`.
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

`process(doc, config, progress?)` runs them in order, optionally reporting
progress via `ProgressCallback`, and prints stats (`SvgStatistics`) if
`config.printStats()`.

`Config` (`svgtoolbox-core:Config.java`) is a large record with a `Builder`,
holding every toolbox knob: `enableHatching`, `globalStyle` (a `HatchStyle`),
`overrides` (`Map<String,HatchStyle>` keyed by fill-hex — per-colour hatch),
`noHatchColors`, `minHatchArea`, palette, hidden layers, crop bounds, rotation,
the line-* tolerances, etc.

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

- **`OptimizeStage#optimize(output, simplifyTolerance, reorderStrokes)`** — per
  layer: RDP-simplifies polylines (`RamerDouglasPeucker`) and optionally reorders
  strokes greedily (nearest-neighbour, via `PathOptimizer`) to cut pen-up travel.
  `computeStats(output)` returns `Stats(travelDistanceMm, pointCount,
  strokeCount)` for before/after reporting. Preserves layer id/station/color.
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

- **`PlotterPanel`** (~1440 lines) — the main window and de-facto controller. Holds
  the current `ProcessorOutput` (`currentOutput`), the right-hand control column
  (Jog, Overlay/Position, Plot, Raw G-code sections — each wrapped in
  `capHeight(...)` which also left-aligns to avoid BoxLayout center-clipping), the
  console, the menu bar, and wires user actions to the pipeline/PlotService. It is
  a known **god-class** and the prime candidate for extracting a `PlotSession`
  controller (see ROADMAP). Menu actions: Import SVG, Process SVG
  (`EditProcessDialog`), Optimize Loaded Commands, Map Layer Colors to Stations,
  Save/Load Commands, Export/Replay G-code.
- **`VisualizationPanel`** (~974 lines) — the live canvas: draws the bed, the
  drawing, the moving cursor, and the interactive positioning overlay
  (`overlayOffsetX/Y`, `overlayScale`, `overlayRotation`, `overlayMirror` — a
  **single global transform**, i.e. exactly one drawing today; multi-document is
  roadmap Phase 9). Drag/scale/rotate/mirror + a right-click context menu. Uses
  `CoordinateTransform.applyOverlayRaw` + `physicalToScreen` so preview matches
  plotted output.
- **`SvgImportDialog`** — import options + the SVGToolBox toolbox controls (incl.
  hatch pattern/angle/gap and the per-pattern amplitude/wavelength/dot-radius
  spinners). Builds an `SvgImportOptions` and (if toolbox or hatch enabled) a
  `Config`. **Note:** enabling hatching implies running the toolbox pipeline (a
  listener ticks the master toggle, and `onOk` builds the config when either is
  on).
- **`EditProcessDialog`** — "Edit > Process SVG": re-runs a subset of toolbox
  processors against the originally imported file. Remembers its hatch/rotate/
  optimize settings across opens via `static last*` fields.
- **`SettingsPanel`** — machine/serial/pen/feed/station configuration; persisted
  via `plot/ConfigStore` (+ `GantryConfig`, `StationConfig`, `PlotSettings`,
  `PlotService`-facing settings). `TimeEstimator` estimates plot duration;
  `CommandFile` handles command-JSON load/save plumbing.

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
`--optimize`, `--toolbox-stats`). Output is a command-model JSON file via
`ProcessorOutputIO`.

**Current CLI vs GUI gap (roadmap Phase 11):** the CLI has no G-code export, no
multipass, no post-import `OptimizeStage`, and no station mapping; the GUI lacks
the per-colour hatch knobs the CLI exposes. Plotting/jog/replay are GUI-only by
design.

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
live serial, full G-code file-content correctness, the CLI `--style` flag.

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
| Change the canvas/positioning UX | `app:gui/VisualizationPanel.java` |
| Change menus/controls/orchestration | `app:gui/PlotterPanel.java` |
| Change persisted settings | `app:plot/ConfigStore` + `*Config`/`*Settings` |
| Headless/batch behavior | `cli:SvgImportCli.java` |
| Coordinate/transform math | `model:CoordinateTransform.java` |

See `ROADMAP.md` for planned work (Phases 9–12: multi-document canvas, per-area
hatch styling, CLI/GUI parity, per-pattern hatch parameters) and the detailed
design rationale behind each.
