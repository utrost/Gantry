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

## Candidate: primary development setup

This entry still needs a recorded hardware acceptance pass before it should be
used as public compatibility evidence.

- Date: pending
- Tester: Uwe
- Gantry version or commit: pending
- Operating system: pending
- Java version: pending
- Plotter/mechanics: GRBL-based pen plotter, details pending
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
- Result: pending
- Caveats: do not cite as known-good until acceptance evidence exists
- Linked release result or issue: pending
