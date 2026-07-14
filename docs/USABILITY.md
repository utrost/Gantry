# Beginner usability plan — scoped

This document scopes Roadmap Milestone 9. Gantry already has enough plotting
capability. The goal is to make the existing capability understandable, safe,
and comfortable for casual operators who may not know plotting terminology.

## Outcome

A first-time operator should be able to complete this journey without reading
the full manual or receiving verbal instructions:

1. Add artwork.
2. Check its size and position on the machine bed.
3. Connect the plotter when ready.
4. Run a plain-language safety check.
5. Start the plot and respond to pen/colour changes.

The UI should make the next safe action obvious while keeping expert controls
available but out of the primary path.

## Existing foundation to retain

This milestone builds on, rather than replaces, the current first-run setup
wizard, guidance banner, pre-plot checklist, tooltips, mock backend, undo/redo,
dirty-state protection, recovery autosave, Live View, and time estimate.

The existing safety and architecture invariants remain mandatory:

- stopping, cancelling, or failing raises the pen;
- plot output is transformed and clamped to the configured bed;
- setup and preflight call the same production actions as direct controls;
- slow work remains off the Swing Event Dispatch Thread;
- start, export, framing, and estimation use the same prepared output;
- advanced users retain access to existing commands and workflows.

## Product principles

1. **One obvious next action.** At any normal workflow state, one primary action
   should be visually dominant.
2. **Artwork before machinery.** A user may safely load and arrange artwork while
   disconnected. Connection and movement enter the journey only when needed.
3. **Progressive disclosure.** Show essential controls by default and reveal
   technical controls on request.
4. **Plain language first.** Use operator-facing words in primary UI; retain
   technical terms only where they help diagnostics or expert work.
5. **Safe defaults over required expertise.** Common imports should work without
   understanding paper formats, curve steps, refill distances, transforms, or
   command models.
6. **Explain consequences before movement.** Clearly distinguish editing states,
   connected/manual-movement states, and active plotting.
7. **Feedback where the action happened.** Do not require a casual user to read
   the Console to discover success, failure, or the next required step.

## Scope

### 1. Default basic workspace

The normal workspace should emphasize:

- Live View;
- artwork size and position;
- connection state;
- layers or colours used by the current drawing;
- estimated duration;
- one state-aware primary action.

Jog controls, Raw G-code, axis overrides, detailed optimization, hatch editing,
calibration tools, low-level import settings, command JSON, G-code replay, and
diagnostic output remain available through an **Advanced controls** disclosure
or the existing menus.

This is presentation-level progressive disclosure, not a second application
mode with different data or behavior. Basic and advanced views must invoke the
same actions and operate on the same session.

### 2. Empty Live View action

When no document is loaded, Live View should contain a large **Add artwork**
action instead of relying on the File menu. It offers three novice-facing paths:

- **SVG or vector drawing**;
- **Image or photo**;
- **Existing Gantry project**.

Commands JSON, flattened JSON, G-code export/replay, and other interchange or
diagnostic formats remain under advanced File-menu actions.

### 3. State-aware primary action

One primary control changes label and action with the operator journey:

| State | Primary action | Supporting message |
|---|---|---|
| No artwork | **Add artwork** | Nothing will move while preparing a drawing. |
| Artwork loaded, disconnected | **Connect plotter** | Check size and position first; connect when ready. |
| Connected, not checked | **Check before plotting** | The check covers origin, fit, pen, paper, and a clear bed. |
| Ready | **Start plotting** | Starting will move the machine. |
| Waiting between layers | **Pen ready — continue** | Fit the requested pen/colour, then continue. |
| Plotting | **Pause** | Stop remains visibly available as the safety action. |
| Paused | **Resume plotting** | The machine remains paused until resumed. |

Disabled states must explain what is missing; they must not silently ignore the
user. Plotting-state controls and stop/cancel safety behavior remain unchanged.

The journey state should be derived by a small, Swing-free model or controller,
not spread across new conditionals in `PlotterPanel`.

### 4. Simplified artwork import

The primary SVG import path should be close to one click:

- default to **Fit safely inside the machine bed**;
- preserve aspect ratio;
- apply a sensible, visible safety margin;
- show the resulting physical size before committing;
- remember appropriate prior choices without carrying surprising expert values
  into a basic import.

Move curve step, refill distance, station ID, manual paper formats, custom size,
mirroring, and SVGToolBox processing into **Advanced options**. These controls
retain their current behavior and validation.

Image import should end in the same simplified artwork-import step after
vectorization. Existing project opening must not force re-import decisions.

### 5. Plain-language terminology

Primary UI copy should prefer these labels or equivalent tested wording:

