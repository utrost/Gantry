# Plotter backend contract and simulation boundary

`PlotterBackend` is the application-facing capability contract. Backends differ
in whether they execute, simulate or merely write commands. Shared tests cover
supported common behavior, not firmware or physical equivalence.

| Capability | GUI mock | Production GRBL serial | G-code file export |
|---|---|---|---|
| Connection | Required; disconnected motion throws; position is null; raw returns no connection | Required; connection loss fails commands; position is null when disconnected | Connect opens output; disconnect finishes it. No machine connection; queries retain last emitted XY |
| Absolute XY | Feed-based interpolation; returns after simulated travel | Sends formatted move and waits for acknowledgement, which may precede physical completion | Emits move; returns without travel |
| Relative XY | Preserves pen state; restores absolute raw mode | G91 → move → G90; preserves pen state | Emits G91 → move → G90 |
| Pen-up travel / drawing | `moveto` lifts; `lineto` lowers; pen state observable with `isPenDown()` | Same high-level intent; configured servo/Z/M3 commands | Same high-level intent in emitted commands |
| Run/Idle | Run while an operation owns the mock; Idle afterward; status is not a firmware planner | Controller-reported states via reader/poller; unknown/alarm/hold/disconnected are distinct | No controller state |
| Position | Simulated work XY, in mm | Last controller-reported work XY, asynchronous | Last emitted XY; not physical feedback |
| Dwell | Interruptible simulated delay | Queued G4; GRBL synchronizes preceding motion before acknowledging | Emits G4 |
| Stop | Invalidates active motion, interrupts its wait, freezes last reached XY and lifts simulated pen; repeated halt does not interrupt cleanup again | Realtime reset and best-effort recovery/pen lift; physical result needs acceptance | No running machine; inherited halt has no physical effect |
| Raw commands | Explicit subset below; unsupported lines fail | GUI reviewed mode uses the strict subset below; library default forwards to firmware | Written verbatim; “written to file” is not acknowledgement/execution |
| Reconnect | After prior worker exits, resets simulated work origin and modal defaults | New serial connection/initialization; prior workers must be cleaned up first | Reopening starts a new output file, not a machine resume |

High-level plotting assumes the absolute millimetre setup established by connect
and restored by `move`. Expert raw commands can alter a real controller's modal
state; this interface is not a universal G-code state sanitizer. Normal plotting
and restricted replay retain their existing review and safety paths.

## Supported mock raw subset

- `?` status; `$$` selected settings; `$X` synthetic unlock; `$H` synthetic home.
- Reads/writes for `$20`–`$23`, `$100` and `$101`; flag/range and positive finite
  steps/mm validation. These settings do not create simulated limit switches.
- `G21`, `G94`, `G90`, `G91`; `G92` with X/Y for a synthetic work-origin change.
- Explicit `G0`/`G1` XY (one or both axes) and optional positive `F`; omitted axes
  retain their current value. G1 uses the modal feed. Raw movement preserves the
  explicit pen state, including pen-down rapid motion.
- The exact configured `GcodeFormatter` pen-up/down lines for servo, Z and M3
  modes. Z depth/servo dynamics are not simulated beyond pen state.
- `G4 P<seconds>` from zero to 60 seconds, interruptible by Stop/disconnect.

Only one supported G-code command per line is modeled. Inch units, arcs,
unrecognized settings/words, duplicate words, nonfinite values and unsupported
syntax return `error: ...` without silently succeeding or changing geometry.
Disconnected raw requests return `(no serial connection)`. A busy mock rejects
conflicting writes; status/settings queries remain available. Cancellation is
observable by the owning worker and raw replay checks both response and
interruption before continuing.

## Verification

- `MockMotionTest` uses a timing hook and latches: the first interpolation tick
  completes; the next waits. Halt/interruption leave a 100 mm move at exactly
  5 mm. There are no assertions dependent on an approximate real-time stop point.
- `MockOperationLifecycleTest` runs the actual app manual and plot owners against
  the mock, checks no subsequent stroke/park after Stop, pen-up cleanup, repeated
  Stop and a deliberate new operation afterward.
