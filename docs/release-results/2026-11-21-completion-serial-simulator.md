# Motion completion and serial simulator — 2026-11-21

Software work for [#111](https://github.com/utrost/Gantry/issues/111) and
[#112](https://github.com/utrost/Gantry/issues/112). Started from post-RC13 main
`6847e74`. These are development changes, absent from the immutable RC13 downloads.
No physical plotter was connected; physical acceptance was **NOT RUN**.

## Reproduction and correction

The first `MotionHandoffTest` failed before the implementation: acknowledged
parking released the next layer gate before execution completion. Its controlled
backend holds a parking barrier; the corrected service cannot offer the next pen
or return final success until that barrier is released. Cancellation prevents the
next layer from moving. This reproduces the early handoff rather than relying
only on source inspection.

`PlotterBackend.awaitMotionComplete()` now distinguishes controller Idle,
simulated Idle and commands written. GRBL sends `G4 P0`, waits for its actual
acknowledgement, then newly requested Idle, sharing one response-timeout deadline.
Upstream GRBL's [dwell implementation](https://github.com/gnea/grbl/blob/master/grbl/motion_control.c)
synchronizes the planner before dwelling, including zero dwell. This prevents
an old Idle report in transit from bypassing queued motion. A missing ack is
never replaced by Idle. Unsupported firmware rejects the barrier and fails closed.

Layer parking waits before the next pen prompt and final history/success. Station
checks, manual workers and replay also wait before declaring completion or
releasing ownership. It is not a per-drawing-point wait. File export flushes and
reports only commands written, without injecting new G4 commands into its output.
Home also uses planner barriers around its pre/post-cycle pen lifts. A second
red-before-fix simulator test froze old Idle reports while the lift remained
queued: the old Home submitted `$H`; the corrected path waits behind G4 until
the lift executes. Existing unlock, atomic `$H`, unconditional lifts and work-zero
ordering remain covered. Unexpected controller startup invalidates completion and
requires recovery review.
Stop-related interruption produces a stopped outcome; cleanup failures remain errors.

The workspace explicitly says it is waiting for motion completion, keeps Stop
available and prevents Pause from submitting a conflicting pen command during
the barrier. Once the next pen is offered, the serial simulation is at work
origin with its pen up. Simulation completion remains labelled as simulation.

## Serial simulator and selection

Disconnected Settings retains **Simulation (no physical hardware)** and offers
**Lightweight mock** or **Serial GRBL simulator**. Existing profiles keep the
lightweight mode. Both are labelled in the workspace and diagnostics. The latter
injects `SerialSimulatorTransport` beneath the actual `GcodeBackend`; its factory
never calls an OS serial transport, even with an invalid physical port name.

A separate planner worker acknowledges moves before execution. It reports
observed XYZ/work coordinates, offsets and Run/Hold/Idle/Alarm; synchronization
commands acknowledge after execution. Its planner window, Hold/resume, reset,
settings, Home and all three configured lift modes support the normal workflows.
Fault seams deterministically delay/drop/release acknowledgements, freeze/suppress
status and inject command rejection, alarm, reset or disconnect. Invalid commands
fail explicitly; an invalid oversized line cannot execute its remaining suffix.
See the [complete subset and limits](../BACKEND_CONTRACT.md#selectable-serial-simulator-112).

## Verification

- Full Maven reactor: **661 Java tests**, zero failures/errors/skips.
- **12 Python regressions**, including packaging and documentation-link checks.
- `MotionCompletionTest`: delayed Run→Idle, cached Idle, missing/rejected barrier
  ack, Run/Hold deadline, alarm, disconnect, direct Stop, interruption and file semantics.
- `SerialSimulatorTest`: independent ack/execution, queued barrier, stale/missing
  status, delayed ack release, all fault modes, offsets, relative/absolute moves,
  servo/Z/M3-M5 (including custom Z dip depth), Home rejection, Hold/resume,
  Stop, reconnect and malformed input.
- Shared `BackendContractTest`: lightweight mock, scripted serial and production
  backend over the serial simulator. No separate application protocol is bypassed.
- `SerialCompletionControllerTest`: manual ownership, final replay barrier and
  Stop during parking; no subsequent handoff, successful history or misleading
  failure dialog. Pause cannot inject a command into an active barrier.
- Full actual Swing suite in Linux/Xvfb: existing import, mock, recovery, history,
  settings, preparation/migration and wizard acceptance, plus the new serial path.
  The serial workflow selects the mode in the actual Settings dialog, connects,
  Homes, visits/tests stations, plots two layers, holds parking, Stops, reconnects
  and shows injected rejection feedback. The compact 1024×800 check retains
  connection, Machine and Stop controls. Logs/screenshots are produced by
  `scripts/test-gui.sh` under `RESULTS/serial-simulator`.

The [retained visual evidence](../validation/serial-simulator/README.md) shows the
settings, pending barrier, completed simulation and visible error states.
The linked issue timelines identify the implementation PR and its Java 17/21
and Windows/Linux installer checks.

## Boundaries

The implementation was independently authored from Gantry's existing transport
contract and documented GRBL semantics; no Nib application code was copied.
Nib's useful architectural separation inspired the issues, but its missing-ack
inference policy was deliberately not adopted.

Simulation runs straight-line motion/dwell at **20x nominal speed**, with a
minimum 2 ms move. It is neither a firmware emulator nor a timing estimate. It
omits acceleration/braking, real USB/firmware queues, physical limit wiring,
mechanical play, actuator dynamics, paint loading and pot/rim clearance. GRBL
Idle establishes controller-reported completion, not a sensor measurement that a
pen physically lifted. Software evidence closes #111/#112's software scope only.
Hardware/operator #39/#54/#93, native scaling #43, participant and artwork gates
remain open. Firmware envelope/origin #113 and placement/restart #114/#115 remain
separate planned work. Release tags, assets and prior evidence remain unchanged.
