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
- `docs/KNOWN_GOOD_SETUPS.md` — evidence-based compatibility matrix; currently includes Uwe's Uuna Tek A1 H as working, with detailed acceptance fields still pending.
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

Test inventory observed after the sketch-trace image-art update: 79 Java test files across the reactor (`app` 36, `svgtoolbox-core` 17, `vectorize` 9, `plotter` 6, `pipeline-core` 5, `cli` 2, `model` 2, `watercolor` 2). The docs already describe the test suite in detail rather than relying on a raw count.

## Roadmap state

Active product roadmap:

- Milestones 1–9 are complete, code-complete, or closed by product-owner decision.
- Beginner usability is no longer the active product-roadmap blocker.
- Slices A–C and processor-studio Slice E are documented as complete.
- The five-person novice study remains useful adoption evidence, but it no longer blocks Milestone 9 closure.

Active adoption roadmap:

- First public alpha exists: `v1.0.0-alpha.1`.
- Release artifacts/checksums and CLI artifact smoke evidence are recorded.
- GitHub metadata, issue templates, and labels are seeded.
- First-plot quickstart, troubleshooting, Help > Copy Diagnostics, release checklist, and compatibility-matrix shell exist.
- Manual adoption blockers remain: full real-hardware acceptance evidence, external tester recruitment, structured novice-study evidence, first demo GIF/video, and external known-good setup entries.

## Documentation health

Strengths:

- The docs have a clear split between active roadmap, adoption roadmap, historical roadmap, user guide, architecture reference, testing guide, and release evidence.
- Current docs consistently avoid overstating hardware readiness; alpha release notes and result files separate mock/CI confidence from real-machine acceptance.
- Beginner flow is documented from multiple angles: first-run guide, user guide, usability scope, and manual test scripts.
- Architecture documentation is unusually complete and should be read before code changes.

Risks:

- There is a lot of documentation. For a new tester, README → FIRST_PLOT must stay the obvious path; everything else should remain secondary.
- The adoption roadmap contains issue numbers and release details that can drift unless checked after GitHub changes.
- `docs/KNOWN_GOOD_SETUPS.md` now has Uwe's Uuna Tek A1 H working report, but it should not be marketed as broad compatibility evidence until the detailed acceptance fields and external entries land.
- Screenshots and demo media are repeatedly referenced as pending. Once captured, update README, FIRST_PLOT, release notes, and adoption status together.
- The user guide is comprehensive, but long. If adoption reaches broader public testing, split it into focused pages only after real users show where they get lost.
- Tracer JSON capture is now documented as a future pressure-bearing import path. Keep it out of current user instructions until implemented and tested.

## Recommended next work

1. Record real-hardware acceptance for `v1.0.0-alpha.1` or the next alpha.
   - Fill `release-results/` using the release checklist.
   - Fill in the remaining fields for the Uuna Tek A1 H entry in `docs/KNOWN_GOOD_SETUPS.md`.
   - Keep hardware readiness language conservative until detailed acceptance evidence exists.

2. Run one external guided mock test before broad outreach.
   - Use only the release link and `docs/FIRST_PLOT.md`.
   - Capture every point where spoken explanation feels necessary.
   - Convert each friction point into a docs fix, product issue, or test-environment note.

3. Run structured novice validation when external testers are available.
   - Use `docs/NOVICE_STUDY.md` and `docs/NOVICE_STUDY_RESULTS_TEMPLATE.md`.
   - Treat results as adoption/release evidence, not as a blocker for the already-closed usability milestone.
   - Update `docs/TESTING.md`, `docs/USER_GUIDE.md`, release evidence, and roadmap candidates after the study.

4. Produce the missing adoption assets.
   - Done: the first committed SVG sample gallery exists for simple line,
     hatch/fill, multi-colour/layer, and text-outline practice.
   - Next: one-minute demo GIF/video of launch → guided mock plot → export/plot result.
   - Then: screenshots for README/FIRST_PLOT if they reduce first-run uncertainty.
   - Later: image/vectorize gallery entries only after validation evidence exists.

5. Keep roadmap maintenance strict.
   - `ROADMAP.md`: Now / current status only.
   - `docs/ADOPTION_ROADMAP.md`: user-count and outreach path.
   - `docs/ROADMAP_HISTORY.md`: old phases and design diary.
   - New feature ideas should enter “validate before scheduling” until supported by tester evidence.

6. Treat Tracer capture import as an experimental source format, not a current promise.
   - Start from `docs/TRACER_CAPTURE_IMPORT.md` when the work becomes active.
   - First useful slice: import visible centerline strokes at a chosen physical size, preserve layer/stroke order, ignore pressure for motion, and keep pressure metadata in `.gantry` provenance.
   - Pressure-to-Z or pressure-to-feed behavior needs per-tool calibration and real Z-axis hardware testing before any hardware-ready claim.

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

## Open question to resolve later

The docs currently point to a JAR-first alpha path while the source tree also has convenient `scripts/start.*` helpers. That split is correct for tester adoption, but every release should verify both paths:

- downloaded GUI JAR launches on Java 17 and 21;
- CLI JAR smoke test passes;
- source checkout still builds and starts with the helper scripts;
- documentation examples match the published artifact names.
