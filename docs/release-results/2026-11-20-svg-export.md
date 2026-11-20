# Reviewed SVG export acceptance — 2026-11-20

Issue: [#68](https://github.com/utrost/Gantry/issues/68).
Baseline: `e34d724`; change branch: `fix/svg-export-acceptance`.
This covers software on main after RC11; published RC11 assets are unchanged.

## Result

Combined export reports `1 SVG file exported: <absolute destination>`.
ZIP export reports `<N> SVG files + README exported in package: <absolute destination>`:
N is one combined SVG plus one SVG per included layer. The same result is logged
and displayed in local feedback, whose Details action exposes long paths.
README is counted separately from SVGs.

Included empty layers intentionally retain their empty group/file, name and
position in reviewed order. Unselected layers contribute neither geometry nor
files. An empty selection, or a selection with no drawing points, is blocked
before the chooser; cancelling the chooser writes nothing. SVG contains paths
only, without machine pauses, refill motions, drying waits or firmware commands.
The package README retains that limitation.

## Verification

- Full `mvn clean package`: **590 Java tests**, no failures/errors/skips.
- `python3 -m unittest discover -s scripts -p 'test_*.py'`: **9 passed**.
- Full `scripts/test-gui.sh` on Xvfb, isolated mock profiles, no hardware:
  actual SVG/ZIP menu exports assert the destination and logical SVG counts;
  empty selection is rejected and chooser cancellation leaves no export.
- `SvgJobExporterTest` exercises preparation of a selected empty layer and a
  drawing layer, an excluded drawing layer, document order, two passes,
  deterministic ZIP names/count, pen metadata, and empty/empty-only selections.
- Failed SVG and ZIP overwrites preserve existing destination bytes and leave
  no staged files. Existing absent-destination failure, transformed/clamped
  geometry, historical pen snapshot and SVG round-trip tests remain passing.

Local logs: `/tmp/gantry-svg-build.log`, `/tmp/gantry-svg-python.log`,
`/tmp/gantry-svg-gui.log`; GUI evidence: `/tmp/gantry-svg-gui`.
CI on the linked pull request additionally checks Java 17/21, virtual workspace
layouts and Windows/Linux installer/upgrade behavior before merge.

No physical plotter or external-tool/user acceptance is claimed. The optional
physical-pen grouping extension is outside #68's remaining scope.
