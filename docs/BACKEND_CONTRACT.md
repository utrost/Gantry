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
| Dwell | Interruptible simulated delay | Queued controller G4; acknowledgement is not completion | Emits G4 |
| Stop | Invalidates active motion, interrupts its wait, freezes last reached XY and lifts simulated pen; repeated halt does not interrupt cleanup again | Realtime reset and best-effort recovery/pen lift; physical result needs acceptance | No running machine; inherited halt has no physical effect |
| Raw commands | Explicit subset below; unsupported lines fail | Sent to firmware; replies/rejections depend on that controller | Written verbatim; “written to file” is not acknowledgement/execution |
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
  the mock and actual `GcodeBackend` with fake serial. Reconnect is checked after
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

Feed override is sampled when a simulated move starts; it is not a continuous
firmware override. Feed-based interpolation omits acceleration, braking, controller queues,
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
