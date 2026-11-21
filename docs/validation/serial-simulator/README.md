# Serial simulator Swing evidence

Captured from the development JAR by `SerialSimulatorAcceptance` under Linux/Xvfb
at 1200×850, with an additional 1024×800 workspace check. An isolated profile selects the serial simulator through Settings;
its physical serial-port name is deliberately invalid. No hardware was opened.
See the [verification record](../../release-results/2026-11-21-completion-serial-simulator.md)
for commands, tests and limitations. These changes are after the preserved RC13 release.

| State | Actual Swing capture |
|---|---|
| Compact workspace; connection, Machine and Stop remain visible | [1024×800](compact.png) |
| Explicit mode selection | [Settings](settings.png) |
| Station dry/wet workflow | [Station check](station-check.png) |
| Queued parking; next pen unavailable, Stop available | [Completion wait](parking-wait.png) |
| Final successful simulation | [Complete](complete.png) |
| Stop while waiting; no new successful history | [Stopped](stopped.png) |
| Production reader surfaces injected command rejection | [Rejected command](rejected-command.png) |

The tiny two-layer drawing isolates handoff behavior; these images are workflow
evidence, not artwork quality or physical motion validation. Motion/dwell are
accelerated and physical pen, endstop and pigment behavior are not simulated.
