# Upright artwork and complete preview strokes

Captured 2026-11-21 on the `fix/versioned-upright-profiles` development branch,
JDK 21 / Linux / Xvfb, isolated mock profiles. No physical machine was connected.
These captures are from `scripts/test-gui.sh`; earlier RC12 artifacts stay unchanged.

- [Workspace](workspace.png): complete readable MAX MUSTERMANN, fresh upright profile.
- [Actual SVG](reviewed.svg) and [independent Batik raster](reviewed.png).
- [Actual overlap dialog](ink-overlap.png): same orientation and page placement.
  Its 420 × 296 pixel page is centered in the larger white viewport; compare page
  coordinates rather than the entire dialog's white background.
- [Legacy profile](legacy-before.png): unversioned settings still display the old
  vertically reflected label. Loading the profile does not migrate it.
- [Explicit opt-in review](orientation-review.png): changing the mapping is a
  disconnected user choice, with direction/frame recheck instructions.
- [Missing-stroke finding](missing-strokes-before.png): an intermediate capture
  after the axis fix but before #98. A lacks its first diagonal and crossbar,
  X loses both diagonals. The final workspace restores those MOVE-to-DRAW segments.

Inspected together: the final workspace, SVG and overlap show the same complete,
upright text. The workspace origin/selection overlay touches the top-right label;
it does not alter the exported geometry. The stroke alphabet intentionally uses
uppercase glyphs for lowercase input; editable text remains Max Mustermann.

[Verification and compatibility limits](../../../release-results/2026-11-21-upright-profiles.md).
Physical direction/frame/plot acceptance and real participant tasks remain open.
