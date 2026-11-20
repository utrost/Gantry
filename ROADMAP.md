# Gantry — Active Roadmap

This is the current product plan. The original phase-by-phase design diary is
preserved in [`docs/ROADMAP_HISTORY.md`](docs/ROADMAP_HISTORY.md) and is
deprecated as a source of current status.

## Current baseline

Gantry already provides the full SVG/image-to-plot workflow: SVG processing
(including optional hand-drawn styling),
raster vectorization with live preview, command editing, hatching, optimization,
watercolor station mapping/refill, G-code streaming/export/replay, plot progress,
machine setup/calibration/pre-flight wizards, travel visualization, and
GRBL cancellation/error handling. Its machine-aware preview also shows ordered
layers with source pen colours, visibility, and physical nib widths that remain
constant when artwork is scaled.

Windows/Linux installers are published through GitHub Releases. RC11 adds stable
manual-control layout and clearer unavailable-action feedback to the RC10
workflow fixes and RC9 pen-aware filling. Native installation, upgrade,
bundled-runtime launch, and persisted state checks pass in release CI. The
maintainer reported Windows 11 / iDraw H A1 plotting, stop/alarm recovery,
limits and calibration on RC2; the changed RC11 behavior still needs physical
acceptance. See the [current acceptance record](docs/release-results/1.0.0-rc.11.md).
Beginner usability is closed as described in [`docs/USABILITY.md`](docs/USABILITY.md).

## Delivery plan

| # | Milestone | Exit criteria | Status |
|---|---|---|---|
| 1 | Per-colour hatch GUI | Discovered/manual colours map pattern, angle, and gap into `Config.overrides`; CLI regression and GUI mapping tests pass | Complete |
| 2 | Roadmap reconciliation | Active plan is concise; historical phase diary is explicitly deprecated and archived | Complete |
| 3 | Project/session persistence | `.gantry` files preserve commands, placement, selected layers, passes, and source/vectorizer provenance; command JSON is clearly a flattened interchange export | Complete |
| 4 | Undo/recovery | Multi-level undo/redo covers model edits; dirty state, close protection, and recovery autosave protect unsaved work | Complete for model/project state and artwork placement edits |
| 5 | Release readiness | Non-SNAPSHOT version, repeatable release artifacts, checksums, and a recorded acceptance template/workflow | RC11 published; automated installer checks pass; RC2 Windows 11 / iDraw H A1 hardware report recorded; RC11 physical and external-user acceptance pending |
| 6 | Focused polish | Full SVG colour discovery for hatch overrides, accurate travel accounting/labeling, and vectorizer crop restoration | Complete |
| 7 | CLI batch artifacts | Post-import optimize, shared config/station mapping, and optional G-code output work headlessly with end-to-end tests | Complete |
| 8 | Exact-job history | Re-plot uses an immutable prepared-job snapshot; recent successful jobs persist and can be reopened/replotted | Complete |
| 9 | Beginner usability | A default basic workflow leads from artwork to a safe first plot with progressive disclosure, plain language, actionable guidance, safe import defaults, visible feedback, and novice acceptance evidence; advanced workflows remain available | Closed by product-owner decision; Slices A–C and processor-studio Slice E complete. Novice-study protocol remains available as adoption validation, not a blocker for this milestone. |

## Delivered on main after RC11

