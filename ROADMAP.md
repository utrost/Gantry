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

RC14 is the current published testing prerelease, combining the RC13 coordinate/preview/timing
baseline with station/homing corrections, completion barriers, serial simulation
and #113–#115 firmware review, placement anchors and reviewed partial restart.
The release process verifies Windows/Linux installers and upgrades. Physical,
native-desktop and external-user acceptance remain open. Older releases and the
held RC12 draft remain unchanged. See the
[current candidate record](docs/release-results/1.0.0-rc.14.md) and
[documentation status](docs/DOCUMENTATION_STATUS.md).
Beginner usability is closed as described in [`docs/USABILITY.md`](docs/USABILITY.md).

## Delivery plan

| # | Milestone | Exit criteria | Status |
|---|---|---|---|
| 1 | Per-colour hatch GUI | Discovered/manual colours map pattern, angle, and gap into `Config.overrides`; CLI regression and GUI mapping tests pass | Complete |
| 2 | Roadmap reconciliation | Active plan is concise; historical phase diary is explicitly deprecated and archived | Complete |
| 3 | Project/session persistence | `.gantry` files preserve commands, placement, selected layers, passes, and source/vectorizer provenance; command JSON is clearly a flattened interchange export | Complete |
| 4 | Undo/recovery | Multi-level undo/redo covers model edits; dirty state, close protection, and recovery autosave protect unsaved work | Complete for model/project state and artwork placement edits |
| 5 | Release readiness | Non-SNAPSHOT version, repeatable release artifacts, checksums, and a recorded acceptance template/workflow | RC14 testing prerelease published; automated installer checks pass; RC2 hardware report retained; current physical and external-user acceptance pending |
| 6 | Focused polish | Full SVG colour discovery for hatch overrides, accurate travel accounting/labeling, and vectorizer crop restoration | Complete |
| 7 | CLI batch artifacts | Post-import optimize, shared config/station mapping, and optional G-code output work headlessly with end-to-end tests | Complete |
| 8 | Exact-job history | Re-plot uses an immutable prepared-job snapshot; recent successful jobs persist and can be reopened/replotted | Complete |
| 9 | Beginner usability | A default basic workflow leads from artwork to a safe first plot with progressive disclosure, plain language, actionable guidance, safe import defaults, visible feedback, and novice acceptance evidence; advanced workflows remain available | Closed by product-owner decision; Slices A–C and processor-studio Slice E complete. Novice-study protocol remains available as adoption validation, not a blocker for this milestone. |

## Delivered on main after RC11

