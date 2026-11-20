# Reviewed physical-page projection — 2026-11-20

Scope: the independently fixable SVG/ZIP and overlap **view disagreement** in #93.
Default workspace text readability remains unresolved. RC12 stays an unchanged,
unpublished draft at `8dcb772`; this development fix does not replace its assets.

## Fix

Previously SVG and overlap treated final motor coordinates as an unconditional
X-right/Y-up page. The workspace instead applies the configured origin and
effective axis swap. SVGs could therefore be reflected, differently positioned or
have the wrong portrait page dimensions relative to the reviewed workspace.

`MachinePageProjection` snapshots the workspace's physical mapping. SVG keeps
exact motor paths under its affine matrix and uses the physical page dimensions.
The actual overlap renderer reorders motor-grid cells with that same mapping;
coverage calculations and per-pen counts are unchanged. Existing history already
stores the required effective axes/origin, so no configuration or history-schema
migration is needed. [Coordinate conventions](../COORDINATE_CONVENTIONS.md).

## Verification

- Focused exporter/coverage and actual rendering regressions: PASS.
- `PageProjectionTest` covers 256 combinations of four origins, portrait /
  landscape, all extra swap/inversion flags, Flip Y and 0/90° rotations.
- Batik rasterizes the actual exported SVG. Sampled asymmetric F strokes must
  match `VisualizationPanel.physicalToScreen`; the actual dialog renderer's
  occupied cells must agree with SVG ink in both directions.
- All 256 G-code outputs match normalized SHA-256 baselines captured from the
  unmodified tagged RC12 JAR before building the fix. CRLF is normalized to LF;
  every command, coordinate, feed, pen operation and order remains in the hash.
  Fixture: `app/src/test/resources/page-projection-rc12-gcode.sha256`;
  generator: `PageProjectionCases.main` compiled/run against the original RC12 JAR.
- Historical settings reload and immutable export-page snapshots are covered.
- Full local clean reactor: 602 Java tests, zero failures/errors/skips. Nine
  Python packaging tests and the full real-Swing mock workflow passed. Java
  17/21 and Windows/Linux installer/upgrade CI run on the implementation PR.
- [Actual workspace, SVG raster and overlap captures](../validation/orientation/page-projection-fix/README.md)
  were inspected; they agree while retaining the known upside-down label.

## Additional verified export defect — #96

The unmodified RC12 exporter emits XML that an independent JDK parser rejects
when a layer or pen name contains U+0001. Valid project/command JSON can hold that
name. Writing the invalid SVG previously reported success and could replace a
valid destination. Unpaired surrogates could also be replaced during UTF-8 writing;
literal attribute whitespace was normalized on reload.

The exporter now validates XML 1.0 code points, rejects illegal control characters
and unpaired surrogates with a rename/retry message, and preserves valid Unicode
and attribute whitespace. Actual combined and ZIP SVGs are independently parsed
for exact name recovery, including supplementary Unicode, metacharacters and
Tab/LF/CR. Invalid layer and pen names in both formats preserve the previous file
and leave no staging files. Geometry and executable output are unaffected.

## Limits

This is a display/export correction. It preserves the workspace's current
appearance, including its known default text reflection. It does not fix that
legacy baseline, change calibration, establish firmware equivalence, or collect
physical/native-desktop/participant evidence. #93 remains open.
