# Processing studio headful acceptance — 2026-07-15

Environment: macOS, Temurin 17, actual Swing UI, repository `colours.svg`.

| Check | Result | Evidence |
|---|---|---|
| Supported minimum | PASS | Main window set to the 1024×775 usable area of a 1024×800 screen. The re-process studio opened at 980×720 wholly inside it. |
| Real import path | PASS | Invoked **Add SVG or vector drawing** with the real ⌘I shortcut and selected `testdata/colours.svg`; processing ran outside the EDT and the drawing loaded. |
| Re-process entry | PASS | Invoked the new ⌘R shortcut after SVG import; **Adjust artwork processing** opened against the recorded source. |
| Preview and impact | PASS | Both **Original** and **With these settings** panes were present. The completed impact row reported `5 → 5 strokes`, `60 → 60 points`, and comparative rough plot times. |
| Progressive disclosure/accessibility | PASS | Accessibility exposed the goal preset, plain-language Look/fill/optimization controls, sliders plus exact-value steppers, fine-tuning disclosures, Reset, Apply, and Cancel. |
| Automated regressions | PASS | Full pinned-JDK headless reactor: app 118 tests; all reactor modules successful. |

Evidence boundary: this run confirms launch, minimum-size layout, real source
workflow, accessible controls, and completed preview rendering. Automated tests
cover preset/config mappings, conflict/context behavior, exact project recipe
round-trip, and recipe-aware undo/redo. A manual visual sweep of every preset at
2× display scaling and physically clicking Cancel during a deliberately slow
processor remains release acceptance, not claimed here.
