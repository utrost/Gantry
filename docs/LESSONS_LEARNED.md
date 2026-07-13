# Gantry — Lessons Learned, Design Principles & Gotchas

> Audience: anyone (human or LLM) about to change this codebase. `ARCHITECTURE.md`
> tells you *how the system is built*; this document tells you *what we already
> got wrong, what we decided on purpose, and what will bite you next time*. Read
> the Design Principles once; skim the Bugs and Gotchas before touching the area
> they name. When you fix a non-obvious bug or make a non-obvious call, add it here.

---

## 1. Design principles

These are the load-bearing decisions. Breaking one isn't a style nit — it
reintroduces a class of bug we've already paid for.

1. **`ProcessorOutput` is the contract.** The command model
   (`ProcessorOutput → List<Layer> → List<Command>`) is the one interchange type
   that flows through import, preprocessing, optimization, watercolor, and
   plotting. Prefer adding a pure `ProcessorOutput → ProcessorOutput` stage over
   threading state through the GUI. Pure stages are headlessly testable and
   compose; GUI state does neither.

2. **One source of truth for coordinate math: `CoordinateTransform`.** Every
   rotate / swap / invert / align / clamp goes through it. Never inline that math
   in the GUI or `PlotService`. The five coordinate spaces (SVG → logical →
   machine → screen → overlay) are documented in ARCHITECTURE §3; mixing them up
   is the single most common source of "drawing is mirrored / off the bed / on the
   wrong axis" bugs. **The preview and the JSON bake must call the *same* overlay
   method** — that's what guarantees what-you-see equals what-you-plot.

3. **Evolve records without breaking callers.** Add a secondary constructor that
   defaults the new field plus a `withX(...)` wither, exactly as `Layer` (added
   `color`) and `HatchStyle` (added `amplitude/wavelength/dotRadius`) did. Existing
   positional callers and old JSON keep working. Don't reorder or retype existing
   record components.

4. **Module dependencies only point one way.** `app → {pipeline-core, plotter,
   watercolor, model}`, `pipeline-core → svgtoolbox-core → model`, `model` depends
   on nothing internal. A back-edge (e.g. `model` importing `pipeline-core`) is the
   one structural change to refuse outright — it's how the build turns into a ball
   of mud.

5. **Hardware safety is non-negotiable: any abort path leaves the pen up.**
   `PlotService.plot` guarantees `backend.penup()` in a `finally`; a GRBL abort also
   fires `haltMotion()` (realtime soft-reset to drop buffered motion). When you add
   a new stop/cancel/error path, preserve both — a pen left down bleeds ink or
   gouges the paper while the machine sits idle.

6. **Wizards are guided orderings of existing actions, never a second code path.**
   The Setup / Pre-Plot / Calibrate wizards re-parent the *real* `SettingsPanel`
   sections and call the *same* `jog()`/`onHome()`/`onStartPlot()`/`PlotService`
   methods as the plain buttons. If a wizard step needs logic the buttons don't
   have, add it to the shared method and have both call it — don't fork.

7. **Never block the EDT on a plot or a serial call.** The plot runs on its own
   thread; `GcodeBackend`'s reader/poller are daemon threads; long GUI work goes
   through `runBusy(...)`. `cancelled`/`paused` are `volatile` behind a `pauseLock`
   monitor. Marshal UI updates back with `SwingUtilities.invokeLater`.

8. **The command model has no curves.** Everything is flattened to line segments at
   import (`curveStep`). Downstream code may assume `DrawCommand.points` is a
   polyline — don't reintroduce beziers past the import boundary.

9. **Shared UI panels over duplicated ones.** `ToolboxOptionsPanel` is embedded
   verbatim by both the import dialog and the re-process dialog; the wizards host
   the real `SettingsPanel` sections. Two editors for the same settings *will*
   drift apart — one editor, reused, can't.

---

## 2. Bugs we already fixed (don't reintroduce them)

Each of these was a real, shipped-then-fixed bug. The named test or guard is what
keeps it dead — keep it green.

