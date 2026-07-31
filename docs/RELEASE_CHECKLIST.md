# Gantry release checklist

## Public alpha / pre-release

Use this path for early tester builds that do **not** yet claim broad hardware
readiness.

1. Confirm `docs/release-notes/<version>.md` exists for the tag version.
2. Run or rely on CI for `mvn clean package` and `mvn test` on Java 17 and 21.
3. Tag `v<version>` and push the tag. Tags containing a hyphen, such as
   `v1.0.0-alpha.1`, are published as GitHub pre-releases.
4. Confirm the release contains `Gantry-<version>.jar`,
   `Gantry-CLI-<version>.jar`, `SHA256SUMS`, `LICENSE`, and `README.md`.
5. Record artifact and mock-practice checks in `release-results/<version>.md`.
6. Keep hardware readiness claims out of release notes until a real acceptance
   run has been recorded.

## Hardware-ready release

1. Run `./scripts/release.sh <version>` and confirm the reactor is green.
2. Copy `TEST_RESULTS_TEMPLATE.md` to `release-results/<version>.md` and record
   the complete mock acceptance suite.
3. For a release claiming completion of Beginner Usability Milestone 9, attach a
   passing five-participant result recorded from `NOVICE_STUDY.md`. Automated or
   agent-driven UI checks are supporting evidence, not a substitute.
4. Run every hardware-marked test on a real plotter: connect/disconnect,
   home/jog/limits, pen lift, frame, stop/alarm recovery, scale calibration,
   station dry/wet visit, and a small pen plus watercolor job.
5. Confirm `SHA256SUMS`, launch the standalone app JAR on Java 17 and Java 21,
   and smoke-test the CLI JAR.
6. Commit the recorded result, tag `v<version>`, and push the tag. The release
   workflow publishes both JARs, checksums, license, and release notes.

A release is hardware-ready only when the recorded hardware result contains no
unexplained failures or blocked safety tests.
