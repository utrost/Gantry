# Gantry adoption roadmap: 2 / 10 / 25 / 100 users

This roadmap complements the product roadmap in [`../ROADMAP.md`](../ROADMAP.md).
It focuses on getting Gantry from a capable personal tool to a small public tool
that other plotter users can install, trust, and return to.

The premise is adoption-led development: Gantry already has enough plotting
capability to test with people. The next work should reduce installation,
onboarding, safety, support, and confidence blockers before adding new plotting
features.

## Current baseline

As of July 2026, Gantry already provides a full SVG/image-to-plot workflow:
SVG processing, raster vectorization, command editing, hatching, optimization,
watercolor station mapping/refill, G-code streaming/export/replay, progress,
machine setup/calibration/pre-flight wizards, travel visualization, project
persistence, undo/recovery, a mock backend, and safe GRBL cancellation/error
handling.

Repository evidence at the start of this adoption plan:

- public repository: <https://github.com/utrost/Gantry>
- license: AGPL-3.0
- stack: Java 17, Maven, Swing/FlatLaf, multi-module architecture
- modules: `model`, `svgtoolbox-core`, `pipeline-core`, `watercolor`, `plotter`,
  `vectorize`, `cli`, `app`
- CI: GitHub Actions build/test matrix for Java 17 and 21
- release workflow: tag-triggered GitHub release artifacts and checksums
- docs already present: user guide, architecture, testing, usability, release
  checklist, novice-study protocol, roadmap history
- current active product gap: Beginner Usability Slice D — five-person novice
  validation and follow-up polish

The main adoption gap is not core capability. It is confidence: can someone who
is not Uwe download Gantry, understand the safe path, complete a mock plot, and
then use either exported G-code or a real GRBL connection without verbal coaching?

## Positioning

Gantry should not be introduced primarily as another SVG-to-G-code converter.
Its stronger position is:

> Gantry is a safe, beginner-aware plotting studio for GRBL pen plotters, with
> serious SVG preparation built in.

Useful short descriptions:

- From SVG to safe GRBL pen plot.
- A plotting studio for GRBL pen plotters.
- Prepare, preview, check, plot.

Suggested GitHub repository description:

> SVG/image-to-G-code plotting studio for GRBL pen plotters, with safe guided
> setup, preview, hatching, optimization, and optional watercolor workflows.

Recommended GitHub topics:

- `pen-plotter`
- `grbl`
- `gcode`
- `svg`
- `generative-art`
- `java`
- `watercolor`
- `creative-coding`

## Definition of a user

For this roadmap, do not count passive views or social likes as users.

Count someone as a Gantry user when they meet at least one of these levels:

1. **Mock user** — installs or launches Gantry and completes the guided mock
   first plot.
2. **Real user** — completes one real plot, or exports usable G-code for their
   own plotting workflow.
3. **Repeat user** — uses Gantry for at least two separate sessions or jobs.

Track users manually at first. A simple private sheet is enough: contacted,
installed, mock completed, real plot/export completed, blocker, would-use-again,
and notes.

## Guardrails

- Do not add new plotting features just because they are interesting.
- Promote new features only when real users show that they remove an adoption
  blocker or unlock a clearly repeated workflow.
- Preserve the safety invariant: Gantry must clearly distinguish preview/editing
  states from states where a real machine can move.
- Keep pen plotting first-class. Watercolor remains an advanced optional path.
- Prefer small public releases over a long hidden branch.
- Capture rejected/deferred ideas explicitly instead of letting scope drift.

## Milestone 1: 2 users

### Goal

Prove that one person other than the primary developer can get through Gantry
with minimal hand-holding.

This can be Uwe plus one external tester, or two external testers if a stricter
bar is desired.

### Product and code work

1. **Create the first public pre-release artifact**
   - Tag a version such as `v0.9.0` or `v1.0.0-alpha.1`.
   - Publish the GUI fat JAR, CLI fat JAR, checksums, license, and release notes.
   - Make the README say how to download and run before explaining how to build
     from source.

2. **Record one hardware acceptance pass**
   - Use [`RELEASE_CHECKLIST.md`](RELEASE_CHECKLIST.md).
   - Minimum evidence: app launch, guided mock practice, real GRBL connect, jog,
     frame, stop/cancel raises pen, G-code export, and one small real pen plot.
   - Commit the result under a release-results directory, for example
     `release-results/v0.9.0.md`.

3. **Add a known-good setup note**
   - Document the first verified machine setup: operating system, Java version,
     GRBL/controller, bed size, baud rate, pen-lift mode, and one sample SVG that
     plotted successfully.

4. **Seed the issue tracker**
   - Create a few honest starter issues so feedback has a place to land:
     installation notes, serial permissions, supported configurations, novice
     study, native launcher/installer, and troubleshooting.

### Organizational work

