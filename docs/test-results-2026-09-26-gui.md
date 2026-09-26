# GUI usability and recovery pass — 2026-09-26

Scope: Linux desktop, Java 17, real Swing controls and file dialogs, isolated
mock profile. This extends the [RC2 installer record](../release-results/1.0.0-rc.2.md).
It does not establish Windows desktop, novice-study or physical plotter acceptance.

## Findings and fixes

- The fixed 300-pixel sidebar clipped primary actions, layer Edit buttons, and
  position controls. Sidebar width now follows its content; overflow scrolls.
  Start/check actions have separate rows. Long layer names truncate with a full
  tooltip while Edit stays visible. Coordinate fields follow UI scaling and the
  welcome explanation wraps.
- Stop returned normally from the plotting service, so the controller incorrectly
  reported success and added cancelled work to successful history. Completion now
  checks cancellation/interruption. Cancellation requested before the worker
  starts is retained. Regression checks include pen-up and re-plot eligibility.
- Project/recovery loading restored the saved layer subset, then rebuilt the
  widgets and selected all layers again. Selection now restores after rebuilding.

## Verification

Local result: **469 Java tests passed**, zero failures/errors/skips; the full
GUI scenario and separate-process recovery check passed.

`./scripts/test-gui.sh` compiles a developer-only driver against the packaged GUI
JAR, then runs two separate application processes. It uses the actual menus,
buttons and file choosers on Swing's event thread; it is automated GUI integration
coverage, not a human mouse/keyboard usability study.

The scenario imports the committed three-colour sample, cancels a second import,
selects layers, connects only a mock backend, completes a plot with three pen
confirmations, stops another at its pen gate, exports G-code, saves/reopens a
single-layer project, changes the selection, waits for that exact recovery state,
exits without clean-close cleanup, and recovers in a new process. Recovery must
retain the unsaved selection, remain dirty and start disconnected.

The fixture disables the preflight wizard and uses fast mock feeds to keep the
check bounded. Guided preflight and physical machine behavior remain separate
acceptance work. Logs, project, exported G-code and profile evidence are retained
under `dist/validation/gui/` (ignored by Git). The check is wired into Java 17/21
CI with Xvfb and artifact upload; local results do not imply those new CI runs
have completed.

Layout regression tests cover long layer names, 100/150/200% font sizes and
viewport overflow. Real rendered-window inspection covers the normal default
layout and a 1024×768 window at 150% and 200% scaling. At 200%, the sidebar
requires horizontal and vertical scrolling; some modal dialogs remain larger
than that small window. Dialog adaptation at extreme scaling remains follow-up
work, not a completed acceptance claim. Screenshots are refreshed
from the resulting local build, without manually widening the control panel.
