# Gantry — Active Roadmap

This is the current product plan. The original phase-by-phase design diary is
preserved in [`docs/ROADMAP_HISTORY.md`](docs/ROADMAP_HISTORY.md) and is
deprecated as a source of current status.

## Current baseline

Gantry already provides the full SVG/image-to-plot workflow: SVG processing
(including optional hand-drawn styling),
raster vectorization with live preview, command editing, hatching, optimization,
watercolor station mapping/refill, G-code streaming/export/replay, plot progress,
machine setup/calibration/pre-flight wizards, travel visualization, and safe
GRBL cancellation/error handling.

The next active work is usability hardening for casual and first-time operators,
not another plotting feature or geometry subsystem. The detailed scope and
acceptance plan lives in [`docs/USABILITY.md`](docs/USABILITY.md).

## Delivery plan

| # | Milestone | Exit criteria | Status |
|---|---|---|---|
| 1 | Per-colour hatch GUI | Discovered/manual colours map pattern, angle, and gap into `Config.overrides`; CLI regression and GUI mapping tests pass | Complete |
| 2 | Roadmap reconciliation | Active plan is concise; historical phase diary is explicitly deprecated and archived | Complete |
| 3 | Project/session persistence | `.gantry` files preserve commands, placement, selected layers, passes, and source/vectorizer provenance; command JSON is clearly a flattened interchange export | Complete |
| 4 | Undo/recovery | Multi-level undo/redo covers model edits; dirty state, close protection, and recovery autosave protect unsaved work | Complete for model/project state; canvas gestures remain direct manipulation |
| 5 | Release readiness | Non-SNAPSHOT version, repeatable release artifacts, checksums, and a recorded acceptance template/workflow | Code complete; real-hardware acceptance required per release |
| 6 | Focused polish | Full SVG colour discovery for hatch overrides, accurate travel accounting/labeling, and vectorizer crop restoration | Complete |
| 7 | CLI batch artifacts | Post-import optimize, shared config/station mapping, and optional G-code output work headlessly with end-to-end tests | Complete |
| 8 | Exact-job history | Re-plot uses an immutable prepared-job snapshot; recent successful jobs persist and can be reopened/replotted | Complete |
| 9 | Beginner usability | A default basic workflow leads from artwork to a safe first plot with progressive disclosure, plain language, actionable guidance, safe import defaults, visible feedback, and novice acceptance evidence; advanced workflows remain available | In progress — Slices A–C and processor-studio Slice E complete; Slice D validation and five-person study pending |

## Adoption roadmap

The product roadmap below tracks what Gantry should become. The adoption plan in
[`docs/ADOPTION_ROADMAP.md`](docs/ADOPTION_ROADMAP.md) tracks how to get from a
capable personal tool to 2, 10, 25, and 100 real users through packaging,
validation, support, compatibility evidence, and low-key public outreach.

## After these milestones

### Validate before scheduling

- Multi-document composition: first validate a real sticker-sheet or mixed-art
  workflow; prefer a small append/compose feature over a full editor initially.
- Same-colour SVG group/element hatch overrides: click-to-hatch already covers
  most interactive use cases; build group overrides only for repeatable batch
  processing demand.
- Network/TCP GRBL backend: useful only for supported real hardware.
- DXF/HPGL import: demand-driven; SVG remains the canonical interchange.
- Tracer capture import (`.tracer.json`): validate with a real
  Tracer-to-plotter experiment first. See
  [`docs/TRACER_CAPTURE_IMPORT.md`](docs/TRACER_CAPTURE_IMPORT.md). Initial
  import should preserve centerline layer/stroke order and pressure metadata;
  pressure-to-Z/feed behavior needs calibration and hardware evidence.

### Plotter-Studio-inspired backlog

