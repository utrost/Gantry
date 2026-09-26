# Release assessment — 2026-09-26

Assessed `main` at `b360551`. `git pull --ff-only` reported already up to date.
Target platforms: Windows and Linux. Retain the 1.0 release line.

## Recommendation

Prepare an installer-bearing release candidate, `v1.0.0-rc.2`. The existing
`v1.0.0-rc.1` is publicly released and current main is 32 commits ahead of it;
keep that tag and its assets intact. This is a distribution and acceptance
milestone rather than a need for another large feature cycle.

The application is credible for further tester distribution. Automated results
are healthy, but they do not establish installed-app behavior or hardware
readiness. Do not promote to stable 1.0 until the recorded acceptance gaps close.

## Evidence

- Local Java 17.0.20.1 / Maven 3.9.9: `mvn -B clean package` passed across all
  eight application modules; 459 tests, zero failures, errors, or skips.
- Built CLI JAR: `java -jar cli/target/cli-1.0.0.jar --help` passed.
- Latest GitHub CI run `34613956369` passed on Java 17 and Java 21.
- GitHub has published `v1.0.0-alpha.1` and `v1.0.0-rc.1`, both pre-releases.
- Existing release automation publishes GUI/CLI fat JARs, checksums and docs.
- No interactive GUI, Windows installer, or real plotter acceptance was performed
  during this assessment. CI uses headless tests; it does not launch the GUI.

## Findings, ordered by release importance

1. **Installer distribution is missing.** `.github/workflows/release.yml` has a
   single Ubuntu job and publishes JARs only. Add native Windows/Linux packaging
   jobs, runtime bundling, installer checksums and a draft release assembly step.
   A successful Maven build alone does not verify an installer.
2. **History and recovery still depend on the launch directory.**
   `PlotterPanel.java` constructs `plot-history.json` and `.gantry-recovery` as
   relative files. Installed launchers may run in an unwritable directory or a
   different directory between launches. History writes silently ignore IO
   failures; recovery reports failures. Move these files into a stable per-user
   data location and verify migration. `ConfigStore` already resolves GUI
   configuration into an OS-specific user directory.
3. **Release version reporting is inconsistent.** The About dialog hardcodes
   `1.0.0`; diagnostics fall back to `1.0.0-dev`; the built manifest has no
   Implementation-Version. Release scripts rename version-1.0.0 JARs without
   changing embedded identity. Inject one release version into manifest, About,
   diagnostics and artifact names. Native installer version constraints may
   require numeric `1.0.0`, with the RC identity in filenames and application
   metadata. Verify that RC-to-stable upgrades work.
4. **Acceptance evidence remains incomplete.** Existing release records leave
   guided mock practice and hardware checks pending. The Uuna Tek A1 H entry
   records a working report, but its detailed acceptance fields are unfinished.
   RC notes also incorrectly imply CI checks Java 21 GUI launch; correct that
   claim when preparing new notes.
5. **Public entry documentation is stale.** README still demonstrates the
   alpha JAR despite an existing RC. Make Windows/Linux downloads the first-run
   path, with source/JAR instructions secondary and explicit installer support
   boundaries (OS versions and CPU architectures).

## Project health

Strengths: useful end-to-end SVG/raster preparation, composition, machine-aware
preview, mock plotting, G-code export/streaming, recovery, diagnostics and
optional watercolor controls. Module boundaries separate processing, hardware,
CLI and GUI concerns. Tests and architecture documentation provide a solid base.
Recent changes include settings persistence, plot lifecycle hardening, homing
safety, composition and per-layer controls.

Maintenance concerns: the GUI remains concentrated in large classes, especially
PlotterPanel (1,857 lines), VisualizationPanel (1,126), and VectorizeStudioDialog
(856). These are future refactoring targets, not reasons to block an installer
candidate. Dependency vulnerability/license auditing and exhaustive code review
were outside this assessment; test success is not evidence of either.

## Concrete Windows/Linux release plan

1. Fix persistent state locations and version identity, with focused regression
   tests for launch-directory independence and existing-user migration.
2. Use JDK `jpackage` to bundle the app and Java runtime. Build Windows `.msi`
   on a Windows runner and Linux `.deb` on an Ubuntu runner. Define x86-64 as
   the initial architecture; add RPM/other architectures only with matching
   build and smoke-test coverage. Preserve CLI JAR downloads.
3. Build and validate artifacts on pull requests or manual workflow dispatch
   before tagging. Stage a draft GitHub release only after all platform jobs
   succeed, with an explicit artifact list and consolidated SHA-256 checksums.
4. Check installation, menu launch, bundled-runtime launch without system Java,
   settings/history/recovery persistence, upgrade and uninstall behavior. Exercise
   SVG import, raster import, guided mock plotting, export and serial discovery
   using the installed app. Decide Windows signing before broad distribution.
5. Record a small real plot and required homing, pen-lift, frame, stop/alarm
   checks; include watercolor checks only for workflows being claimed.
6. Update README, release notes and release-results, then tag/publish the new RC.

`jpackage` supports non-modular fat JARs and bundled runtimes, but native packages
must be built on their target platform. JDK 17 Windows packaging requires WiX;
Linux DEB packaging requires its native packaging prerequisites. See Oracle's
[packaging overview](https://docs.oracle.com/en/java/javase/17/jpackage/packaging-overview.html)
and [jpackage reference](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jpackage.html).

## Linux packaging feasibility check

A local `jpackage --type deb` prototype completed successfully (65,032,132 bytes).
`dpkg-deb` verified its metadata and contents, including the native launcher,
GUI JAR, bundled runtime and desktop entry. It was not installed or launched.
The temporary prototype used the initial `0.3.0` suggestion before the decision
to retain 1.0; it is not a release artifact and remains under
`/tmp/gantry-package-assessment/` only. Packaging feasibility is demonstrated,
not release readiness.

The Ubuntu 24.04 host generated dependencies including `libasound2t64` and
`libglib2.0-0t64`. Do not assume this package supports older Ubuntu/Debian
releases. Choose and test the oldest supported Linux build baseline explicitly.
