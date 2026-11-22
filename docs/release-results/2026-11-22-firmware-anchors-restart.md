# Firmware review, nine-point placement and reviewed restart — 2026-11-22

Software delivery for #113–#115, building on the #111 completion barrier and #112
serial simulator. These are development changes after RC13; existing release
assets remain unchanged. Physical acceptance #39/#54/#93, native scaling #43 and
participant evidence remain open.

## Firmware and reference contract (#113)

GUI GRBL connections, including the serial simulator, require a fresh explicit
review before XY motion. Reading `$$` requires acknowledged finite positive
`$130/$131/$110/$111`; Z pen mode also checks `$112`. Feeds are conservatively
checked against the smaller XY maximum; no profile or firmware setting is written
or silently capped. Unsupported/missing settings block review with an explanation.

Calibration and station-test wizards keep a **Review firmware limits...** control
inside their modal window for GRBL connections. This remains reachable after
connecting there, Home, Stop or calibration setting writes; no XY gate is bypassed.

Starting a fresh firmware read invalidates the previous approval; cancelling or
failing that read/review leaves XY blocked until a new review is approved.

Firmware reference review also requires an explicit `$13=0` (millimetre status
and WCO reporting). Missing/inch/invalid report units are unsupported and block
approval; Gantry does not change them automatically. The actual reported work
position must fit the reviewed envelope. GRBL distinguishes report units from
command units: [upstream configuration](https://github.com/gnea/grbl/wiki/Grbl-v1.1-Configuration#13---report-inches-boolean).

The operator selects each machine axis's actual range (−travel…0 or 0…travel).
The firmware rectangle is shifted by reported WCO into work space and intersected
with the existing positive-work profile. Smaller calibrated dimensions survive.
The work origin must be reachable because normal jobs park there. No homing-mask
inference is used. `$H`, machine/power-on reference, paper/work origin and an XY
return to work zero remain distinct. Reading settings proves no physical position.

PlotService checks actual transformed/clamped drawing, frame, all travel targets
and applicable full station/swirl envelopes before starting. Frame/manual station
operations and restricted replay also validate. Existing profile clipping and
exported coordinates are preserved; the additional firmware boundary rejects
motion rather than applying a second silent clip. Stop, reset, alarm, disconnect,
WCO changes and changed dimensions/feeds invalidate review. Operators can mark
reference uncertain after suspected lost steps. Disconnect skips XY parking when
reference is uncertain. Expert raw XY is restricted to reviewed absolute mm;
unsupported modal/motion syntax fails. Explicit coordinate/settings writes
invalidate review. Library callers opt in with `requireSafetyReview(true)`;
file output and lightweight mock retain their capability boundaries.

## Placement contract (#114)

The nine references belong to the existing placed drawing's axis-aligned DRAW
vertex bounds in controller work coordinates (X/Y minimum, middle or maximum).
Numeric X/Y describe that point. Selection alone changes no geometry. Size,
rotation and mirror preserve the selected reference; an impossible transform is
rejected and restored instead of drifting the reference at a bed edge. Selected
artwork uses the same project reference property and physical group bounds.
Canvas markers, Undo/Redo, dirty/recovery state and new project saves retain it.
Older projects default to reference 0 without changing their placement/output.
Preview, reviewed SVG and execution continue using the same geometry transforms.

## Restart contract (#115)

Pen-only proposals retain an immutable prepared source, full SHA-256 canonical
geometry/settings/pens identity and complete machine/profile identity. Stroke
identity includes layer index, command index/ID and repeated-pass occurrence.
Geometry is projected once into original controller coordinates; selecting a
subset never re-aligns or rotates it again. Every retained stroke starts with an
explicit pen-up MOVE. Forward traverses selected → end; reverse traverses selected
→ first, reversing each stroke's vertices and crossing layer/pass boundaries with
ordinary pen gates and completion barriers.

`restart-proposal.json.job.json` stores the source once; the small atomic
`restart-proposal.json` records selection, direction and progress. Started and
acknowledged counters never imply executed ink. Only completed layer parking
barriers promote acknowledged strokes to controller-confirmed. Stop, alarm or
process loss leaves later strokes uncertain. Selection remains saved on Cancel.
Final uncertainty persistence temporarily clears and restores the cancelled worker
interrupt so atomic file flush can complete. Approval requires three explicit
registration/mapping, pen/order/pass and
uncertainty confirmations, then prepares a read-only partial job. Fresh firmware
review, normal checks, Start and pen gates remain necessary. No startup motion or
automatic resume exists. Changed source selection/order/geometry/pens/profile
blocks reuse; corrupt or unwritable checkpoints fail visibly. Selection changes
after approval invalidate that approval. Source geometry is not rewritten for
every progress event. Final persistence failure leaves conservative uncertain state.

Refill commands, automatic loading/refill budgets and inter-layer rinse jobs block
partial restart/reverse. Brush loading, cleaning, drying and color transitions need
a separate policy; normal watercolor execution remains available. Unused station
metadata alone does not turn a pen job into watercolor.

## Verification

- Full Maven reactor tests and packaged build on Java 21 targeting Java 17:
  **682 tests, zero failures/errors/skips**. Java 17/21 CI links are recorded in
  the integration PR.
- Twelve Python checker/packaging regressions pass. Offline local-link scan:
  82 Markdown files, 372 local file/image/heading references, zero errors.
- Complete actual Swing suite passes, plus all 16 requested theme/density/scale
  layout combinations. Xvfb is virtual display evidence; fractional scale requests
  do not all produce matching device transforms. Native scaling #43 remains open.
- New firmware parsing/intersection/feed/reference/raw-command tests use the actual
  backend with serial simulation. Route tests reject late travel and full swirl
  clearance before any backend motion or ink.
- All nine references across 16 axis/flip combinations and four rotations (576
  cases), repeated edits, numeric positioning, unchanged older placement,
  save/reload and rejected oversized transforms. Existing legacy output and
  page/export mapping regressions remain in the full suite.
- Restart tests cover implicit stroke starts, repeated pass identities, forward and
  reverse layer boundaries, 64 mapping combinations, source/profile/order changes,
  corrupted source, small progress-only writes, serial alarm uncertainty and final
  checkpoint saving on an interrupted worker while preserving its interrupt flag.
- Actual Swing suite: anchor resize/rotation, Undo/save/reopen, minimum window,
  firmware review/Cancel inside the calibration wizard and a reviewed raw-axis jog,
  simulator stopped during actual pen-down interpolation, visual
  selection/reverse/Cancel, then a second JVM restoring the proposal and executing
  only explicitly retained strokes after review. Existing serial fault, station,
  workflow, import, preparation and recovery checks are retained.
- [Captured evidence](../validation/firmware-anchors-restart/README.md).

Simulation establishes software sequencing, not firmware equivalence or physical
registration/stopping. No real plotter, ink or native scaling acceptance was run.