| Area | The bug | The fix / guard |
|---|---|---|
| **SVG import** | A single-shape SVG (one `<rect>`) was silently dropped because the page-border heuristic treated the lone shape as the page frame → "invisible drawing." | The page-border filter self-disables when the document has ≤1 drawable (`skipPageBorderFilter = countDrawables(...) <= 1`). Locked by `SvgImportStageTest#singleShapeSvgIsNotDroppedAsPageBorder`. The filter is **content-relative**: a border rect is only dropped when it coexists with other content. |
| **SVGToolBox layering** | `LayerProcessor` re-bucketed hatched shapes by colour across the whole document, merging two distinct Inkscape layers that happened to share a colour (e.g. both hatched black) into one. | `isInsideExistingLayer` walks the **full ancestor chain**, not just the immediate parent — because hatch output wraps each shape's lines in an extra per-shape `<g>` between the shape and its real layer. |
| **Hatch pattern selection** | Pattern was sometimes read from the vestigial `Config.hatchPattern` field, so the chosen pattern (dot/wave/…) silently fell back to linear. | Pattern comes from `HatchStyle.patternName()` (global or per-colour override via `HatchProcessor#getStyleFor`). Treat `Config.hatchPattern` as dead. |
| **Visualization axis mismatch** | The live cursor/bed moved on the wrong axis when an "Extra" invert/swap flag combined with the Machine-Origin-implied invert — the canvas recomputed its own swap/invert instead of using the composited values. | The visualization consumes the **same** effective swap/invert values used for jogging and G-code. Don't recompute transform components in the canvas. |
| **Frame-the-job overhang** | "Frame the job" traced the raw drawing bounds, so a drawing overhanging the bed commanded the head off the machine (e.g. Y=233 on a 200 mm bed). | `frameJob()` runs the selected layers through `PlotService.computeFrameBounds`, the **same** transform/alignment + soft-clamp pipeline as the plot. The trace can't exceed the bed. |
| **Plot positioning override** | The canvas-alignment re-centered the drawing at plot time, overriding where the user had dragged it. | The preview's alignment offset is passed through to the plot via `alignmentOffsetOverride`, so positioning survives to the machine. |
| **Ink dot at every segment start** | SVGs that split one visible line into many short `<line>`s caused a pen-down (and its settle dwell) per segment, leaving an ink blob at each start. | `OptimizeStage` weld pass (`mergeTolerance > 0`) fuses touching strokes into one polyline — the *structural* fix. `penDownDelayMillis` (default 80 ms, settable to 0) is the per-pen tuning knob for whatever remains. |
| **Windows serial connect** | First connect reported a read timeout, the next "Failed to open serial port" — a leaked port handle after a failed connect on CH340-style adapters. | Handle is released on failed connect; documented in USER_GUIDE Troubleshooting. |

---

## 3. Development & verification gotchas

Practical traps that cost time during this project's development (not bugs in the
product — bugs in *how you work on it*).

- **Rebuild the jar, not just `target/classes`, before verifying a GUI change.**
  The app is launched from the shaded jar (`app/target/app-1.0.0.jar`). If
  you `mvn compile` but run the old jar, you'll "verify" stale behavior and chase a
  ghost. Run `mvn -pl app -am package` (or `scripts/build.sh`) and relaunch.
  - This bit us specifically with the in-app **User Guide**: `docs/USER_GUIDE.md`
    is bundled into the jar as a classpath resource (`/docs/USER_GUIDE.md`, see the
    extra `<resource>` in `app/pom.xml`). Editing the markdown on disk does **not**
    update the in-app Help dialog until you repackage. (It falls back to reading the
    file off disk only if the resource is missing.)

- **`mvn exec:java` is unreliable here** — it repeatedly threw
  `ClassNotFoundException` on `GantryApp` despite the class being present. Run the
  packaged jar directly (`java -jar app/target/app-1.0.0.jar`) for any
  manual/headless GUI run.

- **Headless GUI verification recipe** (works reliably): start `Xvfb :99` and wait
  until `pgrep Xvfb` confirms it's listening *before* launching the app (otherwise
  `Can't connect to X11 window server`); export `DISPLAY=:99`; drive with `xdotool`;
  capture with `scrot`. The window manager here doesn't support `_NET_ACTIVE_WINDOW`,
  so use `xdotool windowfocus`/`--window <id>` and click absolute coordinates
  rather than `windowactivate`.

- **Kill stray Java GUIs with an explicit `kill -9` pipeline**, not `pkill` —
  `ps aux | grep 'java -jar' | grep -v grep | awk '{print $2}' | xargs -r kill -9`.
  `pkill` was observed to silently swallow the output of chained commands in this
  environment.

- **`config.json` is runtime state, never a feature artifact.** For headless
  verification we flip `"mock": true` in it, but always `git checkout -- config.json`
  before committing source changes. Don't commit a mock-toggled or
  geometry-tweaked config as part of a feature.

- **First-run detection must capture `!configFile.exists()` *before* loading.**
  `firstRun` is read before `ConfigStore.load` so the Setup-Wizard offer fires
  exactly once on a fresh install. If you reorder that, the offer either never
  fires or fires every launch.

- **GRBL multi-line responses end at `ok`/`error`.** `sendRaw` already collects
  until that terminator; `$$` (settings dump) and `$100=`/`$101=` (writes) ride on
  it with no backend-interface change. `MockPlotterBackend` emulates the settings
  store so the Calibrate Axes round-trip (read → compute → write → re-read) is
  fully exercisable without hardware — extend the mock when you add GRBL plumbing.