| Current/technical wording | Beginner-facing wording |
|---|---|
| Import SVG | Add SVG or vector drawing |
| Import Image (vectorize) | Add image or photo |
| Pre-flight / Pre-Plot Checklist | Check before plotting |
| Start Plot | Start plotting |
| Confirm Layer | Pen ready — continue |
| Jog | Move the pen manually |
| Home | Find the machine's starting corner |
| command model / Commands JSON | Advanced command file |
| Flattened Commands | Advanced prepared-command export |

Technical names may remain in tooltips, documentation, diagnostics, file type
descriptions, and advanced menus where precision matters.

### 6. Actionable guidance

Replace passive step text with a short explanation and the relevant action. The
first step becomes artwork preparation rather than connection. Example:

> **Add the drawing you want Gantry to reproduce.** Nothing will move yet.
> [Add artwork]

The guidance should update from actual session/connection/plot state and remain
recoverable after dismissal. It must not become a separate workflow engine.

### 7. Guided first successful plot

The existing setup and pre-plot workflows should feel continuous:

`Set up machine → add artwork or sample → position → connect → verify → plot`

The first-run route should:

- explain briefly what a plotter does and that loading artwork cannot move it;
- reuse the existing Setup Wizard and calibration actions;
- allow a supplied sample drawing or the user's own artwork;
- use the mock backend for a no-hardware practice run;
- explain machine movement before connection, homing, framing, or plotting;
- finish at the same production Start Plot action as every other route.

A sample/practice entry is onboarding material, not a new document subsystem.

### 8. Local feedback instead of Console dependence

Important user-facing outcomes should appear near the affected control or in a
short non-blocking status message:

- artwork imported and its physical size;
- connection succeeded or failed, with the next corrective action;
- why plotting is unavailable;
- which pen or colour is needed next;
- edit completed and can be undone;
- autosave/recovery state when it matters.

The Console remains available for diagnostics and full detail. Error messages
must retain enough technical information for troubleshooting without leading
with backend jargon.

### 9. Visible undo affordance

After a model-changing edit, briefly show a contextual action such as:

> Line added. **Undo**

The control must call the existing document undo implementation. It does not
expand undo scope: drawing placement gestures remain direct manipulation unless
a separate, validated roadmap decision changes that behavior.

### 10. Explicit safety states

Use consistent language and visual treatment for these states:

- **Safe — nothing will move** while disconnected and preparing artwork;
- **Connected — manual movement enabled** after connection;
- **Ready to plot** only after required checks;
- **Plotter moving** during framing, jogging, homing, calibration motion, or a
  plot;
- **Paused** and **Stopped** with unambiguous recovery actions.

Colour cannot be the only signal. Text, enabled actions, and icons/borders must
carry the same meaning. The UI must never imply that a disconnected preview is
communicating with hardware.

## Delivery slices

Each slice should be reviewable and releasable without waiting for the entire
milestone.

### Slice A — language, defaults, and empty state

**Status: Complete.**

- Add the Live View **Add artwork** empty state.
- Reorder guidance to artwork → connection → safety check → plot.
- Apply the plain-language labels to the primary path.
- Add safe basic import defaults and an Advanced options disclosure.
- Keep existing expert menu actions intact.

**Exit:** a new user can import an SVG, see it safely fitted to the bed, and
understand that no hardware will move.

### Slice B — primary journey and progressive disclosure

**Status: Complete.**

- Introduce a tested, Swing-free operator-journey state model.
- Add the state-aware primary action and supporting message.
- Make the basic control presentation the default.
- Add **Advanced controls** without duplicating actions or state.
- Show explicit connected/moving/paused safety states.

**Exit:** the primary path has one obvious next action in every normal state;
all existing expert actions remain reachable.

### Slice C — feedback and first-plot continuity

**Status: Complete.**

- Move required operator feedback out of Console-only messages.
- Add the contextual Undo action.
- Connect first-run setup, practice/sample artwork, and the existing pre-plot
  checklist into one understandable route.
- Verify that dismissal, cancel, reconnect, stop, and relaunch return to a sane
  journey state.

**Exit:** a novice can complete a mock first plot and recover from one cancelled
step without external help.

### Slice D — novice validation and polish

**Status: In progress — study protocol and repeatable result template are ready;
five-participant evidence remains required.**

- Run the novice study described below.
- Fix the observed blockers and repeat until the completion target is met.
- Update `USER_GUIDE.md`, `TESTING.md`, screenshots, and release acceptance.
- Record rejected or deferred ideas explicitly instead of quietly expanding the
  milestone.

**Exit:** acceptance evidence satisfies the success measures and all automated,
mock-GUI, and required hardware checks pass.

