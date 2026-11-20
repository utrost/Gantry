# Packaged CLI reliability — five issues, 2026-11-20

Baseline: `8202f73`; branch: `fix/packaged-cli-reliability`.
Issues: [#84](https://github.com/utrost/Gantry/issues/84),
[#88](https://github.com/utrost/Gantry/issues/88),
[#89](https://github.com/utrost/Gantry/issues/89),
[#90](https://github.com/utrost/Gantry/issues/90),
[#91](https://github.com/utrost/Gantry/issues/91).
All are existing software defects, reproduced before implementation. The open
hardware, native desktop and participant acceptance issues are not substituted
or closed by this work. Public RC11 assets remain unchanged.

## Observed defects and resulting behavior

| Issue | Verified previous behavior | Result |
|---|---|---|
| #84 | Packaged PNG export fails with missing Batik PNG WriteAdapter. Missing PDF support returns success. | Bundle batik-codec; PNG decodes with drawing marks and chained command output. Unsupported PDF/unknown formats fail clearly before SVG generation. PNG/PDF render staging preserves existing destinations on failure. |
| #88 | Custom Squiggle sidecar omits Squiggle controls/crop; nested common/style/boolean settings are not consistently restored. | Version-2 sidecars record effective common parameters, crop, format and complete strategy settings, including automatic PBN palette count. Reader supports legacy nested settings. Non-default SVG geometry/style/dimensions rerun exactly. |
| #89 | --config ignores explicit strategy/source/style overrides, except output. Embedder entry point does not resolve configs consistently. | Canonical short/long aliases merge once: saved settings supply defaults; explicit CLI values replace them. Validate effective thresholds after merging. Both CLI and embedder paths resolve settings. |
| #90 | Corrupt batch input prints failure counts but process exits 0. | Continue independent files, retain their successful output, report failed names/counts and exit nonzero if any fail. Process filenames in deterministic order. Empty batch is an explicit no-op; missing/unreadable input directory fails. |
| #91 | same.png and same.jpg both write same.svg; batch reports two successes with one artwork. PNG batch in input directory can overwrite source photos. | Preflight primary/secondary outputs using canonical paths and case-insensitive names. Reject ambiguous destinations or any source overwrite before creating/writing artifacts, with a useful correction. Existing files and input bytes survive. |

Recipe version 2 is additive. Existing nested sidecars remain readable, but
parameters that an older version never saved cannot be recovered retrospectively.
Saved command/project geometry and image-art algorithms are unchanged. CLI
option/strategy identifiers remain compatible; rendering failure and partial
batch failure intentionally return nonzero rather than silently succeeding.
PDF is optional Apache FOP functionality, not bundled in the portable JAR;
use SVG/PNG unless supplying FOP on a custom classpath.

## Verification

- Full clean reactor package: **597 Java tests**, no failures/errors/skips.
- **Nine Python packaging regressions** pass.
- `ExportUtilTest` decodes a PNG with expected geometry and verifies failed
  overwrite preserves original bytes without staging residue.
- `RecipeResolutionTest` covers legacy nested style/flags, effective threshold
  validation after overrides, and real embedder source/strategy/crop resolution.
- `PackagedCliAcceptance.java` launches child JVMs with **only the shaded CLI JAR**
  on their classpath. It verifies real-photo PNG dimensions/nonblank pixels and
  typed command reload, nonzero PDF/unknown-format failures, exact real-photo SVG
  reruns for Squiggle/Needles/Isolines/Sketch/Centerline/PBN, Unicode source paths,
  explicit overrides, mixed batch status/order, valid independent output,
  case-insensitive collisions, existing-output preservation, source preservation,
  valid separate-directory PNG batch, and per-file long-option replacement.
- The current 12-case real-photo corpus and actual Studio Squiggle preset retain
  their exact committed geometry/metrics hashes and production G-code preflight.
  Rendering algorithms/defaults were not altered to resolve these five issues.
- Full mock GUI acceptance passes. Java 17/21 CI runs the packaged checks and
  current corpus; native Windows/Linux release CI runs the checks on the staged
  portable CLI artifact before installed-workflow/upgrade acceptance.

Local logs: `/tmp/gantry-five-issues-build.log`,
`/tmp/gantry-five-packaged.log`, `/tmp/gantry-five-corpus.log`,
`/tmp/gantry-five-studio.log`, `/tmp/gantry-five-gui.log`.
Original reproductions: `/tmp/gantry-cli-audit/`.
These are software/packaging results, not physical quality or firmware evidence.

## Reproduce

```bash
mvn clean package
java -Djava.awt.headless=true -cp cli/target/cli-1.0.0.jar \
  scripts/validation/PackagedCliAcceptance.java \
  cli/target/cli-1.0.0.jar /tmp/gantry-cli-check \
  docs/validation/image-art/inputs/camera.png
```

Use an empty results directory. Logs and `result.json` are written there.
See [the current image-art report](../validation/image-art/squiggle-fix/README.md)
for corpus reproduction. Its qualitative limitations still apply; a valid PNG
or an exact rerun does not promote an image-art mode to a beginner default.
