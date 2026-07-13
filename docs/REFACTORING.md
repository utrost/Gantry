# Architecture stabilization — complete; composition-root growth monitored

This document records the completed stabilization of Gantry's desktop
application. Product features remain in `ROADMAP.md`; this document preserves
the structural boundaries, behavioral invariants, and tests that future work
must keep intact.

## Why this work was needed

The Maven modules provide useful boundaries, but two Swing classes have grown
far beyond a maintainable size:

| Class | Current size | Responsibilities concentrated there |
|---|---:|---|
| `PlotterPanel` | 1,388 lines | window composition and delegation to focused workflows, controllers, views, project recovery, and recent-job history |
| `VisualizationPanel` | 951 lines | canvas scene/overlay state delegating rendering, interaction, geometry, and context actions |

`SvgImportStage` (~840 lines), `VectorizeStudioDialog` (~690 lines), and the
vectorizer controls remain larger classes, but their responsibilities are more
cohesive and their deterministic behavior has stronger test coverage. Revisit
them only when feature work exposes a concrete boundary; size alone is not a
reason for another extraction pass.

## Constraints and invariants

Every extraction must preserve these behaviors:

- A stopped, cancelled, or failed plot raises the pen.
- Plot commands are transformed and clamped to the configured machine bed.
- Swing widgets are only updated on the Event Dispatch Thread.
- Slow file, vectorization, connection, and plotting work stays off that thread.
- Start, export, framing, and time estimation use the same selected-layer,
  overlay-baked, multipass-expanded output.
- A wizard orders existing actions; it does not create a second implementation.
- Replacing or editing a document invalidates re-plot eligibility as appropriate.
- Real, mock, and file backends retain identical lifecycle semantics.
- Each commit compiles and keeps one authoritative implementation of behavior.

## Target responsibilities

The intended direction is a thin Swing composition layer around testable state
and controllers. Names may change as the boundaries become clearer.

### Document state

`DocumentSession` owns the current `ProcessorOutput`, selected layers, source
SVG/raster provenance, bounded multi-level undo/redo, dirty state, and
preparation of the selected output. It contains no Swing components and
performs no I/O; `.gantry` persistence remains in `GantryProjectIO`.

### Plot lifecycle

`PlotJobController` owns the backend connection, active `PlotService`, worker
lifecycle, pause/resume/cancel, progress, and re-plot eligibility. It exposes
typed state changes and contains no dialogs, file choosers, or layout code.

### Swing views

Connection, jog, plot, layer-selection, overlay, and raw-command controls become
small panels which render state and emit user intent through narrow callbacks.
Concrete backends and services are created outside those panels.

### Guided workflows

Preflight, setup, calibration, and station-test workflows move out of
`PlotterPanel`. They continue to use the existing `WizardDialog` framework and
the same actions as the non-wizard controls.

### Canvas

`VisualizationPanel` becomes the Swing event/rendering shell around separable
scene, transform, rendering, overlay, and hit-testing components. Geometry and
selection logic should be deterministic and independently tested.

## Delivery sequence

1. Add characterization tests around document preparation and plot lifecycle.
2. Extract `DocumentSession` and retain behavior through a narrow adapter in
   `PlotterPanel`.
3. Extract backend and plot lifecycle into `PlotJobController`.
4. Extract control sections and concrete wizards one at a time.
5. Extract canvas transform, scene, and hit-testing logic before splitting its
   rendering and input shell.
6. Reassess `SvgImportStage`, vectorizer GUI, and remaining classes over roughly
   500 lines.

## Original completion criteria

- `PlotterPanel` is primarily composition and was below roughly 1,200 lines at
  stabilization completion.
- `VisualizationPanel` delegates deterministic geometry and state management and
  is below roughly 1,000 lines.
- Plot/document controllers have no Swing dependencies and have direct tests.
- Hardware-safety invariants have automated coverage.
- The full supported-JDK reactor build is green.
- `docs/ARCHITECTURE.md` describes the resulting boundaries, not an aspirational
  design.

All criteria above were met on 2026-07-13. The subsequent project persistence,
recovery, and recent-job milestones grew `PlotterPanel` from 1,183 to 1,388
lines without reversing the controller/view boundaries. That composition root
is again a maintenance watch item: extract a focused project/recovery or job
history coordinator when the next feature materially touches those workflows.
Multi-document composition is deferred pending a validated user workflow, so it
is not a reason by itself to start another general refactoring campaign.

## Working log

- 2026-07-12: baseline audit recorded. `PlotterPanel` is 3,268 lines and
  `VisualizationPanel` is 2,113 lines. The existing module boundaries are kept;
  stabilization begins inside `app` rather than by introducing more Maven
  modules.
