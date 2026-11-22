# Document, motor and physical-page coordinates

These spaces have different jobs. Do not change executable transforms to repair
an export or advisory image.

- **Document:** imported SVG and quick text use the existing document geometry.
  The SVG importer and stroke alphabet generate Y-up coordinates. Projects retain
  that geometry, placement and explicit transforms.
- **Motor:** `PlotService.forEachDrawSegment` applies the configured origin-derived
  axes, rotation, alignment, Flip Y and bed clamping. These are the exact points
  sent to the backend. `projectArtwork` retains these points without executing them.
- **Physical page:** `VisualizationPanel.physicalToScreen` maps motor points to
  X-right/Y-down page coordinates using the effective axis swap and origin corner.
  `MachinePageProjection` snapshots that same mapping for SVG/ZIP and overlap.
  Swapped axes also swap physical page width/height. Extra inversions, rotation
  and Flip Y are already present in the motor points and must not be applied twice.

SVG keeps the original motor points under the page mapping's SVG matrix. Its
viewBox and millimetre dimensions describe the reviewed physical page. The heatmap
retains the original motor-grid counts and only reorders cells for display. Pen
width, overlap counts, layer order and executable commands are unchanged.
Stored-job export uses stored settings, never the current document's mapping.
The older three-argument ProjectedArtwork constructor retains its canonical
X-right/Y-up contract for direct callers.

## Versioned artwork orientation

Fresh profiles use `coordinateVersion: 2`: origin-derived Y inversion uses Top
rather than Bottom, so Y-up text reads upright in the ordinary workspace and an
Up jog moves up on the physical-page view. Extra inversions, swap, Flip Y and
rotation remain explicit adjustments. Portrait derivation composes after the
versioned baseline; axis calibration reverses both operations when saving extras.

An existing profile without the marker loads as version 1, preserving its exact
axis flags and executable output. Loading never rewrites the file. A damaged or
unsupported existing profile falls back to legacy defaults with a warning; it
cannot opt into new motion defaults. Missing files create fresh version 2 profiles.

**Preferences > Machine geometry > Artwork orientation** exposes Legacy and
Upright artwork. Changing it requires disconnection and an explicit review of the
motion change. Cancel preserves settings; cancelling setup also prevents the
follow-on calibration connection. Applying clears prior job confirmations.
Machine-settings imports include orientation in their existing disconnected review.
New upright portable profiles use format version 2, so older Gantry releases reject
them instead of interpreting them as legacy. Legacy exports remain format version 1.

Project geometry and placement are never migrated. A saved project uses the
selected machine profile when preparing motion: an old project with an unchanged
legacy profile retains its output; choosing upright changes that output explicitly.
Historical jobs retain their already-derived effective axis/origin flags, with no history
schema migration. The existing replay guard blocks framing and execution when
the current machine mapping differs; return to artwork to prepare under the new
profile, or restore the original machine profile before replay.

[Original RC12 diagnosis](validation/orientation/README.md) and the
[page-projection record](release-results/2026-11-20-page-projection.md) retain the
historical failures. [Upright-profile verification](release-results/2026-11-21-upright-profiles.md)
records the software fix and unchanged legacy G-code baselines. Physical direction,
frame and plot acceptance remains required in #39/#54; simulation does not establish
actual motor direction. The immutable RC12 draft predates these fixes.

## Controller-space starts and route estimates

DRAW endpoints do not have to repeat the preceding head position. For an initial
DRAW that position is the plot's origin; a configured refill or between-layer
rinse changes it to the station center. Unknown refills with no default station
are skipped and retain the previous position. The canvas now paints and hit-tests
these synthetic starts separately from editable geometry, under the current
placement transform. DRAW-only alignment bounds and executable commands remain
unchanged. Historical previews use their recorded station metadata.

Time estimation records `PlotService.plot` with the same prepared settings,
including alignment override and clipping. It includes first segments, parking,
configured dip/swirl/rinse and actual pen transitions, without sleeping or
connecting. Feed overrides scale motion, while configured dwell/settle remains
fixed. [Prepared-route verification](release-results/2026-11-21-prepared-route.md).

### Initial DRAW and the work origin

A disconnected preview/export plans an initial DRAW from work origin (0,0).
Include an explicit initial MOVE in command files to establish the intended start.
Before an initial DRAW without MOVE or a known refill, execution checks reported
position. A known non-origin/nonfinite position rejects the job with instructions
to add MOVE or deliberately return pen-up to origin and review again; it does not
silently position the machine. Halt/pen-up recovery still applies. If the backend
cannot report position, a warning records the origin assumption; this does not
establish actual position. Mock results cannot substitute for physical checks.

## Firmware intersection, placement reference and partial jobs

GUI GRBL review uses explicit per-axis machine ranges (−travel…0 or 0…travel),
converts them to work coordinates with `work = machine − WCO`, and intersects
with the unchanged configured positive-work profile. No `$23` convention is
assumed. All transformed/clamped draw targets, moves and applicable full pot/swirl
envelopes are checked before motion. Existing profile clipping stays compatible;
firmware limits reject extra out-of-envelope motion. File output is unchanged.

Nine-point placement references belong to the existing placed axis-aligned DRAW
vertex bounds in work coordinates. The reference property alone does not move
geometry. Resize/rotation/mirror preserve its work point; impossible anchored
transforms restore the old placement. Old projects default to reference 0.

Reviewed partial jobs snapshot the original controller-space segments including
implicit starts. Execution uses identity geometry transforms and explicit MOVE
before each retained stroke, with original page mapping retained separately for
review/export. The subset must never be re-aligned or have its original rotation,
axis inversions or page projection applied to execution twice. Original mapping
and full machine identity still gate replay. See the [delivery record](release-results/2026-11-22-firmware-anchors-restart.md).
