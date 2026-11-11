# GUI workflow implementation — validation record

This change implements the code work from tracker [#33](https://github.com/utrost/Gantry/issues/33), using SVG → position → pen setup → plot → repeat as the main journey. It retains Swing/FlatLaf, project compatibility, existing preflight policy and hardware protocols. Published release assets are unchanged.

## Implementation coverage

| Issues | Changes |
|---|---|
| #34, #44 | Complete Light/Dark primary-button state palette; quieter actions, viewport Fit bed/Fit artwork/zoom; readable thin paths without changing pen widths; reduced technical canvas overlay. |
| #35, #36 | Append preserves excluded layers; reprocessing preserves uniquely matched layer inclusion, nib widths and refill properties. Unmatched layers stay excluded with a review message. Undo/redo includes nib widths and source provenance. |
| #37, #49 | Recovery offers Recover/Discard recovery/Later; closing preserves deferred recovery through shutdown. Save reuses the project path; Save As has its own chooser and Ctrl+Shift+S. |
| #38, #45 | Reprocess and repeat shortcuts no longer conflict. Whole-drawing and selected-artwork fields use physical millimetres. Enter/focus loss validates and commits; Escape restores the value. Group edits compensate global transforms so unrelated artwork stays stationary. |
| #39, #40 | Manual operations own a cancellable worker through halt/lift cleanup; repeated Stop cannot interrupt cleanup. Wizard Close stops step polling and waits for cleanup. Calibration has usable direction controls inside its modal window. Application close waits for active operations. |
| #41, #42 | Review shows transformed unclamped bounds and warns about clipping; actual frame commands remain bed-clamped. Failure/stopped/completed outcomes survive disconnection. Unknown head position is explicit; document reload does not invent a new machine position. |
| #43, #46 | Leaner toolbar/footer, stable feedback slot, adaptive layer area and compact pinned machine controls. Job inspector identifies requested pen, colour and sequence; Stop stays in the fixed job strip. |
| #47, #48 | SVG import includes a physical preview alongside sizing and optional processing. Raster import uses the accepted trace; option changes immediately invalidate readiness. Missing, stale, empty and failed previews cannot be accepted. Closing cancels workers. |
| #50 | Historical jobs open a separate read-only stored-output preview and review. The current document remains intact. Geometry/axis mismatch blocks replay. |
| #51, #52 | Simulation practice skips irrelevant machine setup. Pen settings show relevant mode controls. Machine import reviews grouped old/new portable fields, retains local preferences and persists before reporting success. |
| #53 | Persistent errors survive transient feedback; details are explicitly opened rather than repeatedly appearing as modals. Export success appears after writing completes. |
| #54 | Expanded automated regression and native Swing acceptance harnesses; external acceptance remains open as listed below. |

The table describes implementation coverage, not a claim that all GitHub issues or operator acceptance criteria are closed.

## Verified locally on 2026-11-11

- Full `mvn package`: **522 tests, zero failures/errors/skips**, packaged application built.
- Native Linux Swing workflow harness: passed with real keyboard numeric editing and an isolated mock profile.
- Successful raster import: passed; accepted-preview dimensions and position matched the imported workspace result.
- Updated one-minute mock walkthrough: generated successfully.
- Light/Dark, Comfortable/Compact layout matrix: see the effective-scale limitations below; no hardware acceptance is inferred from these captures.

## Reproducible verification

Build and unit tests: `mvn test` and `mvn -pl app -am package`.

With a Linux desktop display and the built application JAR:

```sh
GANTRY_GUI_REAL_KEYBOARD=true scripts/test-gui.sh
scripts/test-workspace-layouts.sh
```

The GUI harness uses an isolated temporary profile and the mock backend. It checks settings transfer/cancel, SVG/raster cancellation, numeric keyboard editing, placement undo/redo, layer inclusion, three pen confirmations, repeat and Stop, G-code export, conventional repeated Save, project reopen, crash recovery, recovery prompt dismissal, and wizard cleanup.

The layout harness renders actual Swing states at 1024×800 and 1440×1000, in Light/Dark and Comfortable/Compact. It verifies three layer rows with machine controls open, accessible Stop, empty-state dismissal and unchanged prepared output after presentation navigation. Requested display scales are 100%, 125%, 150% and 200%; logs record the effective device transform. This Linux desktop applies 100% and 200%; fractional requests map to 100% and do **not** count as native 125%/150% acceptance. At 200% the 1440-pixel-tall physical display constrains the window below 800 logical pixels: Stop is checked, but the harness explicitly marks the minimum-workspace layer check unavailable. A taller display is required for that acceptance.

Additional regression tests cover manual cancellation races, durable disconnected outcomes, contrast in enabled default-button states, settings comparison, import-preview invalidation/failure, unclamped review versus clamped frame, pen-property undo, source restoration and 64 placement transform combinations.

## External gates still open

- Windows 11 native installer and 100%/125%/150%/200% DPI checks.
- iDraw H A1 checks of changed jog, focus-loss, Stop, homing, calibration and disconnect interactions.
- Uwe's expert workflow evaluation against the original benchmark.
- The five-participant novice study tracked by #16.

No physical hardware acceptance, novice-study result, native Windows acceptance, or new published installer is claimed by this record. Keep #54 and #16 open until the corresponding evidence exists. The previous audit evidence bundle remains local.

## Visual refinement

Dark is now the default for new profiles. The follow-up visual pass centralizes a graphite/blue palette, rounded quiet surfaces, softer field and button treatments, compact property headings, an unboxed layer list, and less saturated canvas orientation cues. Light remains available and saved preferences are preserved. No artwork coordinates, job preparation or machine command behavior is changed by the visual styling.

The appearance regression checks and actual Swing layouts were exercised in both themes and both densities at 1024×800. The native mock GUI workflow includes keyboard numeric editing, pen changes, Stop, import, save and recovery. The DPI and physical-hardware limitations above still apply.
