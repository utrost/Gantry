# Document, motor and physical-page coordinates

These spaces have different jobs. Do not change executable transforms to repair
an export or advisory image.

- **Document:** imported SVG and quick text use the existing document geometry.
  The SVG importer and stroke alphabet generate Y-up coordinates. Projects retain
  that geometry, placement and explicit transforms.
- **Motor:** `PlotService.forEachDrawSegment` applies the configured origin-derived
  axes, rotation, alignment, Flip Y and bed clamping. These are the exact points
  sent to the backend. `projectArtwork` retains these points without executing them.
- **Physical page:** `VisualizationPanel.physicalToScreen` maps motor points to
  X-right/Y-down page coordinates using the effective axis swap and origin corner.
  `MachinePageProjection` snapshots that same mapping for SVG/ZIP and overlap.
  Swapped axes also swap physical page width/height. Extra inversions, rotation
  and Flip Y are already present in the motor points and must not be applied twice.

SVG keeps the original motor points under the page mapping's SVG matrix. Its
viewBox and millimetre dimensions describe the reviewed physical page. The heatmap
retains the original motor-grid counts and only reorders cells for display. Pen
width, overlap counts, layer order and executable commands are unchanged.
Stored-job export uses stored settings, never the current document's mapping.
The older three-argument ProjectedArtwork constructor retains its canonical
X-right/Y-up contract for direct callers.

## Remaining legacy orientation finding

The current ordinary workspace displays Y-up document text vertically reflected
under the existing origin-derived baseline. Making exported SVG and overlap agree
with that workspace does **not** make the text readable. This remains #93 and
holds public RC12 distribution. Existing calibrated profiles, projects, history,
manual axis settings, G-code and serial execution must not be silently flipped.
A complete fix needs an explicit compatibility strategy and physical direction /
frame / plot acceptance if executable mapping changes.

[RC12 diagnosis](validation/orientation/README.md) retains the original failing
observations. The [page-projection record](release-results/2026-11-20-page-projection.md)
separates the fixed view disagreement from the still-open readability problem.
