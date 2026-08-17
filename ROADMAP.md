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
| 9 | Beginner usability | A default basic workflow leads from artwork to a safe first plot with progressive disclosure, plain language, actionable guidance, safe import defaults, visible feedback, and novice acceptance evidence; advanced workflows remain available | Closed by product-owner decision; Slices A–C and processor-studio Slice E complete. Novice-study protocol remains available as adoption validation, not a blocker for this milestone. |

## Adoption roadmap

The product roadmap below tracks what Gantry should become. The adoption plan in
[`docs/ADOPTION_ROADMAP.md`](docs/ADOPTION_ROADMAP.md) tracks how to get from a
capable personal tool to 2, 10, 25, and 100 real users through packaging,
validation, support, compatibility evidence, and low-key public outreach.

## After these milestones

### Validate before scheduling

- Multi-document composition: first validation slice is now implemented as
  **File > Append SVG to Current Artwork...**. It appends another SVG as
  additional layers, places it to the right of the current drawing with a 10 mm
  gap, remaps command IDs, is undoable, and persists in `.gantry` projects.
  Future work can add per-artwork dragging/placement if dogfooding proves the
  fixed side-by-side append is not enough.
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

These ideas are useful, but they should not interrupt adoption validation or
release hardening. Treat them as post-validation candidates and route them through the
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

### DrawingBotV3 PFM-informed image-art direction

