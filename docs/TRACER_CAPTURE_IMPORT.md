# Tracer capture import notes

This note records the Tracer JSON capture format as a future Gantry import
candidate. It documents a handoff contract; Gantry does not currently claim this
as an implemented workflow.

## Why this matters

Tracer exports SVG for visual/vector handoff, but SVG is derived geometry. For
plotter performance, Gantry should eventually prefer Tracer's lossless JSON
capture because it preserves the original gesture:

- layer order;
- stroke order;
- raw centerline points;
- normalized pointer pressure;
- timestamps;
- Tracer render/export settings;
- optional reference-image metadata.

This keeps Tracer focused on capture and lets Gantry own physical plotting:
scaling, margins, rotation, plotter origin, axis direction, pressure-to-Z, and
pressure-to-feed calibration.

## File identity

Current Tracer capture files are JSON files named:

```text
<reference-or-trace>.tracer.json
```

The MIME type is `application/json`. Older plain `.json` Tracer files may still
exist and can be considered during migration.

Top-level identity:

```json
{
  "type": "tracer-capture",
  "version": 1
}
```

Backward-compatible legacy types seen in older files:

```json
{ "type": "vhs-trace", "version": 2 }
{ "type": "vhs-trace", "version": 3 }
```

## Coordinate model

Read `canvas` first and fall back to `artboard` for older files or early
downstream experiments.

```json
{
  "canvas": {
    "width": 1000,
    "height": 1400,
    "units": "px",
    "coordinateSystem": "top-left-y-down"
  },
  "artboard": {
    "width": 1000,
    "height": 1400,
    "units": "px",
    "coordinateSystem": "top-left-y-down"
  },
  "coordinateSystem": "top-left-y-down"
}
```

For `top-left-y-down`:

- origin is the top-left corner of the artboard;
- positive X moves right;
- positive Y moves down;
- units are currently pixels.

Gantry should treat these as capture-space coordinates, then apply its own
physical size, page margin, rotation, origin, and plotter-bed mapping.

## Source and reference metadata

Source metadata identifies the producing app and helps future migrations:

```json
{
  "source": {
    "app": "Tracer",
    "appVersion": "0.1.0"
  }
}
```

`image` is either `null` or a reference-image object:

```json
{
  "image": {
    "name": "reference-grid.svg",
    "data": "data:image/svg+xml;base64,..."
  }
}
```

The image belongs to project context. It is not expected to appear in SVG exports
unless a later workflow deliberately adds reference overlays.

## Capture settings

```json
{
  "settings": {
    "stabilizer": 0.35,
    "smooth": true,
    "variable_width": true
  }
}
```

- `stabilizer`: normalized UI value from 0 to 1.
- `smooth`: whether Catmull-Rom smoothing is enabled for display/export.
- `variable_width`: whether pressure is baked into SVG outline geometry.

Raw points remain authoritative. Stabilizer and smoothing are render/export
settings, not mutations of the captured points.

## Layers, strokes, and points

Layers are ordered bottom-to-top. Later array entries draw above earlier entries.
Hidden layers are preserved in JSON and omitted from SVG export by Tracer.

```json
{
  "name": "Ink",
  "color": "#111111",
  "visible": true,
  "opacity": 1,
  "strokes": []
}
```

Each stroke stores its own color, base width, and raw points:

```json
{
  "color": "#111111",
  "width": 5,
  "points": [
    { "x": 150, "y": 520, "p": 0.45, "t": 0 },
    { "x": 260, "y": 360, "p": 0.65, "t": 20 }
  ]
}
```

Point fields:

- `x`: artboard-space X coordinate.
- `y`: artboard-space Y coordinate.
- `p`: normalized pointer pressure from 0 to 1.
- `t`: timestamp in milliseconds from browser capture. Existing files may use
  absolute `Date.now()` values; examples may use relative values.

Gantry should preserve layer and stroke order. If time-aware replay becomes a
feature, normalize each stroke's first `t` to zero during import.

## Pressure summary

`capture.pressure` is diagnostic metadata:

```json
{
  "samples": 327,
  "min": 0.08,
  "max": 0.91,
  "nonDefaultSamples": 284
}
```

The authoritative pressure values are still the per-point `p` values.

## Gantry import guidance

A future Gantry importer should:

1. Require `type: "tracer-capture"`, with optional migration support for legacy
   `type: "vhs-trace"` version 2 or 3.
2. Read `canvas` first, then fall back to `artboard`.
3. Treat `x`, `y`, `p`, and `t` as raw capture data, not SVG geometry.
4. Preserve layer order and stroke order unless the user explicitly optimizes the
   job later.
5. Apply physical size, page margin, rotation, plotter origin, and axis direction
   in Gantry, not in Tracer.
6. Map pressure `p` to Z height, feed rate, or another machine behavior through a
   per-tool/per-plotter calibration curve.
7. Keep SVG export/preview as reference geometry only.

## Roadmap implications

This is not the same class of work as another generic import parser. It would add
a pressure-bearing gesture source that can drive Z-axis or feed-rate expression.
Schedule it only after the current adoption work has real user evidence, or when
a concrete Tracer-to-plotter experiment needs it.

Minimum safe first slice:

- parse `.tracer.json` into an internal capture DTO;
- display/import visible layers as centerline strokes at a chosen physical size;
- preserve stroke order and layer order;
- ignore pressure for motion, but retain it in project provenance;
- round-trip enough metadata through `.gantry` project save/open to avoid data
  loss.

Later slices:

- pressure-to-Z calibration curve;
- pressure-to-feed calibration curve;
- time-aware replay or speed shaping;
- preview overlays for pressure range;
- tests with a real Z-axis tool before any hardware-ready claim.
