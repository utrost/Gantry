# Watercolor, stations and homing audit — 2026-11-21

Status: **local software verification PASS; cross-platform CI pending; physical acceptance NOT RUN**.
This audit starts from the published RC13 source. Its assets/tag remain unchanged;
the fixes below are later development changes, absent from RC13 downloads.

## Confirmed failures and corrected behavior

| Issue | Reproduction before the fix | Correction |
|---|---|---|
| [#106](https://github.com/utrost/Gantry/issues/106), first-segment distance | MOVE(0,0), DRAW[(25,0)], budget 10: only the initial refill | Track actual controller endpoints; three refills and no segment over budget |
| #106, buffered exact boundary | DRAW 0→8 then 8→10→15, budget 10: refill/return precedes buffered 8→10, retracing backward | Flush pending ink before visiting the pot, including exact boundaries |
| #106, DRAW-only start | Initial refill followed by DRAW without returning to its assumed origin | Emit explicit return MOVE before the first ink after the initial refill |
| [#107](https://github.com/utrost/Gantry/issues/107) | Closest-color station is water/rinse; mapping selects it as paint | Exclude reserved water/rinse names and explicit rinse behavior; retain metadata in GUI/CLI |
| [#108](https://github.com/utrost/Gantry/issues/108) | Stored station (100,100), head (250,150), +1 X: commands (251,150), saves (101,100) | Submit absolute pen-up target (101,100); update/save only after successful submission |
| [#109](https://github.com/utrost/Gantry/issues/109) | Explicit ALARM:9 while waiting for pre-home lift: Home remains blocked waiting for Idle | Observe terminal failures, deadline and interruption on every poll and before accepting Idle |

The first three planner regressions and reserved-palette regression failed against
the unfixed source. The homing alarm test failed before the fix. The actual Swing
wizard regression failed against the released RC13 JAR with observed `(251,150)`;
the fixed application submits and saves `(101,100)` from the same unrelated start.
These are executable regressions rather than conclusions from code inspection alone.

## Quality and validation

- Full reactor tests plus focused CLI/export additions: **634 Java tests**, zero
  failures/errors/skips. **12 Python tests** pass. All **78 Markdown files / 321
  local references** pass the offline checker. Cross-platform CI is pending.
- Planner tests cover MOVE-to-DRAW, single endpoints, DRAW-only origin, exact
  boundaries, diagonal paths, pen-up gaps, seven budgets and repeated settings
  edits. Command IDs remain unique and drawn geometry stays on the original paths.
- Export integration runs the corrected planner through actual PlotService and
  GcodeFileBackend, independently measures pen-down XY from emitted G-code, and
  checks total ink, forward direction, refill budgets and final pen-up.
- Color mapping covers reserved names, arbitrary names with explicit rinse behavior,
  no usable paint candidate and the CLI's serialized station/refill assignments.
- Scripted GRBL tests inject ALARM:9 during the initial lift wait and check that $H
  is never sent afterward. Continuous Run reports cannot bypass the deadline;
  interruption remains visible. Existing unlock and pre/post-home lift tests pass.
- Full Swing mock workflow, cancellation/recovery, import, preparation, migration,
  and station-nudge/Finish acceptance pass in Linux/Xvfb.

Code cleanup supports the fixes: refill planning has explicit route state and
flush points; one shared rinse identity rule serves execution and color mapping;
homing has a shared wait guard. Obsolete mapping imports and a Python-era class
comment were removed, and station metadata documentation now matches the code.

## Limits and release identity

Station Z Down is deliberately reserved for custom/future GUI patterns; built-in
patterns keep the machine's normal pen heights. Existing tests and the current
user guide explicitly require this, so this audit does not silently change dip depth.
The CLI's optional station-depth export behavior remains as documented.

A successful backend call is command submission, not proof that a brush reached
the pot or cleared its rim. Mock, file and scripted serial checks do not establish
firmware timing, physical watercolor loading/cleaning, endstop behavior or native
fractional scaling. #39/#43/#54/#93 and participant/user gates remain open. Run the
operator procedure against a new identified candidate before claiming these fixes
physically accepted; do not substitute RC13's older payloads.
