# Controller-space starts and route estimate

Captured 2026-11-21, JDK 21 / Linux / Xvfb. The baseline is the unchanged GUI JAR
from `c9b1d38` / PR #99; the after capture uses `fix/prepared-preview-and-estimates`.
No physical backend was connected. Images paint the actual VisualizationPanel;
the reference raster is generated independently by Batik from the actual SVG.

- [Before canvas](before/workspace.png): only the middle horizontal stroke is visible.
- [After canvas](after/workspace.png): origin-to-first-DRAW and station-to-DRAW strokes restored.
- [Actual reviewed SVG](after/reviewed.svg), [independent raster](after/reviewed.png).
- [Before timing](before/estimate.txt), [after timing](after/estimate.txt).

The fixture draws from origin to (8,6), travels to (4,4), draws to (8,4), refills
at (30,22), then draws to (18,10). With drawing at 60 mm/min and travel at
6000 mm/min, two 1500 ms dips and 100 ms pen settling, the old estimate says
0 mm drawn / 1.129 s; production actually draws 30.971 mm with 53.489 mm travel,
for a 35.007 s feed/dwell estimate. The SVG is byte-for-byte unchanged:
SHA-256 `abdfb00b008f70fd20047d0d519ea273335270887f0fbfe28651c9ca806028ce`.

The after canvas and SVG show all three strokes with matching placement. Canvas
axes, station marker and bed boundary are UI annotations and do not enter SVG.

Reproduce by compiling `scripts/validation/PreparedRouteAudit.java` against the
baseline JAR, then run that same class with baseline and new JAR classpaths,
passing a separate output directory each time. It detects the older API so the
baseline is genuinely unmodified. See the [verification record](../../release-results/2026-11-21-prepared-route.md).
