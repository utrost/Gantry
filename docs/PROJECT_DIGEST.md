# Gantry project digest — documentation and roadmap

This digest captures the current shape of Gantry so future documentation and roadmap work can start from the same map. It is a navigation aid, not a substitute for the code or the detailed guides.

## Current position

Gantry is a Java 17 / Maven multi-module plotting studio for GRBL pen plotters. It combines SVG preparation, raster-to-SVG vectorization, editable project/session state, safe pre-plot checks, G-code export/streaming/replay, optional watercolor station workflows, and a beginner-aware Swing/FlatLaf GUI.

The project has moved past core capability discovery. The active challenge is adoption confidence: can a person outside the primary development context download Gantry, launch it, complete the guided mock first plot, and report useful friction without verbal coaching?

## Repository map

- `README.md` — public entry point: positioning, download/start path, module overview, docs index.
- `ROADMAP.md` — active product roadmap; keep it concise and status-oriented.
- `docs/ADOPTION_ROADMAP.md` — adoption plan for 2 / 10 / 25 / 100 users.
- `docs/FIRST_PLOT.md` — shortest safe path for a new tester.
- `docs/USER_GUIDE.md` — full operator guide.
- `docs/TROUBLESHOOTING.md` — serial, GRBL, launch, and first-run failure help.
- `docs/KNOWN_GOOD_SETUPS.md` — evidence-based compatibility matrix; includes a maintainer-reported Uuna Tek A1 H setup, with detailed acceptance fields still pending.
- `docs/TESTING.md` — automated coverage inventory and manual acceptance scripts.
- `docs/USABILITY.md` — scoped Beginner Usability milestone, now closed; novice validation remains an adoption-evidence path.
- `docs/TRACER_CAPTURE_IMPORT.md` — future Tracer JSON capture handoff contract; documents pressure-bearing gesture import without claiming current implementation.
- `docs/ARCHITECTURE.md` — developer/LLM technical reference for module graph, data model, pipeline, threading, and extension points.
- `docs/LESSONS_LEARNED.md` — invariants, fixed-bug ledger, development gotchas, and FAQ.
- `docs/ROADMAP_HISTORY.md` — historical phase diary; deprecated as a source of current status.
- `docs/RELEASE_CHECKLIST.md` and `release-results/` — release and acceptance evidence.

## Current implementation baseline

The Maven reactor contains eight modules:

- `model` — shared DTOs and coordinate transforms.
- `svgtoolbox-core` — SVG-to-SVG preparation processors.
- `pipeline-core` — SVG import and command-model transforms.
- `watercolor` — optional station mapping and refill workflow support.
- `plotter` — mock, G-code file, serial GRBL, and replay backends.
- `vectorize` — raster image to SVG front stage.
- `cli` — headless SVG/image batch conversion and optional G-code output.
- `app` — Swing GUI, orchestration, persistence, recovery, history, visualization, and help surface.

RC11 stabilizes manual controls and clarifies disabled actions while retaining
RC10's workflow fixes, pen-aware filling, coverage previews and calibration
profiles. Its release notes record 554 passing Java tests. Release CI verifies
Java 17/21, GUI workflows and native Windows/Linux packaging; see the
[RC11 record](release-results/1.0.0-rc.11.md) for evidence and limitations.

## Roadmap state

Active product roadmap:

- Milestones 1–9 are complete, code-complete, or closed by product-owner decision.
- Beginner usability is no longer the active product-roadmap blocker.
- Slices A–C and processor-studio Slice E are documented as complete.
- The five-person novice study remains useful adoption evidence, but it no longer blocks Milestone 9 closure.

Active adoption roadmap:

- Native Windows MSI/Linux DEB releases and portable GUI/CLI JARs are published through GitHub Releases; RC11 is the current candidate.
- Mock first-plot video, screenshots, offline help and SVG samples ship with the documented workflow.
- Windows 11 / iDraw H A1 RC2 hardware success is recorded; changed RC11 behavior still needs physical confirmation.
- External tester recruitment, structured novice evidence and additional known-good setups remain adoption work.

## Documentation health

Strengths:

- The docs have a clear split between active roadmap, adoption roadmap, historical roadmap, user guide, architecture reference, testing guide, and release evidence.
- Current docs consistently avoid overstating hardware readiness; alpha release notes and result files separate mock/CI confidence from real-machine acceptance.
- Beginner flow is documented from multiple angles: first-run guide, user guide, usability scope, and manual test scripts.
- Architecture documentation is unusually complete and should be read before code changes.

