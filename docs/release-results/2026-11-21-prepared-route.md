# Prepared-route software fixes and documentation audit — 2026-11-21

Scope: independently reproduced #100, #101 and #103, plus reconciliation of current
guides, reference documentation, screenshots and walkthrough. No physical
machine, native fractional-scaling acceptance or participant study was run.
RC11 public assets and immutable RC12 draft assets remain unchanged.

## Reproductions and fixes

| Finding on main `c9b1d38` | Result after fix |
|---|---|
| MOVE(0,0), DRAW[(3,4)] estimates 0 mm drawn | Counts the full 5 mm segment |
| Refill at (50,0), 6000 mm/min travel, 1500 ms dips and 100 ms settle estimates 1.0 s | Counts both dips, settle/gaps and park: 4.202 feed/dwell seconds |
| Estimates omit clipping, park/rinse and swirl, charge settle per DRAW, and charge skipped refills | Records actual production orchestration, including pen-state transitions and configured station behavior |
| Feed override scales fixed dwell/settle | Only motion seconds scale; fixed time stays fixed in summary, tooltip and live estimate |
| Initial/post-refill/rinse DRAW lacks its preview start | Controller-space start is displayed without changing editable geometry or alignment bounds |

The three initial route/preview assertions failed before implementation. A fourth
reproduction (#103) confirmed that a known non-origin controller could execute an
initial DRAW from a different start than review. Execution now rejects that known
mismatch and applies existing halt/pen-up recovery, with no automatic positioning
move. Explicit MOVE, known refill and origin-start jobs remain supported. Unknown
reported position retains a logged origin assumption, not a physical guarantee. The timing
backend records `PlotService.plot`; it never connects or sleeps. Prepared settings
include current alignment offsets or the historical snapshot. Preview painting
and stroke hit testing use synthetic controller starts, separate from commands.
Full station metadata is supplied to both current and historical previews;
unknown refills retain the previous position when execution skips them.

[Actual before/after canvas and independent SVG raster](../validation/prepared-route/README.md)
show a three-stroke fixture. Its SVG is byte-identical before/after; the corrected
35.007 s estimate replaces 1.129 s for the same executed route. Images were
inspected together for completeness and placement, independently of green tests.

## Verification

- Java reactor and focused tests cover 623 tests, with zero failures/errors/skips
  on the completed run. Java 17/21 and Windows/Linux installed-upgrade CI run on
  the implementation PR.
- Twelve Python tests pass: nine existing packaging regressions and three new
  documentation-checker regressions.
- Full real-Swing mock workflow passes, including historical A/current B estimates,
  persistence failures, profile migration/cancellation and preparation export.
- Actual preview pixels and starting coordinates match production projection in
  128 origin/orientation/extra-axis/station-fallback cases. Initial-origin,
  post-refill, unknown/fallback station and between-layer-rinse starts are covered;
  changing placement leaves machine-space starts fixed. Historical station starts
  are hit-testable, and station refresh preserves excluded-layer selection.
- Immediate fake-position checks cover rejection from (5,5) without drawing or
  automatic positioning, halt/pen-up recovery, and accepted explicit-MOVE,
  origin-start and known-refill cases. No new automatic machine motion is added.
- Existing 512 SVG/overlap renderings and 256 tagged-RC12 G-code baselines pass.
- Independent numerical expectations cover first endpoints, clipping, park/rinse,
  both configured dips, polygon swirl distance, skipped/fallback stations,
  consecutive DRAW pen state and fixed-vs-motion override. A long-dwell regression
  ensures estimation finishes without waiting through the controller dwell.
- All 47 current gallery PNGs and the 60-second/six-state mock walkthrough were
  regenerated from this development JAR, using isolated profiles. This does not
  establish native desktop, physical motion or novice acceptance.

## Documentation reconciliation

The initial audit covered all 72 repository Markdown files: 286 local file,
image and heading references resolved. The committed `check_doc_links.py` checks
all tracked/untracked nonignored Markdown and runs in CI; new report links are
included in the final scan. It is an offline local-reference check, not a crawl
of external web links or a claim of human task acceptance.

Current user/offline guides, first-plot/troubleshooting, architecture, coordinate
conventions, lessons, testing, contributor guidance, roadmap/digest/adoption,
operator checklist and gallery/status provenance were updated where affected.
The architecture now correctly identifies command subclasses as final classes.
The user guide no longer claims a separate per-layer elapsed clock or that
operator waiting is excluded from the wall clock. Historical release observations
and test counts remain dated; RC12 defect image links now point to its immutable
tag so refreshing the current gallery does not erase the original failures.
The old known-start limitation in the upright-profile record is superseded by
this follow-up, without changing that record's historical test result.

## Limits

This corrects preview completeness and a feed/dwell cost estimate. Unclamped
editable geometry may still show the portions that bounds review warns will be
clipped; SVG/overlap use prepared clamped output. Estimates exclude acceleration,
transport latency, pen mechanics and operator waiting. Host-side pen-settle timing
can differ from exported G-code/replay timing. Command-progress remaining time is
still an approximation with unequal command costs. Native/hardware/user gates
#39/#43/#54/#93/#72/#16 and umbrella #33 remain open; #71 stays deferred research.
A new candidate must use a new immutable version and collect its own acceptance.
