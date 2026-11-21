# Versioned upright profiles — 2026-11-21

The software portion of #93 now makes ordinary Y-up artwork upright for fresh
profiles, without silently changing existing calibrated profiles. Visual review
also found and fixed #98: missing MOVE-to-first-DRAW segments in the workspace.
RC12 remains an unchanged unpublished draft at `8dcb772`; it contains neither fix.

## Behavior and compatibility

- New profiles use coordinate version 2. Existing unversioned files load as version
  1 and are not rewritten on load. Extra axis flags, rotations and Flip Y remain
  effective. Unsupported or damaged existing files cannot adopt new motion defaults.
- Preferences and setup expose Artwork orientation. Changes require disconnection,
  an explicit motion-change review, preview inspection and pen-up direction/frame
  rechecks. Cancel preserves persisted settings; cancelled setup does not continue
  into calibration or connect. Applying invalidates prior confirmations.
- Portable settings preserve the convention. Upright exports use format 2; the
  unmodified RC12 JAR independently rejects them as unsupported. Legacy exports
  retain format 1; old unmarked exports retain legacy flags on import. The existing
  disconnected import review names the orientation change and its implications.
- Calibration reverses both the chosen baseline and portrait composition before
  saving extra corrections. Document paths, placement and project format are not
  rewritten. An old project with its original legacy profile retains its output;
  explicitly choosing upright changes newly prepared output.
- Historical settings retain derived flags. Replay remains blocked when current
  machine geometry/mapping differs. Restore the original profile to replay, or
  return to artwork and prepare for the new profile. No history migration occurs.
  See [coordinate conventions](../COORDINATE_CONVENTIONS.md).

## Verified preview defect — #98

Quick text emits MOVE(start), DRAW(remaining endpoints). The canvas previously
loaded only DRAW endpoints. It hid the first segment and made single-endpoint
strokes invisible. It now includes a known MOVE or preceding DRAW endpoint for
painting, hit testing and travel statistics. DRAW-only raw alignment bounds stay
unchanged, so this display correction does not change legacy executable offsets.
Commands and saved geometry remain unchanged. Initial DRAWs without a known
MOVE, and post-refill controller positions, are outside this focused correction;
no firmware-equivalent preview claim is made.

## Validation

- Full reactor package: **608 Java tests, zero failures/errors/skips**.
- **256 legacy G-code hashes** still match the original tagged RC12 baselines.
- **512 actual SVG/heatmap renderings** cover both conventions, four origins,
  landscape/portrait, all extra swap/inversion flags, Flip Y and 0/90° rotations.
  Loaded canvas paths are now checked as well as physical-page mapping.
- Fresh asymmetric F reads up/right and Up jog maps up for all eight ordinary
  origin/orientation combinations. Editable project reload preserves geometry.
- All 128 valid origin/orientation/version/calibration-direction combinations
  round-trip to the intended effective axis flags.
- Actual canvas pixel checks cover every MAX stroke, including A's crossbar and
  first segments; separate assertions preserve DRAW-only bounds even when MOVE
  starts outside them, and include consecutive DRAWs.
- Profile load/save, old/new portable transfer and G-code preservation, corrupt
  version fallback and historical mapping/replay guards are covered.
- Full real-Swing mock workflows pass, including old-profile retention, cancelled
  migration, explicit opt-in without document mutation, connected-change rejection,
  setup cancellation without calibration, Undo/Redo, save and SVG/ZIP export.
- Nine Python packaging tests pass. Java 17/21, image-art CLI baselines and native
  Windows/Linux installer/upgrade CI run on the implementation PR.
- [Actual before/after captures](../validation/orientation/upright-profiles/README.md)
  were inspected alongside independently rasterized SVG and the overlap dialog.

## Remaining acceptance

Fresh or explicitly opted-in profiles change executable mapping. Windows 11 /
iDraw H A1 pen-up direction, frame and actual plot acceptance in #39/#54 remains
NOT RUN. #93 stays open for that conditional acceptance. Native scaling #43,
quick-artwork user task #72 and novice study #16 remain open. These software tests
do not establish motor wiring, firmware behavior, physical output or human usability.
A future candidate must have a new immutable tag and new assets; do not replace
RC12 assets or imply that the public RC11 download contains these changes.
