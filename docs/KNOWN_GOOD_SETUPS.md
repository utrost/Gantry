# Known-good Gantry setups

Use this file to record hardware and operating-system combinations that have
successfully completed Gantry acceptance checks. Keep entries evidence-based:
include what was tested, the Gantry version or commit, and any caveats.

A setup is **known good** only for the workflows recorded in its entry. For
example, a pen-plot acceptance run does not prove watercolor station behavior.

## Entry template

```markdown
## <short setup name>

- Date:
- Tester:
- Gantry version or commit:
- Operating system:
- Java version:
- Plotter/mechanics:
- Controller / GRBL version:
- Connection:
- Baud rate:
- Machine travel:
- Origin corner:
- Pen mode:
- Tested workflows:
  - [ ] guided mock practice
  - [ ] connect/disconnect
  - [ ] home
  - [ ] jog directions
  - [ ] frame job
  - [ ] stop/cancel raises pen
  - [ ] G-code export
  - [ ] small real pen plot
  - [ ] watercolor station dry visit
  - [ ] watercolor wet/refill job
- Result:
- Caveats:
- Linked release result or issue:
```

## Uwe primary setup — Uuna Tek A1 H plotter

Gantry has been reported working on this hardware by Uwe. This is the first
real-hardware compatibility entry, but the detailed release-acceptance checklist
still needs to be filled before making broad hardware-readiness claims.

- Date: 2026-08-17
- Tester: Uwe
- Gantry version or commit: `29f5c03`
- Operating system: Linux 6.8.0-136-generic x86_64 GNU/Linux
- Java version: OpenJDK 17.0.19
- Plotter/mechanics: Uuna Tek A1 H pen plotter
- Controller / GRBL version: pending
- Connection: USB serial, pending details
- Baud rate: expected 115200 unless acceptance records otherwise
- Machine travel: pending
- Origin corner: pending
- Pen mode: pending
- Tested workflows:
  - [ ] guided mock practice
  - [ ] connect/disconnect
  - [ ] home
  - [ ] jog directions
  - [ ] frame job
  - [ ] stop/cancel raises pen
  - [ ] G-code export
  - [ ] small real pen plot
  - [ ] watercolor station dry visit
  - [ ] watercolor wet/refill job
- Result: Gantry works on this hardware per Uwe's report.
- Caveats: detailed acceptance evidence, controller/GRBL version, travel,
  origin, pen mode, and exact tested workflow list are still pending.
- Linked release result or issue: [#15](https://github.com/utrost/Gantry/issues/15)