- `BackendContractTest` runs the same motion/pen/raw-mode/disconnect checks against
  the mock and actual `GcodeBackend` with both scripted fake serial and the
  serial simulator. Reconnect is checked after
  a rejected disconnected draw: the fresh serial session must send pen-down,
  rather than trusting stale command-cache state.
- `FakeGrblTransport` models relative/absolute XY and default servo pen state,
  supports per-command response scripts and command latches, and can withhold
  acknowledgements or inject failure/status. Rejected commands do not update its
  geometry. It does not implement a real motion planner.
- `GrblFaultContractTest` adds rejected motion/raw commands, explicitly delayed
  acknowledgements with unsolicited status, interruption and alarm/disconnect
  while an acknowledgement is pending. Existing `GcodeBackendTest` retains its
  timeout, hold/resume, homing, pen-mode and serial-failure coverage.

## Physical boundary

The lightweight mock samples feed override when a move starts. The serial
simulator samples it during interpolation; neither reproduces firmware timing. Feed-based interpolation omits acceleration, braking, controller queues,
mechanical play, physical limits, real homing, serial-adapter latency and pen/ink
behavior. A stronger mock still cannot close hardware acceptance #39/#54 or
participant study #16. Preserve the dated [original mock audit](release-results/2026-11-20-mock-fidelity.md)
as historical evidence; the regression tests above specify the corrected behavior.

## Initial drawing position

Projection/export assume work origin until an explicit MOVE or known refill
establishes the start. PlotService checks reported position before an initial
nonempty DRAW: a known non-origin/nonfinite position rejects the job with existing
halt/pen-up recovery and no automatic positioning. Origin, explicit-MOVE and
known-refill starts remain supported. If position is unavailable, execution logs
the origin assumption; neither the disconnected preview nor that warning proves
actual controller position. Command files should establish their start with MOVE.
The new check does not turn mock/fake-serial evidence into physical acceptance.

## Homing wait failures

Development after RC13 checks terminal controller failure, deadline and thread
interruption on every fresh-Idle polling iteration, including before accepting
Idle. Continuous non-Idle reports cannot extend the deadline. An explicit alarm
during the initial lift fails Home without submitting $H. The existing controlled
unlock and pre/post-home lift ordering is retained. #111 now also synchronizes
the planner before accepting Idle for either lift; a frozen old Idle report
cannot start `$H` while the lift is still queued. Scripted transport tests verify
these commands; physical endstop/lift behavior still needs operator acceptance.
See the [watercolor/homing audit](release-results/2026-11-21-watercolor-homing.md).


## Explicit motion-completion capability (#111)

`awaitMotionComplete()` is separate from command submission. Its result states
what was established: `CONTROLLER_IDLE`, `SIMULATED_IDLE` or `OUTPUT_WRITTEN`.
The default throws, so a new backend cannot silently enable an unverified handoff.

