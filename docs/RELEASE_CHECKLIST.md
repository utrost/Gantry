# Gantry release checklist

1. Run `./scripts/release.sh <version>` and confirm the reactor is green.
2. Copy `TEST_RESULTS_TEMPLATE.md` to `release-results/<version>.md` and record
   the complete mock acceptance suite.
3. Run every hardware-marked test on a real plotter: connect/disconnect,
   home/jog/limits, pen lift, frame, stop/alarm recovery, scale calibration,
   station dry/wet visit, and a small pen plus watercolor job.
4. Confirm `SHA256SUMS`, launch the standalone app JAR on Java 17 and Java 21,
   and smoke-test the CLI JAR.
5. Commit the recorded result, tag `v<version>`, and push the tag. The release
   workflow publishes both JARs, checksums, license, and release notes.

A release is hardware-ready only when the recorded hardware result contains no
unexplained failures or blocked safety tests.