The [DrawingBotV3 Path Finding Modules documentation](https://docs.drawingbotv3.com/en/latest/pfms.html#pfms)
is a useful mature reference for image-to-geometry generators. Treat it as a
conceptual design reference only. Do not copy code or assume its complete PFM zoo
belongs in Gantry. Its main lesson is architectural: build reusable primitives
for sampling, mark generation, tone management, continuity, and validation rather
than adding isolated one-off raster processors.

#### Borrowed architecture concepts

- **Separate sampler from renderer.** DrawingBotV3 repeatedly combines a point or
  path distribution strategy with different mark renderers. Gantry should move in
  the same direction once the current modes are validated: samplers such as grid,
  adaptive, LBG, Voronoi, streamline seeds, or spiral paths should feed renderers
  such as dots, dashes, shapes, scribbles, trees, triangulation, hatches, or
  TSP/continuous routes.
- **Use a working-image erasing model for density.** Sketch-style PFMs repeatedly
  draw over dark regions and brighten the covered area in a lightened working
  image. This is a stronger density/overlap control than a static threshold and
  should inform future sketch/squiggle refinements: darker areas invite more
  strokes until local tone has been consumed.
- **Make continuity an explicit trade-off.** DrawingBotV3 exposes concepts such as
  pen lifting, linked hatch ends, squiggle min/max length, and max deviation.
  Gantry should surface the same choices in plotter language: fewer pen lifts and
  lower travel ratio versus possible loss of detail or tone accuracy.
- **Add tone-fidelity validation next to plotter metrics.** Gantry now measures
  command/stroke/point counts, draw distance, travel distance, plot-time estimates,
  tiny segments, and warnings. DrawingBotV3's tone-map idea suggests the next
  validation layer: rasterize generated output, blur/normalize it, compare it with
  the source luminance map, and report tone error, contrast drift, and over-dark or
  under-dark regions. Presets should eventually be tuned against both plotter
  practicality and tone fidelity.
- **Prefer reusable image-art families over a PFM zoo.** Gantry should not expose
  dozens of algorithms to beginners. Instead, it should keep a small set of
  beginner presets and let advanced mode reveal the sampler/renderer/tone/routing
  components that compose them.

#### DrawingBotV3 family digest and Gantry relevance

- **Sketch PFMs.** These find dark image regions, choose a dark pixel, trace the
  next dark line/curve, brighten the covered area, and repeat until a line-density
  or max-line limit is reached. Variants include lines, curves, squares,
  quadratic/cubic Béziers, Catmull-Rom curves, shapes, Sobel edges, waves, flow
  fields, superformula paths, and sweeping curves. Important controls include
  plotting resolution, random seed, directionality, clarity/unsharp mask,
  distortion, angularity, edge/Sobel/luminance power, line density, min/max line
  length, angle tests, squiggle length/deviation, erasing strength/radius, tone,
  and optional shading. Gantry relevance: closest to **Sketch/blueprint trace**,
  **Squiggle shading**, and **Oriented needles**, but Gantry's current sketch
  trace is threshold/skeleton based. The useful next idea is not another curve
  variant; it is a working-image erasing density model and continuity controls.
- **Streamline PFMs.** These generate non-overlapping streamlines whose spacing is
  driven by image brightness and whose direction is taken from a vector field such
  as Edge Tangent Flow, a procedural flow field, or a superformula field. Important
  controls include min/max spacing, min/max length, tone, distortion, edge-field
  power, ETF iterations/radius, and post-blur. Gantry relevance: the strongest
  future artistic candidate is **Streamlines Edge Field / flowline portrait**,
  because it follows image structure rather than decorative noise. A Gantry slice
  would compute grayscale gradients, build/smooth a tangent vector field, seed
  streamlines in dark regions, enforce spacing/no-overlap, emit polylines, and
  validate with plot metrics plus tone-fidelity metrics.
- **Spiral PFMs.** These follow a spiral path, sample brightness along it, and draw
  perpendicular marks or circular scribbles whose amplitude/velocity follows the
  sampled luminance. Variants include sawtooth and circular scribble spirals.
  Important controls include spiral type, center, ring spacing, amplitude,
  min/max velocity, ignore-white behavior, and connected-line output. Gantry
  relevance: a strong plotter-practical candidate because it naturally minimizes
  pen lifts and should produce low travel ratios. It is less general than a TSP
  portrait but easier to validate and explain.
- **Hatch PFMs.** These lay hatching lines across the image and modulate them into
  waves or scribbles based on luminance. Important controls include line spacing,
  angle, crosshatch, linked ends, amplitude, min/max velocity, and curve tension.
  Gantry relevance: Gantry already has SVG hatching, but not a raster-image
  hatch-art generator. A future raster hatch processor could reuse Gantry's hatch
  vocabulary while adding image-driven amplitude/velocity and an explicit
  link-ends option for low-travel plotting.
- **Adaptive PFMs.** These add a tone-mapping stage before generating output: build
  a reference/drawing/blurred tone map, adjust the input image to compensate for
  how the chosen drawing style reproduces tone, then place evenly distributed
  points and render a style. Variants include circular scribbles, shapes,
  triangulation, trees, stippling, dashes, letters, diagrams, and TSP. Important
  controls include min/max sample radius, brightness, contrast, ignore-white, and
  renderer-specific controls. Gantry relevance: the tone-map idea is more
  valuable immediately than the individual renderers. It should drive a future
  **tone-fidelity metrics** slice before treating image-art modes as validated
  defaults.
- **LBG PFMs.** Linde-Buzo-Gray sampling combines some speed of adaptive sampling
  with better point placement for detail retention, especially with large changes
  in desired stipple spacing. Controls include stipple radius min/max, density,
  threshold, max iterations, and cache-result. Variants mirror adaptive outputs:
  scribbles, shapes, triangulation, tree, stippling, dashes, letters, diagram,
  and TSP. Gantry relevance: if Gantry adds stipple, dense dash, or TSP/continuous
  routes, LBG sampling is likely a better first infrastructure target than full
  weighted Voronoi relaxation.
- **Voronoi PFMs.** These scatter points according to brightness, build a weighted
  Voronoi diagram, compute weighted centroids from luminance, rebuild the diagram,
  and iterate. Variants include shapes, triangulation, tree, stippling, dashes,
  diagram, and TSP. Important controls include point density, point limit,
  luminance/density power, iterations, accuracy, and ignore-white. Gantry
  relevance: powerful for high-quality stippling and TSP portraits, but heavier.
  Defer until the sampler/renderer abstraction and validation corpus exist.
- **Grid PFMs.** These start from a regular or perturbed grid and use brightness,
  contrast, threshold, threshold feathering, convergence, and shape scale to draw
  shapes, dashes, or letters. Controls include X/Y spacing, random offset,
  interleave, concentric fills, and convergence toward darker regions. Gantry
  relevance: this is the easiest reliable future engine family. A **Grid dashes /
  halftone** processor would be deterministic, fast, beginner-friendly, and easy
  to compare with plot metrics.
- **Composite PFMs.** These combine multiple drawing styles, either as mosaics
  that divide the image into rectangles/Voronoi/triangles/SLIC segments or as
  layered full-image styles. Controls include drawing-style lists, weights,
  outlines, nested composites, and whether to keep the lightened image between
  layers. Gantry relevance: powerful but risky. Defer broad composites; later,
  support a limited **layered style recipe** such as isolines + sketch edges +
  light squiggle shading, with per-layer metrics and an aggregate validation
  report.
- **Special PFMs.** DrawingBotV3 includes edge/contour/shading composites, SVG
  conversion with hatch fills and color-derived drawing sets, and pen calibration.
  Gantry already overlaps with SVG import, hatching, station/layer mapping,
  calibration, and edge/contour vectorization. The useful roadmap signal is to
  strengthen Gantry's multi-pen/layer outputs and calibration evidence rather than
  add another special-case raster engine.

#### Prioritized Gantry image-art sequence

1. **Validate the existing four experimental modes first.** Build a small committed
   real-image corpus, run **Sketch**, **Squiggle**, **Needles**, and **Isolines**
   through `VectorizeCli` → `SvgImportCli --metrics` → optional G-code →
   `ImageArtValidationCli`, and record evidence before making any of them default
   beginner presets.
2. **Add tone-fidelity metrics.** Rasterize generated output or command previews,
   compare blurred luminance against the source image, and report tone error,
   contrast drift, over-dark, and under-dark areas. Use this next to existing
   plottability metrics when tuning presets.
3. **Improve sketch/squiggle density with working-image erasing.** Use the
   DrawingBotV3 lightened-image idea to control overlap and local tone buildup,
   then validate that it improves tone fidelity without exploding travel ratio or
   tiny-segment warnings.
4. **Add explicit continuity controls.** Surface options such as should-lift-pen,
   max continuous stroke length, max brightness deviation, and link-hatch-ends in
   plotter terms, and tie them to travel-ratio/time warnings.
5. **Introduce the next algorithm only after validation evidence.** Candidate
   ranking:
   - **Streamlines Edge Field / flowline portrait:** best artistic upgrade;
     image-structure aware; more complex but justified for portraits/figures.
   - **Spiral or raster hatch sawtooth:** best plotter-practical upgrade;
     naturally low pen-lift and easy to validate.
   - **Grid dashes / halftone:** easiest reliable implementation; deterministic,
     fast, and beginner-friendly.
   - **LBG stipple / TSP:** strong future direction once sampling/routing
     infrastructure exists.
   - **Voronoi family:** high-quality but heavier; keep for later.
6. **Longer-term architecture:** evolve toward sampler + mark renderer + tone
   policy + routing policy + validation report. Keep the beginner UI preset-led,
   with the composable architecture exposed only in advanced controls.

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
choose the next algorithm from the prioritized DrawingBotV3-informed sequence
above; **Continuous-line image mode** remains a named candidate, but streamlines,
spiral/hatch, grid, or LBG/TSP may be better first depending on validation
findings.

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