[PR #77](https://github.com/utrost/Gantry/pull/77) merged at `3462659`: reviewed
SVG/ZIP export, reusable named pens, advisory whole-job ink overlap and editable
stroke text/basic shapes. Java 17/21 GUI checks and Windows/Linux installer/upgrade
checks passed. Local evidence records 574 Java tests and nine packaging tests.
These additions ship in RC13; older release assets remain unchanged.
See the [integration record](docs/release-results/2026-11-20-integration.md).

- **Named pens #69 and advisory overlap #70:** software delivered; physical
  pen/paper evaluation remains operator evidence, not a drying guarantee.
- **SVG export #68:** complete, including destination/SVG-count feedback and
  excluded/empty-layer acceptance. See the
  [verification record](docs/release-results/2026-11-20-svg-export.md).
- **Quick artwork #72:** software criteria complete; one actual user task remains.

## Now — current acceptance and serial reliability

RC13 packages the presentation-only page projection, versioned upright defaults,
explicit legacy-profile migration and the later preview/route corrections. RC12
assets predate these changes and remain unchanged. #93's software criteria are
addressed; physical direction/frame/plot acceptance in #39/#54/#93 remains open.
See [page projection](docs/release-results/2026-11-20-page-projection.md),
[upright-profile verification](docs/release-results/2026-11-21-upright-profiles.md)
and the [RC14 operator worksheet](docs/release-results/1.0.0-rc.14-operator.md).

RC14 includes fixes for #106–#109's watercolor route, cleaning-pot mapping,
station nudge and homing fault waits. #111 adds an explicit planner-completion
barrier before pen handoff/final success; #112 supplies a selectable serial
simulator under the production backend, preserving the lightweight mock. See the
[completion/simulator verification](docs/release-results/2026-11-21-completion-serial-simulator.md).
Both follow-ups are included in RC14; physical acceptance remains open.

Software #113–#115 is delivered: read-only firmware/profile/work-origin envelope
review, nine-point placement with fixed references, and persisted, explicitly
reviewed pen-only partial restart/reverse. Restart never resumes automatically;
watercolor cases are blocked pending a separate loading/cleaning/drying policy.
See the [contracts, tests and actual GUI evidence](docs/release-results/2026-11-22-firmware-anchors-restart.md).
RC14 includes these changes; hardware, paper registration and native scaling
acceptance remain open.

Follow-up #100/#101/#103 fixes route estimates and controller-space preview starts
(initial origin, refill and rinse). Known non-origin initial DRAWs are rejected
without automatic positioning; unknown position keeps an explicit warning. Full station metadata reaches current and
historical views; commands and alignment bounds remain unchanged. Current guides,
all 47 gallery PNGs and the mock walkthrough are refreshed. See
[verification/documentation audit](docs/release-results/2026-11-21-prepared-route.md).

The independently actionable tracked software issues #84 and #88–#91 are
complete: packaged PNG support, honest export failures, reproducible recipes,
explicit rerun overrides, failed-batch status and collision/source protection.
See the [verification record](docs/release-results/2026-11-20-cli-reliability.md).
The remaining external acceptance work needs native desktop, hardware or
participant evidence as listed below.

Mock hardening #78/#79 implements cancellable motion that freezes at the last
reached point, explicit pen/connection/Run/Idle state, supported raw-command
execution and shared production-backend/fake-serial fault tests. See the
[backend contract](docs/BACKEND_CONTRACT.md) and
[verification record](docs/release-results/2026-11-20-backend-contracts.md).
The [original mock audit](docs/release-results/2026-11-20-mock-fidelity.md)
remains historical evidence. Improved simulation and protocol tests still do not
establish physical behavior.

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
is delivered: three licensed photographs, all four modes, exact repeated geometry,
production G-code preflight and per-image visual quality/cost review. See the
[report and all previews](docs/validation/image-art/README.md). Sketch provides
useful sparse outlines; Needles/Isolines have aesthetic and motion/detail costs.
The original Squiggle failed the visual bar. #85 removes the positional mask
and adds connected sketch contours: the unchanged standard recipe now retains
recognizable subjects, at 8–13 minute feed/dwell estimates. See the
[before/after quality and cost review](docs/validation/image-art/squiggle-fix/README.md).
The actual Studio preset was reviewed too; flat dark suppression is explicitly
content-based, not background segmentation. #84 packaged PNG export is fixed; recipe/batch reliability fixes also retain
all reviewed geometry hashes.
No beginner defaults were promoted; physical quality remains operator evidence.

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
[documented future handoff](docs/TRACER_CAPTURE_IMPORT.md). Automatic/unattended
physical resume, watercolor restart, pressure motion, automatic safe-Z, AxiDraw
and full vector-node editing remain deferred.

## Roadmap maintenance rules

- Status requires code, tests and a user-facing entry point; closure also requires
  every applicable acceptance criterion. Reopen a delivered issue when residual
  criteria were missed rather than silently deleting them.
- Distinguish published release, merged software, simulated/protocol evidence and
  physical/participant acceptance.
- Keep active priorities here, user-count plans in ADOPTION_ROADMAP, candidate
  research in FEATURE_CANDIDATES and implementation history in ROADMAP_HISTORY.
- Preserve issue/release history; remove duplication and stale status claims.
