# Documentation status — RC11 and main, 2026-11-20

The public entry points, release links, offline help links and screenshot
references were checked against RC11. Current validation is recorded in
[the RC11 release record](release-results/1.0.0-rc.11.md). A full human review of
every operator-guide instruction remains open.
All 44 gallery screenshots and the one-minute mock walkthrough were refreshed
from successful RC11 CI capture jobs on 2026-11-15.

| Area | Current documentation |
|---|---|
| Install and first plot | README, FIRST_PLOT, packaging help; Windows MSI/Linux DEB include Java |
| Daily operation | USER_GUIDE, SAMPLE_GALLERY, TROUBLESHOOTING; Dark default and visible disabled actions |
| Pen-aware filling | Effective width/overlap, physical scale, boundary inset, coverage preview, profiles and calibration |
| Developer reference | ARCHITECTURE, TESTING, REFACTORING and PROJECT_DIGEST |
| Release and adoption | ROADMAP, ADOPTION_ROADMAP, RELEASE_CHECKLIST and RC11 notes/results |
| Visuals | All 44 screenshots and the mock walkthrough refreshed from RC11 CI |
| Historical evidence | Dated prior release/test records retain their original observations; they are not current acceptance claims |
| Future work | TRACER_CAPTURE_IMPORT remains a proposed handoff contract |

The [image gallery](images/README.md) describes provenance and reproduction.
The mock demo contains no hardware motion. Earlier Windows 11 / iDraw H A1
reports do not establish physical acceptance of RC11. Pen/paper swatch evaluation,
external novice/expert studies and broader hardware coverage require operators.

## Development changes after RC11

The source guide now also covers named pens, quick artwork, reviewed SVG/ZIP
exports and advisory whole-job overlap. These changes are for the next revision;
the preserved public RC11 artifacts do not contain them. See the
[integration evidence](release-results/2026-11-20-integration.md) and
[operator checklist](OPERATOR_ACCEPTANCE.md). Existing screenshots retain their
recorded RC11 provenance rather than implying that all new dialogs are pictured.

## Roadmap and tracker reconciliation

The active roadmap now separates delivered main changes, software work that can
run without hardware, operator/participant acceptance and deferred candidates.
Detailed research is preserved in [FEATURE_CANDIDATES.md](FEATURE_CANDIDATES.md).
The [mock fidelity audit](release-results/2026-11-20-mock-fidelity.md) qualifies
simulation evidence and feeds #78/#79; it does not revise historical test passes.

## Backend simulation contracts

[BACKEND_CONTRACT.md](BACKEND_CONTRACT.md) records supported simulator/raw-command
capabilities and the differences from serial and file backends. The #78/#79
[follow-up record](release-results/2026-11-20-backend-contracts.md) tracks the
corrected behavior; the original audit stays dated historical evidence. Physical
acceptance and participant gates remain open.
