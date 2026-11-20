# Mock fidelity audit — 2026-11-20

Audited revision: main `3462659`. This run used Java 21, an isolated Xvfb display
and mock/file/in-memory serial backends only. No physical machine was connected.
The observed limitations are tracked in [#78](https://github.com/utrost/Gantry/issues/78)
(Stop/interruption) and [#79](https://github.com/utrost/Gantry/issues/79)
(backend contracts, state/raw-command behavior and fault scenarios).

## Passing software checks

The following existing tests were rerun successfully, with zero failures, errors
or skips. Their assertions establish their scripted scenarios, not equivalence
to a real controller.

| Test class | Tests |
|---|---:|
| MockGrblEmulationTest | 3 |
| GcodeBackendTest | 25 |
| GcodeFileReplayTest | 5 |
| PlotServiceTest | 15 |
| PlotJobControllerTest | 16 |
| InstallationCheckTest | 3 |
| Total | 67 |

The complete `scripts/test-gui.sh` suite also passed: mock plotting and pen gates,
Stop/lifecycle, restart recovery, imports, failure handling, historical job review
and the integrated label/undo/save/SVG/ZIP/overlap workflow. The passing GUI Stop
checks do not prove that mock motion itself stopped at its interrupted position.

Reproduce the targeted test selection:

```bash
mvn -pl app -am \
  -Dtest=MockGrblEmulationTest,GcodeBackendTest,GcodeFileReplayTest,PlotServiceTest,PlotJobControllerTest,InstallationCheckTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
xvfb-run -a ./scripts/test-gui.sh
```

## Confirmed mock defects

A separate temporary Java probe called the shipped `MockPlotterBackend` directly.
These are observed defects, not expected contract behavior to preserve in tests.

| Probe | Observation at 3462659 |
|---|---|
| `sendRaw("THIS IS NOT GCODE")` | Returns `ok` |
| Send raw `G21`, `G90`, `G1 X10 Y20 F1000` | Returns `ok`; queried position remains (0,0) |
| Connect, disconnect, then call `moveto(0,0)` | Accepted; no connection lifecycle is enforced |
| Start `moveto(100,0)` at 6000 mm/min, query during movement | Status reports `Idle` while the worker is running |
| Call `haltMotion()` during that move | Inherited no-op; movement remains active |
| Interrupt the worker at roughly 10 mm | Worker ends with reported X=100 mm, rather than stopping at the last reached position |

The movement loop calls `sleepQuietly`, which restores the interruption flag and
returns; subsequent loop iterations still advance the coordinates. The current
mock also logs pen changes without retaining an assertable pen-state model.

Code evidence:

- [MockPlotterBackend at the audited revision](https://github.com/utrost/Gantry/blob/3462659/plotter/src/main/java/org/trostheide/gantry/plotter/MockPlotterBackend.java)
- [PlotterBackend default haltMotion](https://github.com/utrost/Gantry/blob/3462659/plotter/src/main/java/org/trostheide/gantry/plotter/PlotterBackend.java)

## What each layer establishes

| Layer | Useful evidence | Limits |
|---|---|---|
| GUI mock | Tested editing, review, prompts, ownership, feedback and persistence paths | Known Stop/state/raw/connection defects; no firmware or physical behavior |
| Production GcodeBackend + FakeGrblTransport | Actual formatting, reader/poller, response waits, scripted alarm/hold/disconnect and recovery code | Fake normally acknowledges lines and updates XY immediately; injected scenarios are not complete GRBL behavior |
| Real controller and mechanics | Physical limits, homing, serial adapter behavior, actual stop/lift and timing on the tested setup | Revision/configuration-specific; requires an operator record |

Continue software work in #78/#79 without a plotter. Keep physical acceptance
#39/#54, native scaling #43, actual participants #16 and the user task in #72 open.
A stronger simulator still will not establish physical acceleration, braking,
planner buffering, limit-switch mechanics, ink behavior or pen-lift safety.