Risks:

- There is a lot of documentation. For a new tester, README → FIRST_PLOT must stay the obvious path; everything else should remain secondary.
- The adoption roadmap contains issue numbers and release details that can drift unless checked after GitHub changes.
- `docs/KNOWN_GOOD_SETUPS.md` has a maintainer-reported Uuna Tek A1 H setup, but it should not be marketed as broad compatibility evidence until detailed acceptance fields and external entries land.
- Current screenshot views were refreshed during RC12 preparation and are available in
  [the screenshot gallery](images/README.md). The refreshed mock video is available;
  external novice evidence remains pending.
- The user guide is comprehensive, but long. If adoption reaches broader public testing, split it into focused pages only after real users show where they get lost.
- Tracer JSON capture is now documented as a future pressure-bearing import path. Keep it out of current user instructions until implemented and tested.

## Development integration — 2026-11-20

Reviewed SVG/ZIP export, shared named pens, advisory ink overlap and editable
quick artwork are merged on main at `3462659`. SVG export #68 now also has
destination/count feedback and explicit excluded/empty-layer acceptance; see the
[verification record](release-results/2026-11-20-svg-export.md). #69/#70 remain
software-complete. RC11 remains
the public download. See the [integration evidence](release-results/2026-11-20-integration.md)
and [operator checklist](OPERATOR_ACCEPTANCE.md). Software verification does not
close the hardware/native-scaling/participant gates in #39, #43, #54 and #16,
or the real-user-task criterion in #72. Drying-aware execution (#71) is deferred.

## Recommended next work

Mock Stop/interruption and backend contracts (#78/#79) are implemented with
controlled-motion, shared-backend and scripted serial fault regressions. See
[BACKEND_CONTRACT.md](BACKEND_CONTRACT.md) and the
[verification record](release-results/2026-11-20-backend-contracts.md).

1. The five independent CLI issues #84/#88–#91 are fixed: PNG packaging,
   complete saved recipes, overrides, batch failure status and collision/source
   protection. See the [verification record](release-results/2026-11-20-cli-reliability.md).
   Squiggle #85 retains its [quality/cost review](validation/image-art/squiggle-fix/README.md).
   RC12 is staged as a verified draft at `8dcb772`, with current screenshots and an
   [operator worksheet](release-results/1.0.0-rc.12-operator.md). Visual review found
   [#93](https://github.com/utrost/Gantry/issues/93), a document/workspace/export
   orientation mismatch. Versioned upright defaults now address the software
   criteria while preserving unversioned profiles and recorded jobs. See the
   [upright-profile verification](release-results/2026-11-21-upright-profiles.md).
   Physical direction/frame/plot acceptance remains open. RC12 assets predate the
   fixes and remain unchanged; public distribution stays held. See the
   [candidate record](release-results/1.0.0-rc.12.md).
2. Use [OPERATOR_ACCEPTANCE.md](OPERATOR_ACCEPTANCE.md) for hardware, native scaling
   and expert checks (#39/#43/#54). Keep the first independent tester and
   five-person study (#16) separate from mock evidence; #72 still needs a user task.
3. Keep drying-aware execution (#71) deferred pending coverage review and
   physical pen/paper evidence. Preserve wider research in
   [FEATURE_CANDIDATES.md](FEATURE_CANDIDATES.md), rather than expanding the
   active roadmap with unscheduled algorithms.

## Decision frame for incoming work

Favor work that helps another person complete a safe first plot:

- install/run friction;
- serial and GRBL troubleshooting;
- clearer first-run text;
- screenshots/demo evidence;
- compatibility records;
- support diagnostics;
- test cases that preserve safety and beginner flow.

Defer work that expands capability without adoption evidence:

- new import formats;
- new backends;
- full vector editing;
- restart-resume for machine jobs;
- plugin architecture;
- broad UI restyling unrelated to comprehension or safety.

Exception: Tracer `.tracer.json` is worth keeping as a named candidate because it preserves centerline pressure and timing that SVG throws away. It should still wait for either adoption evidence or a concrete Tracer-to-plotter experiment.

## Distribution checks

RC11 already publishes bundled-runtime Windows MSI and Linux DEB packages,
portable GUI/CLI JARs and an offline documentation ZIP. Development changes are
not part of those preserved published assets.
Every release should verify the native install/upgrade/uninstall checks, GUI and
CLI JARs, source build helpers, and exact published artifact names. See the
[release checklist](RELEASE_CHECKLIST.md) and [documentation audit](DOCUMENTATION_STATUS.md).