The facilitator protocol is [`NOVICE_STUDY.md`](NOVICE_STUDY.md); record each
round with [`NOVICE_STUDY_RESULTS_TEMPLATE.md`](NOVICE_STUDY_RESULTS_TEMPLATE.md).
Automated or agent-driven UI checks do not substitute for the five participants.

## Acceptance criteria

### Functional and regression

- Basic and advanced presentations use the same document, preparation, backend,
  and plot actions.
- Every current File, Edit, Machine, View, Settings, and Help action remains
  reachable.
- SVG, raster, project, JSON, and G-code workflows retain their current output.
- Setup, calibration, pre-plot, station test, undo/redo, recovery, recent jobs,
  pause/resume/stop, and plot confirmation continue to pass their existing tests.
- The primary action never bypasses the configured pre-plot checklist.
- No control capable of movement becomes enabled earlier than it is today.
- UI updates remain EDT-safe and slow work remains asynchronous.

### Layout and accessibility

- The supported minimum screen/window target is **1024×800**; layouts below that
  size are out of scope for this milestone.
- The basic workflow remains usable at 1024×800 and the default window size at
  both 1× and 2× UI scaling.
- Primary actions and safety state do not rely on colour alone.
- All primary controls have keyboard access, visible focus, and descriptive
  accessible names/tooltips.
- Dialogs use consistent primary/cancel ordering and do not hide required actions
  below the supported viewport.

### Novice study

Test with five people who have not used Gantry and ideally have little or no
plotter experience. Follow the canonical clean-profile procedure in
[`NOVICE_STUDY.md`](NOVICE_STUDY.md), beginning with **Your first plot** visible,
and give this task without procedural instructions:

> Use Gantry to prepare and complete a simulated practice plot. Nothing physical
> is connected, so no real machine can move.

Observe rather than coach. Record:

- whether they complete the task;
- time to first visible artwork and to completed practice plot;
- every hesitation longer than ten seconds;
- every term they ask to have explained;
- unsafe or unintended actions;
- points where they read the Console or manual because the UI did not explain
  the next step;
- confidence rating after completion.

Target outcome:

- at least four of five complete without procedural help;
- all five understand when the machine can move;
- no safety-critical mistake;
- median time to first visible artwork under two minutes;
- median practice-plot completion under ten minutes;
- no participant needs to understand Commands JSON, G-code, axis inversion,
  curve step, or station internals for the basic task.

Any verbal explanation the facilitator must give is logged as a usability defect
or an explicit test-environment dependency.

## Automated and manual verification

Add tests in proportion to each slice:

- table-driven tests for operator-journey state and primary-action selection;
- component tests for basic/advanced visibility and action identity;
- import tests for safe-fit defaults, remembered values, and Advanced options;
- tests that important blocked states produce visible local explanations;
- shortcut, undo/redo, recovery, and dialog regression tests through the live UI;
- screenshot/manual checks at 1024×800 and the default size, at 1× and 2× scaling;
- the existing transform matrix across origins, orientation, axis overrides,
  rotation, alignment, and numeric positioning;
- mock-backend end-to-end first-plot acceptance;
- real-hardware safety acceptance before a hardware release.

Update `docs/TESTING.md` with stable script IDs as slices land; do not mark the
milestone complete from design review or screenshots alone.

## Architecture guardrails

This work touches the composition root and therefore risks regrowing
`PlotterPanel`. Keep these boundaries:

- journey-state derivation is Swing-free and directly tested;
- the primary action is a small view bound to existing callbacks;
- basic/advanced presentation controls visibility and copy, not duplicate logic;
- import-default policy lives outside the dialog layout where practical;
- feedback events use a focused presenter/status component rather than more
  ad-hoc dialogs and `PlotterPanel` conditionals;
- if project/recovery or job-history code must change, extract its existing
  coordination boundary rather than adding another responsibility to the panel.

Monitor `PlotterPanel`, `VisualizationPanel`, and the import/vectorizer dialogs
after each slice. A usability milestone is not permission to create a new
monster class.

## Non-goals

- New drawing, geometry, vectorization, optimization, or plotter capabilities.
- Removing or weakening expert workflows.
- A separate beginner data model or duplicated plotting implementation.
- Full vector-node editing or multi-document composition.
- Changing the hardware protocol or adding new backends.
- Making every advanced concept understandable without opening Advanced controls.
- Supporting windows smaller than 1024×800.
- Broad visual restyling unrelated to comprehension, hierarchy, feedback, or
  safety.

## Scope control

New ideas enter this milestone only when they directly reduce a documented
novice blocker in the five-step journey. Otherwise they remain backlog candidates
under `ROADMAP.md`'s validate-before-scheduling rules.

Completion requires implementation, automated regression coverage, a live mock
GUI acceptance run, novice-study evidence, documentation, and the usual
hardware-dependent release checks. It is not complete when the new layout merely
looks simpler.