The open-source [`iclubu/Plotter-Studio`](https://github.com/iclubu/Plotter-Studio)
project is closer to Gantry than to GenerativeArt: it is an image-to-plotter
studio with Streamlit image-art engines, vpype-style export optimization,
CMYK/layer output, pen passes, and a lightweight serial backend. Use it as a
feature reference only; do not copy code unless licensing is clarified.

These ideas are useful, but they should not interrupt Milestone 9 beginner
usability. Treat them as post-validation candidates and route them through the
existing Gantry architecture: `vectorize` for raster-to-line art, `svgtoolbox-core`
for SVG preprocessing, `pipeline-core` for command transforms, and `plotter`/`app`
for machine-aware execution.

- Image-art engines in the vectorizer/import workflow:
  - **Implemented experimental modes:** **Squiggle shading**, **Oriented
    needles**, **Tonal isolines/topographic contours**, and **Sketch/blueprint
    trace** now exist in the vectorizer, CLI, metadata replay, and live preview
    studio. They still need real-image sample evidence, plot-time/plottability
    review, and beginner-facing preset tuning before they should be treated as
    validated defaults.
  - **Continuous-line image mode**: image-weighted point placement plus a greedy
    nearest-neighbour route for one-pen-down drawings; validate performance and
    plot quality before scheduling. Fast Marching/topographic wavefront output is
    interesting, but should wait until there is a Java-compatible algorithmic
    plan and a real use case.
- Image import preparation controls: crop/mask before vectorization, transparent
  pixel skipping, and beginner-facing presets such as **Portrait lines**,
  **Sketch trace**, **Squiggle shading**, and **Dense stipple**. Start with
  numeric crop/margin controls before a complex visual crop editor.
- Plotter metrics surfaced earlier in the workflow: draw distance, pen-up travel,
  estimated time, command count, layer count, refill/pen-change count, tiny
  segment count, and high-density warnings. Gantry already owns the command model
  and machine settings, so these metrics belong in import/processing/preflight
  rather than in GenerativeArt.
- vpype-style optimization vocabulary in UI/docs: explain Gantry's native merge,
  sort, simplify, reloop, and two-opt-like improvements using plotter-community
  language while keeping the Java implementation and tests authoritative.
- Multi-pen/layer job output: per-layer/per-colour SVG or G-code export, a
  combined job with explicit pen-change pauses, ordered layer previews, and
  generated setup notes. Prefer Gantry's station/layer model over literal CMYK
  unless the source is a colour image.
- Multipass as an art-facing feature: presets for single fineliner, ballpoint
  shadow boost, heavy black fill, and light watercolor wash; cap pass counts and
  warn when passes multiply draw time or risk over-inking paper.
- Local helper boundary: if browser/static tools such as GenerativeArt need
  vpype-like or machine-aware processing later, Gantry should be the local helper
  or canonical importer/exporter rather than duplicating hardware logic in the
  browser app.

Candidate first validation slice after Milestone 9: productize the new
image-art modes rather than adding another unvalidated engine. Commit a small
sample corpus, expose before/after plotter metrics, verify headless G-code export
for **Squiggle shading**, **Oriented needles**, **Tonal isolines/topographic
contours**, and **Sketch/blueprint trace**, and record deterministic geometry,
bounds, and plot-time estimate tests. The headless validation spine now exists:
`SvgImportCli --metrics` writes JSON with layers, command/stroke/point counts,
draw/travel distance, travel ratio,
tiny segments, bounds, and — when a batch `--config` is supplied — a
feed-rate-based plot-time estimate and machine-readable plottability warnings
with measured values and thresholds (`HIGH_TRAVEL_RATIO`, `TINY_SEGMENTS`,
`LONG_PLOT_TIME`) for any chained `VectorizeCli` output. `ImageArtValidationCli`
aggregates those sidecars into a compact comparison report that identifies the
lowest-travel, fastest-estimated, and fewest-tiny-segment artifact. The remaining
validation work is to run that spine on a small committed real-image corpus and
record the evidence before treating the modes as defaults. After that evidence,
the next new algorithm candidate is **Continuous-line image mode**.

### Deliberately deferred

- Resume across application restarts: controller/head state recovery is unsafe
  without a proven hardware protocol.
- Pen pressure/per-stroke feed modeling, automatic safe-Z optimization, AxiDraw,
  and full vector node editing. Tracer capture import may preserve pressure data
  before these machine-expression features exist.

## Roadmap maintenance rules

- Status is based on code, tests, and a user-facing entry point—not intention.
- A milestone may be complete with explicitly rejected/deferred subfeatures.
- Historical implementation notes belong in `docs/ROADMAP_HISTORY.md`.
- Hardware-dependent completion requires a recorded hardware acceptance run.