- 2026-07-12: introduced the Swing-free `DocumentSession` with characterization
  tests for replacement, layer selection, preparation order, undo, and source
  provenance. `PlotterPanel` now delegates its selected-layer and
  overlay/multipass preparation path to the session. All replacements, edits,
  source-provenance transitions, clearing, and undo now pass through the session;
  the old provenance, undo, and `currentOutput` adapter fields have been removed.
  `DocumentSession` is now the sole source of document truth in the application.
- 2026-07-12: introduced `PlotJobController` as the sole owner of the connected
  `PlotterBackend`. Connection adoption, failed connections, duplicate-connect
  rejection, disconnection, and backend action dispatch have direct tests.
  Plot execution intentionally remains in `PlotterPanel` until cancellation,
  pause, progress, completion, and pen-safety controller tests are in place.
- 2026-07-12: moved active `PlotService`, pause/resume, cancellation, and re-plot
  eligibility state into `PlotJobController`. The existing plot thread and its
  callback ordering remain unchanged; 22 focused controller, session, and plot
  service tests pass.
- 2026-07-12: moved the dedicated plot worker and guaranteed success/failure
  cleanup into `PlotJobController`. Completion is reported through a Swing-free
  callback; the panel retains presentation-only timing and status updates. Both
  success and thrown-failure paths are covered, with 24 focused tests passing.
- 2026-07-12: extracted thread-safe `PlotProgressState` for layer counts,
  command progress, elapsed time, pace-based remaining time, and estimates.
  Formatting is deterministic and directly tested; 26 focused tests pass.
- 2026-07-12: began canvas decomposition with deterministic `CanvasPalette` and
  `CanvasGeometry` components. Colour parsing/readability/ghosting and segment
  projection/distance now have direct tests and no Swing dependency.
- 2026-07-12: extracted the complete preflight checklist into
  `PreflightWorkflow` behind narrow connection/home/frame/start actions, plus a
  reusable `ConnectionWizardStep` for calibration. `PlotterPanel` dropped below
  3,000 lines while both workflows continue to invoke the existing actions.
- 2026-07-12: extracted `SetupWorkflow` and `StationTestWorkflow`. Machine
  settings still reuse the existing `SettingsPanel` sections; station dry/wet
  visits still use `PlotService` and backend actions. `PlotterPanel` is now about
  2,820 lines, with axis calibration the last large nested wizard.
- 2026-07-12: extracted the complete 453-line `CalibrationWorkflow`, including
  direction solving, steps/mm correction, limit-switch polling, and pen-lift
  testing. Persistent writes, backend actions, preview application, and logging
  cross a narrow host boundary. `PlotterPanel` is now 2,414 lines.
- 2026-07-12: extracted `PlotControlsPanel`, which now owns plot buttons,
  multipass input, layer selection, progress, estimate display, and plotting-safe
  widget enablement. `PlotterPanel` delegates view updates and is now 2,236 lines.
- 2026-07-12: extracted `DocumentEditor` as the owner of undoable hatch and
  stroke edits, hatch style/session IDs, and undo dispatch. The main window now
  receives one document-changed callback for preview and status refresh;
  `PlotterPanel` is 2,052 lines.
- 2026-07-12: extracted `DocumentFileWorkflow` for remembered file locations,
  command load/save, SVG and raster import, re-vectorization, SVG reprocessing,
  and station-color mapping. It receives busy-work and UI-refresh callbacks;
  `PlotterPanel` is now 1,791 lines.
- 2026-07-13: extracted `GcodeFileWorkflow` and `BusyTaskRunner`. G-code export
  and replay retain dedicated worker threads, while import/vectorize busy-state
  presentation is reusable. `PlotterPanel` is now 1,655 lines.
- 2026-07-13: extracted `JogPanel`, `BackendConnectionCoordinator`,
  `OverlayControlsPanel`, `RawCommandPanel`, and `ApplicationDialogs`. Jog soft
  limits/hold behavior, asynchronous connection state, preview interaction
  wiring, raw commands, settings, and optimize dialogs are no longer main-window
  responsibilities. `PlotterPanel` is 1,179 lines, meeting its size criterion.
- 2026-07-13: completed the canvas split. `CanvasRenderer` owns painting,
  `CanvasInteractionController` owns the mouse state machine,
  `CanvasInteractionGeometry` owns hit testing, snapping, viewport inversion,
  resize geometry, and hatch-region selection, and `CanvasContextMenu` owns
  contextual actions. Together with the tested `CanvasGeometry` and
  `CanvasPalette`, this reduces `VisualizationPanel` from 2,113 to 964 lines and
  meets the canvas criterion.
- 2026-07-13: completion verification passed on Temurin JDK 17.0.19 with
  `mvn -Djava.awt.headless=true test`: all nine reactor modules and 308 tests
  passed. Headless mode is required for the vectorizer's image tests in this
  macOS terminal environment. `git diff --check` is clean.
