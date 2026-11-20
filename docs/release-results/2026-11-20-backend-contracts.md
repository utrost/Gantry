# Mock cancellation and backend contracts — 2026-11-20

## Scope

This implements #78/#79 after the [original mock audit](2026-11-20-mock-fidelity.md)
on `3462659`. The GUI simulator now owns cancellable operations, freezes the last
reached XY on halt/interruption, retains pen/connection/Run/Idle state, and executes
a documented raw-command subset. Unsupported commands fail instead of receiving
false acknowledgement. High-level moves and raw G1 share modal feed semantics.

See [BACKEND_CONTRACT.md](../BACKEND_CONTRACT.md) for capabilities, differences
from serial/file backends, exact supported raw syntax and physical limitations.
The production backend also clears its cached pen state on a new connection.
The shared reconnect test reproduced a rejected disconnected draw leaving that
cache down and suppressing the next connection's first pen-down command. This
fix restores the normal pen-down sequence; formatting and recovery protocols
remain unchanged.
Published RC11 assets remain preserved and do not contain this development work.

## Regression evidence

- `MockMotionTest`: controlled first tick at 5 mm, then blocked second tick;
  halt/interrupt of a 100 mm move leaves XY at 5 mm. Pen recovery, busy/Idle,
  disconnected rejection, later movement, raw validation/replay, all configured
  pen modes and modal feed inheritance are checked without real-time travel.
- `MockOperationLifecycleTest`: actual app manual/plot owners with the actual
  mock implementation, repeated Stop, no later stroke or park, pen-up cleanup
  and a deliberate new operation.
- `BackendContractTest`: common XY, pen and relative-mode restoration behavior
  against mock and production GRBL backed by fake serial, plus reconnection.
- `GrblFaultContractTest`: scripted rejection, pending/delayed acknowledgement,
  unsolicited status separated from raw responses, interrupt cancellation,
  alarm and serial loss while an acknowledgement is outstanding.
- Existing production GRBL timeout, hold/resume, homing, pen-mode, read/write
  failure and replay checks remain in the suite.

The fake serial fixture now handles relative versus absolute XY, default servo
pen state, per-command response scripts and command latches. It does not acquire
a motion planner or become a firmware emulator.

## Reproduction

Local tooling: Linux x86-64, Java 21.0.13.1, Maven 3.9.9, isolated Xvfb display.
All mock profiles and artifacts are isolated. No real serial device is opened.

```bash
mvn clean package
python3 -m unittest discover -s scripts -p 'test_*.py'
xvfb-run -a ./scripts/test-gui.sh
```

Local verification: 588 Java tests, zero failures/errors/skips; nine Python
packaging tests; complete GUI mock/restart/recovery/import/preparation workflows
passed against the final packaged implementation. `git diff --check` and local
documentation link targets pass. The implementation PR also runs Java 17/21 GUI
checks and Windows/Linux native installer/upgrade acceptance; consult its checks
for the exact tested commit and platform results.

## Remaining acceptance

Hardware Stop/lift, limits, homing, serial latency, acceleration, planner queues,
pen mechanics and ink behavior still require the version-specific operator run
in #39/#54. Native DPI #43, actual participants #16 and a user task #72 remain
open. Drying-aware route planning #71 remains deferred.
