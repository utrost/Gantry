# Gantry novice usability study

Use this protocol to validate Beginner Usability Slice D with five people who
have not used Gantry and preferably have little or no plotter experience. The
goal is to observe the product, not teach plotting.

## Prepare each session

1. Use the same Gantry build and a fresh working directory for every person.
   It must contain no `config.json`, recovery file, project, or plot history.
2. Launch Gantry so **Your first plot** is open when the participant begins.
   Do not configure the mock backend for them: guided practice does that itself.
3. Use a 1024×800 or larger screen. Record the display scale and operating
   system in the result sheet.
4. Start screen recording only with the participant's consent. Do not collect
   names or unrelated screen content; identify people as P1–P5.

Do not pre-open Advanced controls, the Console, the user guide, or an import
dialog. Do not leave another participant's project or history visible.

## Introduction to read verbatim

> Gantry controls a drawing machine called a pen plotter. For this session it is
> connected to a simulation, so no physical machine can move. Please think
> aloud. I am testing the application, not you, and you can stop at any time.

Then give only this task:

> Use Gantry to prepare and complete a simulated practice plot. Nothing physical
> is connected, so no real machine can move.

Do not define plotter terms or provide steps. If the participant asks for help,
reply once with “Please do what seems most natural.” Any further procedural help
is recorded verbatim and makes the run assisted.

## What to record

Start the timer when the task is read. Observe without coaching and record:

- completion and whether it was unassisted;
- time to first visible artwork and completed plot;
- every hesitation longer than ten seconds and the screen/state where it began;
- every term the participant asks to have explained;
- unsafe or unintended actions;
- whether they open Advanced controls, Console, or the guide, and why;
- cancellation/error recovery and the action they chose next;
- whether they can state when a real machine could move;
- confidence from 1 (not confident) to 5 (very confident).

After the task, ask only:

1. “When could a real plotter have moved during that task?”
2. “What, if anything, felt unclear or surprising?”
3. “How confident would you feel doing the same task again, from 1 to 5?”

## Pass criteria

Slice D novice evidence passes only when:

- at least four of five people complete without procedural help;
- all five understand when the machine can move;
- there is no safety-critical mistake;
- median time to visible artwork is under two minutes;
- median completion time is under ten minutes;
- nobody needs Commands JSON, G-code, axis inversion, curve step, or station
  internals for the task.

Every explanation the facilitator gives is a usability defect or an explicit
test-environment dependency. Fix product blockers, reset the study profile, and
repeat affected sessions; do not silently reinterpret a failed run.

Copy [`NOVICE_STUDY_RESULTS_TEMPLATE.md`](NOVICE_STUDY_RESULTS_TEMPLATE.md) for
each study round and commit the anonymized result with the tested build.
