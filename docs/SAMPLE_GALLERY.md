# Sample gallery

This gallery gives testers a tiny set of known-good inputs before they try their
own artwork. The files are deliberately simple, small, and committed in plain SVG
so they can be used from a release build, a source checkout, or the headless CLI.

Start with the mock backend. A sample being marked suitable for a real pen plot
only means the geometry is intentionally small and simple; it does not prove your
machine size, origin, pen lift, or GRBL setup.

## How to use a sample

1. Open Gantry.
2. Choose **Add artwork** or **File > Open** and select one of the files under
   [`docs/samples/`](samples/).
3. Keep the mock backend selected for the first pass.
4. Confirm the preview fits inside the configured bed.
5. Run **Check before plotting** before any real machine movement.

Recommended baseline settings unless a sample says otherwise:

- Backend: mock for first pass.
- Machine size: any bed at least 150 × 120 mm for these samples.
- Fit/scale: keep the drawing within the center of the virtual bed.
- Feed: conservative default feed rate.
- Real pen: pen raised for jog/frame checks before paper contact.

## Samples

### Simple line drawing

- Source: [`docs/samples/simple-line.svg`](samples/simple-line.svg)
- What it tests: basic SVG import, preview bounds, line/path/circle handling, mock
  plotting, and small G-code export.
- Expected preview size: about 120 × 90 mm.
- Recommended settings: no hatch/fill processing; one black pen/station.
- Mock practice: yes — safest first manual import after guided practice.
- G-code export: yes.
- Real pen plot: yes, after jog direction and frame checks.
- Watercolor / station mapping: no special value; treat as one-colour pen work.
- Expected success: a rectangular frame, simple mountain line, and small sun/circle
  appear centered in preview and produce a short single-colour job.

### Hatch/fill check

- Source: [`docs/samples/hatch-fill.svg`](samples/hatch-fill.svg)
- What it tests: filled regions, hatch/fill processing decisions, preview density,
  and export sanity for generated hatch strokes.
- Expected preview size: about 120 × 90 mm before processing.
- Recommended settings: enable hatching only after first importing the plain SVG;
  start with a visible hatch gap rather than dense fill.
- Mock practice: yes.
- G-code export: yes, but inspect density and estimated time before plotting.
- Real pen plot: cautious yes; use a small size and stop if hatching is too dense.
- Watercolor / station mapping: no; grayscale fills are for hatch behavior, not
  paint-station matching.
- Expected success: filled shapes import as recognizable regions and any generated
  hatching stays inside the simple rectangle/circle/blob shapes.

### Multi-colour / layer check

- Source: [`docs/samples/multi-colour-layers.svg`](samples/multi-colour-layers.svg)
- What it tests: layer visibility, colour preservation, colour-to-station mapping,
  and prompts for multi-pass or multi-colour workflows.
- Expected preview size: about 140 × 100 mm.
- Recommended settings: keep red, blue, and green distinct; for watercolor/station
  mapping, assign only known colours and verify prompts in the mock backend first.
- Mock practice: yes.
- G-code export: yes.
- Real pen plot: yes for pen changes only after proving pause/prompt behavior in
  mock mode.
- Watercolor / station mapping: yes, as a tiny station-mapping fixture.
- Expected success: three coloured layer groups remain visually distinct and can
  be mapped or plotted in a predictable order.

### Text outline check

- Source: [`docs/samples/text-outline.svg`](samples/text-outline.svg)
- What it tests: text-like artwork that has already been converted to paths. It
  avoids the common SVG-font pitfall where raw `<text>` depends on local fonts.
- Expected preview size: about 130 × 70 mm.
- Recommended settings: no font conversion needed; import as normal paths.
- Mock practice: yes.
- G-code export: yes.
- Real pen plot: yes if the frame fits on the bed.
- Watercolor / station mapping: no special value.
- Expected success: blocky `GAN` outline letters, a dashed baseline, and a frame
  appear as strokes with no font warning or missing glyphs.

## Not in this first gallery yet

Image/vectorize samples are intentionally not marked as beginner defaults here.
The roadmap still requires running the existing image-art modes through the
validation spine on a small real-image corpus before recommending them as sample
inputs for external testers.
