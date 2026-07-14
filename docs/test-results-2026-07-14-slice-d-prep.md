# Slice D preparation and polish — 2026-07-14

## Run metadata

| Field | Value |
|---|---|
| Tester | Codex automated and GUI inspection |
| Build | `feature/beginner-usability-slice-d` working tree |
| Backend | No-hardware UI state plus prior mock-workflow baseline |
| OS | macOS, Temurin 17 |
| Automated result | PASS — full 9-module reactor, 352 tests total (105 app tests), zero failures |

## Focused results

| Area | Result | Evidence |
|---|---|---|
| Completion guidance | PASS (automated) | `OperatorJourneyTest` confirms a successful plot remains visibly complete, offers **Plot another copy**, and routes that action through the safety check. Editing the drawing or job settings invalidates the same-copy offer. |
| Minimum window | PASS | The empty beginner workspace was inspected through the actual Swing UI at a 1024×775 usable window area, corresponding to a 1024×800 screen with the macOS menu bar. The guidance action, artwork card, safety state, and complete 300 px control column remained visible. |
| Display scaling | PASS | The minimum-size workspace was launched and inspected with `-Dsun.java2d.uiScale=1` and `-Dsun.java2d.uiScale=2`; neither view clipped the primary workflow controls. |
| First-run documentation | PASS | A clean temporary profile produced the complete **Your first plot** dialog and workspace captured in `docs/images/first-run-guided-practice.png`. |
| Five-person novice study | INCOMPLETE | The fixed protocol and anonymized result template are ready, but no participant evidence has been collected. |
| Hardware acceptance | BLOCKED | No physical plotter was in scope for this run. |

The full mock workflow was exercised in the Slice C acceptance run. This Slice D
preparation run does not claim a new participant study, a hardware run, or
completion of Beginner Usability Milestone 9.
