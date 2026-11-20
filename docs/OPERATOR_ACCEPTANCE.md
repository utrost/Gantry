# Operator acceptance after RC11

The [RC12 worksheet](release-results/1.0.0-rc.12-operator.md) is ready for session
identity, task observations, scaling combinations and benchmark timings.

Status: **not run**. This is a checklist for the next available operator session,
not evidence of a pass. RC11 remains the latest public download. The RC12 draft
at `8dcb772` contains the integrated features, with verified Windows/Linux assets;
public distribution is held for orientation issue #93. See the
[candidate record](release-results/1.0.0-rc.12.md) for artifact identity and hashes.
Never replace published RC11 assets with changed code.

Record results in `docs/release-results/<version>-operator.md`. Use PASS, FAIL,
BLOCKED or N/A per row, with a short observation. Record version/commit, installer
checksum, date, OS, display resolution/effective scale, controller/firmware, baud,
travel, origin, pen-lift mode, pen/paper and effective width. Add logs or photos
where useful; omit private paths and connection details from public captures.

## Desktop and expert workflow — #43, #54

Start with an isolated mock profile. Do not overwrite an existing machine profile.

| Check | Expected result | Result |
|---|---|---|
| Install/launch and About | Candidate identity matches downloaded artifact; bundled Java works | NOT RUN |
| Light/Dark, both densities, 1024×800 and larger | Pinned Machine, ordinary placement fields and three layer rows coexist; Stop stays reachable without scrolling | NOT RUN |
| Actual 100/125/150/200% desktop scaling | Record effective scale and logical window size; unavailable combinations are BLOCKED, never a pass | NOT RUN |
| Many layers/long names and open diagnostics | Focused fields and job controls remain accessible | NOT RUN |
| Numeric Enter/Tab/Escape and keyboard scope | Commit/revert works; editing arrows do not jog | NOT RUN |
| Expert benchmark | Time SVG → position → pen setup → plot → repeat on RC11 and the new revision using the same sample/settings; record time, extra steps and mistakes | NOT RUN |

For the benchmark, use [multi-colour layers](samples/multi-colour-layers.svg).
Use simulation first and keep real movement separate from desktop timing.

## Changed manual operations — #39, #54

Use the known Windows 11/iDraw H A1 setup. Begin with the pen removed, clear travel
and verified machine limits. Keep the controller's physical stop accessible.
A software Stop is not an emergency-stop guarantee. Do not induce disconnects or
alarms until the stationary/pen-up tests are understood and controlled.

| Check | Expected result | Result |
|---|---|---|
| Connect/disconnect, home and small jogs | Correct identity, directions and limits; no unexpected automatic movement | NOT RUN |
| Pen up/down test | Configured lift works; state and feedback agree | NOT RUN |
| Frame a small in-bed drawing | Pen-up bounds agree with review; visible, keyboard-reachable Stop throughout | NOT RUN |
| Stop during Frame/home/calibration move | No later movement in that sequence; halt/lift attempted; conflicting actions wait until cleanup finishes | NOT RUN |
| Repeated Stop | Cleanup is not interrupted or restarted | NOT RUN |
| Modal calibration/station dry visit | Stop remains accessible; Close/Escape waits for cleanup; no orphan operation | NOT RUN |
| Controlled controller error/alarm and recovery | Explicit failure, halted sequence, best-effort pen lift where controller permits, deliberate recovery | NOT RUN |
| Small pen plot and layer confirmation | Only selected layers run, requested pen/order agree with review, no automatic continuation | NOT RUN |
| Stop mid-plot, then recheck/repeat | Halt/lift attempted; fresh review required; exact stored job is previewed before repeat | NOT RUN |

Use [the sample gallery](SAMPLE_GALLERY.md) for a small suitable job. Record
any skipped operation explicitly. Watercolor wet/refill behavior requires its own
controlled run if hardware readiness for watercolor is claimed.

## Integrated preparation features — #68–#72

| Check | Expected result | Result |
|---|---|---|
| Share one named pen between two layers | Name/colour/width update together; save/reopen and Undo/Redo preserve assignments | NOT RUN |
| Add a 10 mm “Max Mustermann” label and frame | Upright readable stroke text and physical size; edit after reopen; existing artwork is preserved | NOT RUN |
| Position/exclude layers, add passes, export SVG/ZIP | Reviewed geometry/order/inclusion match exports; package opens in another SVG tool with usable layer separation | NOT RUN |
| Review an older historical job | Stored geometry is used; missing widths are explicitly assumed, never borrowed from the current document | NOT RUN |
| Review a new historical job | Stored named pen and width survive reopening and SVG export | NOT RUN |
| Ink-overlap analysis and Cancel | Crossings/repeated passes are identified; per-pen views agree with assignments; no machine commands occur | NOT RUN |
| Pen/paper swatch and dense sample | Record measured coverage/usefulness separately; geometric heatmap does not prove spread or drying | NOT RUN |

For #72, record the actual task, operator, result and friction. An automated
label regression does not satisfy the real-user-task acceptance criterion.
Drying-aware route planning (#71) is not implemented and is outside this run.

## External participants — #16, #54

Give the first independent tester only the release link and [FIRST_PLOT.md](FIRST_PLOT.md).
Record completion, time, any coaching and at least five concrete friction points.
For the five-person study, use [NOVICE_STUDY.md](NOVICE_STUDY.md) and its
[results template](NOVICE_STUDY_RESULTS_TEMPLATE.md). Do not count mock automation
or the maintainer's own run as participant evidence.