- Recruit one patient tester with a GRBL plotter or a willingness to complete the
  mock workflow first.
- Give the tester only the release link and the task: complete a guided mock
  plot, then export or run a small real plot.
- Observe where verbal explanation is tempting. Each explanation becomes either a
  docs fix, product fix, or explicit test-environment dependency.

### Marketing work

Keep this stage private-alpha. Do not broadcast broadly yet.

Suggested outreach text:

> I am testing Gantry, a Java plotting studio for GRBL pen plotters. It prepares
> SVGs/images, shows the bed, runs a safety check, and streams or exports G-code.
> I am looking for one patient tester to try the guided mock plot and, if
> possible, one small real plot.

### Exit criteria

- One person other than Uwe launches a release artifact without cloning/building.
- They complete the guided mock practice.
- Ideally they complete one real plot or produce usable G-code.
- At least five concrete friction points are captured.
- No safety-critical surprise occurs.

## Milestone 2: 10 users

### Goal

Turn one successful external test into repeatable onboarding.

### Product and code work

1. **Finish Beginner Usability Slice D**
   - Run the five-person novice study defined in [`NOVICE_STUDY.md`](NOVICE_STUDY.md).
   - Record results with [`NOVICE_STUDY_RESULTS_TEMPLATE.md`](NOVICE_STUDY_RESULTS_TEMPLATE.md).
   - Fix observed blockers and repeat affected sessions until the stated pass
     criteria are met.

2. **Add a support bundle path**
   - Add or plan a Help action such as **Copy diagnostics** or **Open logs/config
     folder**.
   - Include Gantry version, Java version, OS, selected backend, relevant config
     without secrets, last error, and recent log tail.

3. **Harden serial setup documentation**
   - Document Windows COM ports, macOS serial-device naming, Linux `dialout`,
     common CH340/CP210x boards, baud rate expectations, GRBL alarm/lock states,
     and how to test with the mock backend first.

4. **Create a sample gallery**
   - Include known-good SVGs/images for: simple line drawing, hatch/fill,
     multi-colour/layer, text outline, and image/vectorize.
   - For each sample, document expected preview size, useful settings, and what a
     successful plot/export looks like.

5. **Improve release instructions**
   - Keep source build instructions, but make release download the primary path.
   - Add platform-specific launch notes for Windows, macOS, and Linux.

### Organizational work

Recruit across different user types:

- plotter/CNC users
- generative-art/SVG users
- Java/open-source users
- complete novices who can do mock-only onboarding

Use GitHub Issues or Discussions as the support surface. Add labels such as
`first-run`, `hardware`, `serial`, `docs`, `good first issue`, `usability`,
`vectorize`, and `watercolor`.

### Marketing work

Do a low-key public tester call. Lead with a short demo GIF or video rather than
architecture.

Suggested public text:

> Gantry is a Java plotting studio for GRBL pen plotters. It imports SVGs or
> images, helps fit them safely on the bed, previews travel, runs a pre-plot
> check, and streams or exports G-code. I am looking for early testers with
> plotters, or people who just want to try the no-hardware mock workflow.

### Exit criteria

- 10 people launch Gantry.
- At least 7 complete the guided mock practice.
- At least 3 complete a real plot or usable G-code export.
- At least 5 concrete issues or feedback notes come from people other than Uwe.
- README and troubleshooting reflect actual support questions.
- No severe safety issue appears.

## Milestone 3: 25 users

### Goal

Make Gantry credible as an early open-source tool rather than a personal project.

### Product and code work

1. **Ship a stable `v1.0.0`**
   - Include release notes, screenshots, checksums, known limitations, hardware
     acceptance evidence, and migration/upgrade notes if config or project format
     changed.

2. **Decide on native packaging**
   - If JAR launching remains a blocker, provide at least zip bundles with
     platform launch scripts.
   - Full installers can wait, but the default run path should not require users
     to know Maven.

3. **Build a compatibility matrix**
   - Track GRBL version, controller board, OS, pen-lift mode, and who tested it.
   - Treat this as both documentation and trust-building.

4. **Strengthen safe dry-run workflows**
   - Document how to test without pen/paper.
   - Make G-code export and frame/check workflows easy to understand with
     screenshots.
   - Explain what Gantry sends before movement.

5. **Add contributor onboarding**
   - Add `CONTRIBUTING.md`, issue templates, a bug report template, and a
     hardware report template.

6. **Watch architecture pressure points**
   - Periodically inspect `PlotterPanel`, `VisualizationPanel`, and import or
     vectorizer dialogs.
   - If they start accumulating unrelated responsibilities, open refactoring
     issues before user-driven changes make them harder to split.

### Organizational work

- Move from a tester list to a small community surface.
- Use Discussions for show-and-tell, hardware setups, and questions.
- Keep a public roadmap split into Now / Next / Later / Not planned.
- Maintain issue hygiene and avoid accepting hardware support promises without a
  tester who owns that hardware.

