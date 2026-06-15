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
| **6. Cutover** | Delete `legacy/`; docs; single-artifact release | One JAR, no Python anywhere |

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