Production GRBL sends `G4 P0`, requires its acknowledgement, then waits for a
newly requested Idle status. This is a planner barrier: upstream GRBL
[`gc_execute_line`](https://github.com/gnea/grbl/blob/master/grbl/gcode.c) calls
[`mc_dwell`](https://github.com/gnea/grbl/blob/master/grbl/motion_control.c), which
synchronizes the planner before dwelling, including zero dwell. An old Idle
report alone cannot bypass queued motion. A missing acknowledgement is always a
failure, even if status says Idle. Unsupported firmware rejects the barrier and
the job fails closed; this is not a claim about all GRBL-derived firmware.

One response-timeout deadline covers Hold, barrier acknowledgement and fresh
Idle. Alarm, disconnection, unexpected reset and interruption fail the wait.
Stop invalidates the pending barrier. Layer parking waits before the next pen
prompt and final success/history; manual sequences and replay retain their worker
until completion. Ordinary drawing points do not receive per-point Idle barriers.
The synchronous mock verifies it is connected and idle. File export flushes its
writer, emits no extra G4 and reports only `OUTPUT_WRITTEN`.

## Selectable serial simulator (#112)

Enable **Simulation (no physical hardware)** in disconnected Settings and choose
**Serial GRBL simulator**. Existing profiles retain **Lightweight mock**. Both
modes are identified visibly and in diagnostics; their factories never open an
OS serial port. The serial simulator is a `SerialTransport` beneath the actual
`GcodeBackend`, so production formatting, reader, queues, polling, parsing,
initialization and recovery run unchanged. Its separate daemon planner worker
acknowledges moves before executing them. G4, G92 and Home acknowledge after
execution; a 16-entry planner window delays further move acknowledgements until
space becomes available. Work coordinates reflect observed position and offsets.

Supported subset (one explicit command per line):

- G0/G1 XYZ and positive modal F; G90/G91, G21/G94; G92 XYZ; G4 P0–3600.
- Configured servo M280/M3 up/down values and M5; other servo values reject.
  XYZ moves include custom Z dip depths. The binary lowered flag follows travel
  from the configured up height toward the down height; it is not contact sensing.
- `$H`, `$X`, `$$`, `$I`, `$G`; reads/writes for `$20`–`$23`, `$100/$101`,
  `$110/$111/$112`, `$130/$131`, with finite/range validation.
- Realtime `?`, Hold `!`, resume `~`, reset, jog cancel and feed overrides
  reset/+10/−10. Reports include Run/Hold/Idle/Alarm, XYZ MPos/WCO and override.
- Selected `$20` XY soft-limit checks; `$22=0` rejects Home. No switch contacts
  or actual firmware safety envelope are modeled.

Unknown syntax is rejected, including duplicate/unsupported words; invalid or
oversized lines discard their remaining tail. Deterministic test controls pause
execution, freeze/suppress status, withhold/release acknowledgements and inject
rejection, alarm, reset or disconnect. They are acceptance seams, not a physical
machine diagnostic.

Motion and dwell use 20x nominal speed (minimum 2 ms move), with simple straight
interpolation. There is no acceleration, braking distance, mechanical play,
endstop wiring, firmware buffer/USB timing, servo dynamics, pigment or pot model.
The simulator is intentionally a documented subset, not a firmware-equivalence
claim. Shared backend, serial fault, controller and actual Swing tests are linked
in the [verification record](release-results/2026-11-21-completion-serial-simulator.md).
Physical acceptance #39/#54/#93 remains open. These changes are after RC13.

## Read-only firmware/reference review (#113)

GUI production-backend connections, including serial simulation, opt in with
`requireSafetyReview(true)`. Library constructors default off for compatibility;
callers that execute on hardware must deliberately choose their safety policy.
`validateWorkEnvelope` is a capability hook: generic/mock/file backends do not
claim firmware validation. GUI GRBL motion requires acknowledged finite positive
travel/feed limits, fresh WCO, an explicit machine-coordinate convention and
operator reference confirmation. Firmware/profile intersection preserves smaller
profiles and rejects unsafe targets/feeds without writing settings or capping.

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

All high-level absolute/relative XY calls validate. Relative reviewed moves first
synchronize and refresh work position. PlotService, frame/manual station actions
and restricted file replay prevalidate their full applicable route/envelopes.
Stop/reset/alarm/disconnect/WCO/reference or relevant options changes invalidate
review, including a dialog's revision token. A fresh settings read is not physical
homing/reference evidence. Disconnect skips XY parking while reference is uncertain.

Reviewed raw mode permits explicit absolute mm G0/G1 XY with optional reviewed F,
read queries, setup G21/G90/G94, dwell and exact configured pen commands. Duplicate,
nonfinite and unsupported motion/modal words fail. Explicit G92/G10, setting writes,
unlock/reset requests invalidate review; no continued XY is allowed until rereview.
Use the dedicated Home action for switch-based homing. The older general raw-mode
warning above describes library callers without this GUI opt-in, not permission
to bypass review in the application. File export retains legacy commands and has
no actual firmware/reference claim. See the [verification](release-results/2026-11-22-firmware-anchors-restart.md).
