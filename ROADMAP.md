# Gantry — Active Roadmap

This is the current product plan. The original phase-by-phase design diary is
preserved in [`docs/ROADMAP_HISTORY.md`](docs/ROADMAP_HISTORY.md) and is
deprecated as a source of current status.

## Current baseline

Gantry already provides the full SVG/image-to-plot workflow: SVG processing,
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
| 9 | Beginner usability | A default basic workflow leads from artwork to a safe first plot with progressive disclosure, plain language, actionable guidance, safe import defaults, visible feedback, and novice acceptance evidence; advanced workflows remain available | In progress — Slices A–C complete; Slice D validation prepared, five-person study pending |

## After these milestones

### Validate before scheduling

- Multi-document composition: first validate a real sticker-sheet or mixed-art
  workflow; prefer a small append/compose feature over a full editor initially.
- Same-colour SVG group/element hatch overrides: click-to-hatch already covers
  most interactive use cases; build group overrides only for repeatable batch
  processing demand.
- Network/TCP GRBL backend: useful only for supported real hardware.
- DXF/HPGL import: demand-driven; SVG remains the canonical interchange.

### Deliberately deferred

- Resume across application restarts: controller/head state recovery is unsafe
  without a proven hardware protocol.
- Pen pressure/per-stroke feed modeling, automatic safe-Z optimization, AxiDraw,
  and full vector node editing.

## Roadmap maintenance rules

- Status is based on code, tests, and a user-facing entry point—not intention.
- A milestone may be complete with explicitly rejected/deferred subfeatures.
- Historical implementation notes belong in `docs/ROADMAP_HISTORY.md`.
- Hardware-dependent completion requires a recorded hardware acceptance run.