[PR #77](https://github.com/utrost/Gantry/pull/77) merged at `3462659`: reviewed
SVG/ZIP export, reusable named pens, advisory whole-job ink overlap and editable
stroke text/basic shapes. Java 17/21 GUI checks and Windows/Linux installer/upgrade
checks passed. Local evidence records 574 Java tests and nine packaging tests.
RC11 remains the public download; these additions are not in its preserved assets.
See the [integration record](docs/release-results/2026-11-20-integration.md).

- **Named pens #69 and advisory overlap #70:** software delivered; physical
  pen/paper evaluation remains operator evidence, not a drying guarantee.
- **SVG export #68:** implemented, but reopened for file-count feedback and
  explicit excluded/empty-layer acceptance tests.
- **Quick artwork #72:** software criteria complete; one actual user task remains.

## Now — software work without a plotter

| Priority | Work | Exit criteria |
|---|---|---|
| P1 | [#78 — Mock Stop/interruption](https://github.com/utrost/Gantry/issues/78) | Halt/interrupt freezes simulated position; no jump to destination; pen recovery and repeated Stop have deterministic tests |
| P2 | [#79 — Backend contracts and simulation fidelity](https://github.com/utrost/Gantry/issues/79) | Coherent connection/Run/Idle/pen state, honest raw-command support and reusable production-backend/fake-serial failure checks; builds on #78 |
| P2 | [#68 — Finish reviewed SVG export acceptance](https://github.com/utrost/Gantry/issues/68) | Success reports destination and output count; excluded/empty layers have explicit tests; atomic output and round-trip guarantees remain intact |

The [mock audit](docs/release-results/2026-11-20-mock-fidelity.md) reproduced
no-op halt, interruption advancing to the endpoint, false Idle reports, raw
commands acknowledged without execution and unenforced disconnect state. GUI
mock passes establish the tested workflows. Production-backend fake-serial tests
cover scripted protocol scenarios. Neither establishes physical behavior.

## Operator and participant acceptance

These remain open; software implementation must not be mistaken for acceptance.
Use the [operator checklist](docs/OPERATOR_ACCEPTANCE.md).

| Issue | Remaining evidence |
|---|---|
| [#39](https://github.com/utrost/Gantry/issues/39) | Current Windows 11/iDraw H A1 manual-operation Stop/recovery run |
| [#43](https://github.com/utrost/Gantry/issues/43) | Actual native scaling and minimum-workspace checks; virtual 125/150% requests rendered at 100% |
| [#54](https://github.com/utrost/Gantry/issues/54) | Release-specific operator matrix and expert workflow benchmark; links #39, #43 and #16 |
| [#16](https://github.com/utrost/Gantry/issues/16) | Five actual novice participants; adoption evidence for the already-closed usability milestone |
| [#72](https://github.com/utrost/Gantry/issues/72) | At least one real text/shape preparation task; further drawing tools wait for feedback |
| [#33](https://github.com/utrost/Gantry/issues/33) | GUI audit umbrella; closes when its remaining acceptance issues have evidence |

Before a new candidate is published, select an unused version, record its exact
commit and installer checks, and preserve all published assets. Broad hardware,
novice or stable-readiness claims require the corresponding acceptance records.

## Next — validate existing capability

[#80 — Real-image validation corpus](https://github.com/utrost/Gantry/issues/80)
tracks a licensed, committed sample set and reproducible comparisons of Sketch,
Squiggle, Needles and Isolines through the existing CLI metrics/reporting chain.
It can run without hardware, after simulation correctness and release hardening.
Do not add another engine or promote beginner defaults before this evidence.

Recruit the first independent tester using only the release link and FIRST_PLOT;
record completion, coaching and friction. User-count exit criteria belong in the
[adoption roadmap](docs/ADOPTION_ROADMAP.md); no external-user milestone is claimed
from maintainer or automated runs.

## Later — candidates, not commitments

[#71 — Drying-aware planning](https://github.com/utrost/Gantry/issues/71) stays
deferred: #70 provides advisory geometry, but physical pen/paper assumptions still
need review before executable waits or reordering are scheduled.

Composition handles, extra formats/backends, new image-art engines, tone fidelity,
continuity controls and sampler/renderer ideas are preserved in
[FEATURE_CANDIDATES.md](docs/FEATURE_CANDIDATES.md). Tracer capture remains a
[documented future handoff](docs/TRACER_CAPTURE_IMPORT.md). Restart-resume, pressure
motion, automatic safe-Z, AxiDraw and full vector-node editing remain deferred.

## Roadmap maintenance rules

- Status requires code, tests and a user-facing entry point; closure also requires
  every applicable acceptance criterion. Reopen a delivered issue when residual
  criteria were missed rather than silently deleting them.
- Distinguish published release, merged software, simulated/protocol evidence and
  physical/participant acceptance.
- Keep active priorities here, user-count plans in ADOPTION_ROADMAP, candidate
  research in FEATURE_CANDIDATES and implementation history in ROADMAP_HISTORY.
- Preserve issue/release history; remove duplication and stale status claims.
