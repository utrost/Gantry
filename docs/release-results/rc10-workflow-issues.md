# RC10 workflow issue acceptance

This pass reviews the software issues from tracker
[#33](https://github.com/utrost/Gantry/issues/33) against the actual application,
its regression tests and native Linux Swing interactions. It separates code
acceptance from the external gates in #16, #39, #43 and #54.

## Closure evidence

| Issue | Implementation and acceptance evidence |
|---|---|
| [#34](https://github.com/utrost/Gantry/issues/34) — Button contrast | `WorkspaceSafetyRegressionTest` verifies enabled default-state palettes. `ButtonStateAcceptance` renders default, primary, motion, Stop and selected-toggle labels in normal/hover/pressed/native-focus states through Dark → Light → Dark. Every measured rendered text/fill contrast is at least 4.5:1. |
| [#35](https://github.com/utrost/Gantry/issues/35) — Layer inclusion | `DocumentSessionTest` and `DocumentCompositionTest` cover append, reordering/reprocessing, unaffected layers, pen widths and undo/redo. RC10 adds one-to-one ambiguity rejection and first-append source retention. GUI inclusion and project/recovery round trips use the production document state. |
| [#36](https://github.com/utrost/Gantry/issues/36) — Pen undo | Widths and refill configuration share the document snapshot. Layer/project persistence and pen-property undo/redo are covered by session, layer-property and project tests; pen-specific feedback is used. `WorkflowIssueAcceptance` reproduces 0.3 → 0.8 mm through the shared editor, Undo and Redo while preserving inclusion and commands. Plain pen layers no longer require a hidden refill station. |
| [#37](https://github.com/utrost/Gantry/issues/37) — Recovery dismissal | `GuiAcceptance` creates recovery, restarts, closes the prompt, checks byte preservation through clean shutdown, restarts again and recovers unsaved selection with no restored connection. Explicit discard is separate; corrupt recovery is retained with an error. |
| [#38](https://github.com/utrost/Gantry/issues/38) — Keyboard scope | `KeyboardWorkflowAcceptance` checks unique menu accelerators and sends real Ctrl+R/Ctrl+Shift+R to repeat/processing. Native Enter/Tab commit, Escape restores, arrows in fields/layers do not jog, and only focused direction controls jog. Focus loss cancels a simulated mouse hold on the mock backend. `NumericEditSupportTest` rejects partial/nonfinite/out-of-range edits. |
| [#40](https://github.com/utrost/Gantry/issues/40) — Wizard lifecycle | `WizardLifecycleAcceptance` covers window Close, Escape, Finish, Skip, Back/Next, repeated close and waiting for operation cleanup. Enter/leave counts and polling lifetime are checked. Skipped steps are named; calibration positioning controls are inside the modal wizard. The wizard explains that applied motion/settings are not undone. |
| [#41](https://github.com/utrost/Gantry/issues/41) — Bounds review | `PlotServiceTest` checks unclamped artwork bounds versus clamped frame bounds; transformed geometry tests cover origins/axes/rotation. `WorkflowIssueAcceptance` exposes an oversized drawing before Start and verifies readiness invalidation. `WorkspaceNoteLayoutTest` prevents wrapped bounds guidance being truncated at its original height. |
| [#42](https://github.com/utrost/Gantry/issues/42) — Durable state | `WorkspacePresentationTest` covers unknown/alarm/moving/hold/disconnected eligibility, mandatory checks in simulation, active transitions, explicit repeat review outcomes surviving disconnect, and disabled repeated connection attempts during reconnect. Connected preferences cannot change backend identity; the GUI derives simulation from the actual backend. GUI uses simulation wording and submitted-command progress; no automatic replay is introduced. |
| [#44](https://github.com/utrost/Gantry/issues/44) — Visual roles and canvas | Rendered state checks cover semantic actions and theme changes. Existing canvas/viewport tests verify view-only fit/zoom, thin-path legibility, physical width preservation and output-independent presentation. Buttons/icons retain names, focus indicators and explanatory tooltips. |
| [#45](https://github.com/utrost/Gantry/issues/45) — Physical placement | `PhysicalArtworkPlacementTest` exercises 64 axis/rotation combinations, preserving other artwork and undo placement. Native keyboard tests exercise completed numeric edits; relative transform fields use the same strict parser. Head coordinates remain separate. |
| [#46](https://github.com/utrost/Gantry/issues/46) — Running/pen-change inspector | `GuiAcceptance` executes three pen confirmations and Stop. `WorkflowIssueAcceptance` verifies the requested pen belongs to the stored job. The inspector wraps names, shows the sequence independently of inclusion, and the fixed strip retains Stop and submitted progress/elapsed estimates. |
| [#47](https://github.com/utrost/Gantry/issues/47) — SVG import | Physical preview, document-size/bed-size policies and page margins use the production import pipeline. `ImportLayouts` checks actual dialogs; `GuiAcceptance` covers chooser/import cancellation preserving the document. RC10 gives Open and Append distinct titles/actions and explicit consequences. |
| [#48](https://github.com/utrost/Gantry/issues/48) — Raster trace validity | `ImportPreviewPanelTest` and `VectorizeLifecycleAcceptance` verify immediate invalidation, rapid changes, failed sources, cleanup on Close and keeping the accepted trace alive. `ImportLayouts` imports the accepted raster trace and checks preview/result dimensions. Missing sources report errors without replacing saved output. |
| [#49](https://github.com/utrost/Gantry/issues/49) — Save/Save As | `GuiAcceptance` saves repeatedly without a chooser and reopens inclusion. `WorkflowIssueAcceptance` cancels Save As, forces an actual filesystem write failure, checks dirty/recovery preservation and retries successfully to the original path. |
| [#50](https://github.com/utrost/Gantry/issues/50) — Exact historical job | `WorkflowIssueAcceptance` keeps oversized drawing B open while reviewing/framing/plotting stored A. It checks A's estimate/bounds/pen/output and then returns to B unchanged. Geometry/axis compatibility tests block mismatched machines. Current pen-lift/speed use is explicit. |
| [#51](https://github.com/utrost/Gantry/issues/51) — Practice/setup | Guided practice directly loads the sample in simulation; replacement cancellation preserves the drawing. `StartupWelcomeDialogTest` and `SettingsPanelTest` cover dismissal/policy, preserved hidden settings and detached configuration copies. Pen-mode-specific fields retain other-mode values; setup failure prevents calibration handoff. The native workflow reaches the practice sample directly without setup dialogs or connecting. |
| [#52](https://github.com/utrost/Gantry/issues/52) — Settings comparison | `MachineSettingsIOTest` covers portable/legacy imports, invalid data, local preferences, detached values and station grouping. GUI acceptance covers Cancel/Apply/disconnected gating and an actual persistence failure preserving active settings. Full comparison values have tooltips. |
| [#53](https://github.com/utrost/Gantry/issues/53) — Failure/export feedback | `LocalFeedbackPanelTest` checks retained errors, visible later success, timeout restoration, explicit dismissal and accessible long-message details. GUI tests exercise actual failed Save/export and successful retries with diagnostics closed; failed export preserves its destination and removes temporary output. Export reports success only after atomic completion and snapshots its settings. Preferences and setup write failures preserve active settings; retained feedback adapts to runtime theme changes. |

## Reproduction

```bash
mvn package
python3 -m unittest discover -s scripts -p 'test_*.py'
GANTRY_GUI_REAL_KEYBOARD=true scripts/test-gui.sh
scripts/test-workspace-layouts.sh
```

The keyboard flag requires a desktop that can grant real focus. Ordinary CI runs
all non-keyboard GUI checks under Xvfb. Button models supply hover/pressed states;
focus is obtained from the actual window manager. The native keyboard test
injects the production mouse-hold event because this Wayland session does not
reliably deliver Robot pointer presses. Its focus transitions use the real
keyboard focus manager. This is not hardware acceptance.

## Gates deliberately left open

- **#39:** fresh Windows 11/iDraw H A1 results for changed manual operations.
- **#43:** native desktop scaling. Local 125%/150% requests resolve to a 100%
  device transform. At 200%, the physical display is too short for an 800-logical-
  pixel window; the harness records that limitation instead of claiming a pass.
- **#16:** five actual novice participants.
- **#54:** those external checks plus Uwe's expert-workflow baseline/comparison.
- **#33:** umbrella remains open until its external acceptance work is complete.

Earlier RC2 hardware success and automated installer/GUI checks are not evidence
that these newer interactions were physically rechecked.
