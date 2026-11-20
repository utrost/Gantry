# Post-RC11 preparation integration — 2026-11-20

## Scope and publication boundary

This development revision combines the RC11 documentation audit (#67), reviewed
SVG export (#73), named pens (#74), advisory overlap (#75) and quick artwork (#76).
RC11 (`c630f27`) remains the latest published release. No existing release tag or
asset is replaced. No physical plotter, external participant or native Windows
scaling result is claimed.

Integration review fixed:

- The export/coverage projection conflict: both now consume one transformed,
  soft-clamped segment stream, including head position after refill and rinse.
- Bundled glyphs used Y-down coordinates in the Y-up command model. Labels now
  plot upright; an asymmetric A regression verifies baseline and apex.
- The heatmap now displays machine Y-up coordinates with the top of the bed at
  the top of the image.
- New historical jobs retain pen widths/names in SVG exports; only older jobs
  with missing snapshots receive an explicit width assumption.
- The first quick artwork in an empty document is undoable, and adding after
  that undo does not create an empty artwork group.
- Java/GUI and native installer workflows now run for all PR target branches,
  including stacked feature PRs.

## Local software evidence

Environment: Linux x86-64, OpenJDK 21.0.13.1, checksum-verified Maven 3.9.9,
Xvfb 1600×1200. Maven dependencies and display tooling were isolated under `/tmp`.
All GUI profiles and generated acceptance artifacts were isolated; no serial
connection or real machine operation was performed.

| Check | Result |
|---|---|
| Full Maven reactor package | 574 Java tests; zero failures, errors or skips |
| Python packaging/payload regressions | 9 tests passed |
| Existing GUI workflow/restart recovery | PASS: import, three pen gates, Stop, recovery defer/restart, wizard lifecycle, raster lifecycle, historical A/current B, settings and atomic-write failures |
| New preparation GUI workflow | PASS: text label, first Undo/Redo, project save, named-pen SVG and atomic ZIP export, completed overlap analysis, still disconnected |
| Workspace matrix | 16 virtual-display runs passed: Light/Dark × Comfortable/Compact × requested 1/1.25/1.5/2 |
| Effective scaling | 1/1.25/1.5 requests produced device transform 1; scale 2 produced transform 2. These are virtual-display results, not native Windows DPI acceptance |
| Whitespace | `git diff --check` passed |

The new `PreparationIntegrationAcceptance` is part of `scripts/test-gui.sh`, so
Java 17/21 CI also exercises these preparation actions. Existing controller
regressions cover cancellation ownership, repeated Stop and cleanup sequencing.
No further motion change was needed for #39 during this integration review.

Reproduce with Java 17+ and Maven 3.8+:

```bash
mvn clean package
python3 -m unittest discover -s scripts -p 'test_*.py'
xvfb-run -a ./scripts/test-gui.sh
xvfb-run -a ./scripts/test-workspace-layouts.sh
```

CI runs the combined revision on Java 17/21 and builds/checks native Windows and
Linux installers. Consult the integration PR checks for the exact commit and
results; prior feature-branch checks alone do not establish combined acceptance.

## Remaining gates

Use [the operator checklist](../OPERATOR_ACCEPTANCE.md) to record:

- #39: changed manual operations on Windows 11/iDraw H A1.
- #43: actual native scaling and minimum-workspace behavior.
- #54: expert benchmark and the broader operator/participant matrix.
- #16: five actual novice participants.
- #72: at least one real user task with quick artwork.

SVG cross-tool feedback, shared-pen operator experience and physical pen/paper
coverage remain useful validation. Drying-aware execution (#71) remains deferred.
The experimental image-art modes still need a committed real-image corpus and
recorded quality/plottability evidence before becoming beginner defaults.