### Marketing work

Create three durable assets:

1. a one-minute demo video: import, fit, mock plot, export or real plot, final drawing;
2. a plain “First plot with Gantry” article;
3. a gallery page with outputs, source files, and settings where possible.

Avoid overclaiming. Do not call Gantry universal plotter software. Be explicit
about tested hardware and known limits.

### Exit criteria

- 25 people have launched Gantry.
- 10 or more complete the mock first plot.
- 8 or more complete a real plot or export.
- 3 or more hardware configurations are documented.
- At least one external person contributes an issue with diagnostics.
- At least one external PR, documentation improvement, or clearly engaged repeat
  tester exists.
- A public release exists and is installable without cloning/building.

## Milestone 4: 100 users

### Goal

Make Gantry a sustainable niche project.

At this point the main challenge is not whether Gantry works. It is whether the
project can absorb feedback without becoming chaotic.

### Product and code work

1. **Stabilize file formats**
   - Version `.gantry` project files and configuration schema.
   - Add migration tests and document compatibility expectations.

2. **Add extension boundaries only when demand proves them**
   - Possible future extension points: import processors, vectorization
     strategies, plotter backends, and export formats.
   - Do not build a plugin system speculatively.

3. **Strengthen release quality gates**
   - Add artifact launch checks, CLI smoke tests, checksum verification, and
     release smoke checks to the release process where practical.

4. **Make support reproducible**
   - Support bundle, issue templates, versioned diagnostics, and a clear path from
     user report to reproducible test.

5. **Restructure docs if needed**
   - Keep README short.
   - Split user docs into quick start, first plot, hardware setup,
     troubleshooting, advanced processing, watercolor, CLI, and developer guide.

6. **Clarify project stance**
   - State AGPL contribution expectations, no-warranty machine-motion safety
     limits, and what kinds of hardware/backends are in or out of scope.

### Organizational work

- Establish lightweight governance: maintainer, contribution rules, release
  cadence, and support expectations.
- Maintain a trusted tester group of 3 to 5 people for release candidates.
- Keep the compatibility matrix evidence-based.
- Close stale or unreproducible issues rather than letting the tracker become a
  vague wish list.

### Marketing work

At 100 users, marketing is mostly community proof:

- user plot gallery;
- compatibility matrix;
- short release posts;
- before/after SVG processing examples;
- a “why Gantry exists” article;
- careful comparison language focused on Gantry's own fit, not on attacking other
  tools.

### Exit criteria

- 100 confirmed launches/downloads/testers, or a defensible proxy such as release
  downloads plus distinct feedback participants.
- 25 or more people complete the mock workflow.
- 20 or more complete a real plot or export.
- 10 or more hardware setups are documented.
- 5 or more external issue reporters exist.
- 2 or more external contributors or repeat testers exist.
- Release process is routine and support load is manageable.

## Immediate adoption backlog

Do these in order. Items marked **done** are resolved in the repository or GitHub
metadata; items marked **blocked/manual** need a release tag, real hardware,
external people, or media production.

1. **Done:** Add GitHub repository description and topics.
2. **Done:** Create the first public pre-release artifact —
   [`v1.0.0-alpha.1`](https://github.com/utrost/Gantry/releases/tag/v1.0.0-alpha.1)
   is published and tracked in [#14](https://github.com/utrost/Gantry/issues/14).
3. **Blocked/manual:** Run and record one real hardware acceptance pass — tracked
   in [#15](https://github.com/utrost/Gantry/issues/15).
4. **Blocked/manual:** Recruit one external tester for the 2-user milestone.
5. **Done:** Seed GitHub issue labels and issue templates.
6. **Blocked/manual:** Finish the five-person novice study — tracked in
   [#16](https://github.com/utrost/Gantry/issues/16).
7. **Done:** Write a “First plot with Gantry” quickstart. Screenshots can be
   added after the next guided-practice capture.
8. **Done:** Add serial-permission and GRBL-alarm troubleshooting.
9. **Blocked/manual:** Publish a one-minute demo video or GIF — tracked in
   [#18](https://github.com/utrost/Gantry/issues/18).
10. **Started:** Start the compatibility matrix from real tester reports. The
    matrix document exists; real entries still need acceptance evidence.

Additional support hardening now tracked in
[#17](https://github.com/utrost/Gantry/issues/17).

## Operating rhythm

For every adoption round:

1. release or prepare a clearly identified build;
2. invite a small number of users;
3. observe or collect structured feedback;
4. classify each blocker as code, docs, packaging, safety, or expectation;
5. fix the top blockers;
6. update docs and release notes;
7. repeat.

The working motto for this phase is:

> Validate, package, recruit, observe, fix, repeat.