- **The shade plugin does not bundle `system`-scoped dependencies.** The
  `vectorize` module needs DrPTrace, which isn't on Maven Central. Carrying it as
  a `system`-scoped jar compiles and tests fine on the reactor classpath, but the
  class is then *missing* from the shaded `cli`/`app` fat jars — so `bezier`
  breaks only in the distributed artifact. The fix that keeps a fresh
  `mvn clean install` self-contained is an **in-project Maven repository**
  (`<root>/maven-repo`, the `gantry-local` `file://` repo keyed off
  `${maven.multiModuleProjectDirectory}`) with the jars as ordinary `compile`
  dependencies. Don't reach for `<scope>system</scope>` for anything that must
  ship in a fat jar.

- **Prefer vendoring a small public-domain source file over a JitPack
  dependency.** ImageTracer (`bezier2`) was originally a JitPack artifact
  (`com.github.brixomatic:imagetracerjava`); JitPack is unreachable from
  restricted/CI/offline networks (HTTP 403 here), which blocks the *whole* build.
  The upstream `jankovicsandras/imagetracer` is a single Unlicense file — vendored
  in-tree under `vectorize/.../jankovicsandras/imagetracer/`, with the fork-only
  `getPalette()`/`SVGUtils` calls mapped onto the upstream
  `imageToSVG(image, options, null)`. Self-contained builds beat a fragile remote
  repo.

---

## 4. FAQ

**Q: I added a field to a record and old JSON / some caller broke. Why?**
You reordered/retyped components or dropped the old constructor. Add the field at
the end, keep a secondary constructor defaulting it, and add a wither (see
Principle 3). The polymorphic `Command` round-trips on the `"op"` discriminator —
don't rename the subtypes' JSON names (`MOVE`/`DRAW`/`REFILL`).

**Q: The drawing is mirrored / upside down / in the wrong corner. Where do I look?**
`CoordinateTransform` and the machine origin/orientation + Extra invert/swap flags
— not the GUI. Import flips SVG-Y-down to command-Y-up about the content center;
machine space applies `rotate → swap → invertX → invertY`. The user-facing version
of this is USER_GUIDE Troubleshooting; the code-facing version is ARCHITECTURE §3.

**Q: My new hatch pattern shows up as plain lines.**
You're probably reading `Config.hatchPattern` (vestigial). Selection is
`HatchStyle.patternName()`; register the new `HatchPattern` in `HatchProcessor`'s
switch and read its params off `HatchStyle` (with `0 = auto`).

**Q: There are two "simplify" / "optimize" stages — which one?**
Two pipeline levels. The **toolbox** line-simplify/sort/merge processors run at the
SVG-DOM level *during import* (`svgtoolbox-core`). `OptimizeStage` runs at the
*command-model* level *after* import (`pipeline-core`, also exposed as
`Edit > Optimize Commands (JSON)`). Same idea, different data, different stage —
keep them straight.

**Q: Why is the time estimate "too high" on hatch-dense drawings?**
Because the per-pen-down settle overhead (`penDownDelayMillis`) is charged once per
`DrawCommand` and dominates the distance terms when there are thousands of tiny
strokes. That's correct, and it matches `GcodeBackend.pendown()`'s real dwell —
don't drop it when re-deriving `TimeEstimator`. Weld strokes (`OptimizeStage`) or
lower the dwell to actually reduce it.

**Q: Where does the CLI lag the GUI (and vice-versa)?**
The GUI and CLI now both support per-colour hatch overrides. The CLI also has
multipass, post-import optimization, station mapping from a shared batch config,
and G-code artifact output. Interactive plotting, jog, replay, and visual canvas
placement remain GUI-only by design.

**Q: How do I verify a GUI change without a plotter?**
Mock backend (Settings → Mock backend, or `config.json` `"mock": true`) + the Xvfb
recipe in §3. Run the relevant `TS-*` scripts from `docs/TESTING.md`; the
hardware-marked ones still need a real machine before a hardware release.

---

## 5. Where the docs live

| Doc | Purpose |
|---|---|
| `README.md` | Elevator pitch, module table, build/scripts |
| `docs/ARCHITECTURE.md` | Module graph, data model, control/data flow, threading, extension points, "where to look by task" |
| `docs/LESSONS_LEARNED.md` | *(this file)* design principles, fixed-bug ledger, dev/verification gotchas, FAQ |
| `docs/USER_GUIDE.md` | Operator instructions (rendered in-app via Help > User Guide) |
| `docs/TESTING.md` | Automated-test inventory + the `TS-*` human test-script suite |
| `ROADMAP.md` | Phase history and design rationale; what's done vs planned |
