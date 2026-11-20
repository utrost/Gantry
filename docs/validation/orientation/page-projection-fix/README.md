# Physical-page agreement after the presentation fix

Captured 2026-11-20 from `568b70b` plus the page-projection fix, JDK 21 / Linux /
Xvfb, isolated mock profile. No backend was connected. The exported SVG was
rasterized independently with Batik; these are actual application results.

- [Workspace](workspace.png): current physical view and successful ZIP feedback.
- [Exported SVG](reviewed.svg) and [its independent raster](reviewed.png).
- [Actual overlap dialog](ink-overlap.png).

All three now use the same orientation and physical-page placement, including
origin and effective axis swap. Compare the [original RC12 finding](../README.md).
**The label is still vertically reflected.** These captures verify view agreement,
not readability. #93 and the RC12 public-distribution hold remain open.

The [verification record](../../../release-results/2026-11-20-page-projection.md)
includes the 256-case rendered regressions and unchanged G-code baselines.
