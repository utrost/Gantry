# Gantry — User Guide

Gantry is an all-Java toolkit for pen plotters (and optional watercolor painting).
It converts SVG files into G-code and streams it to a GRBL-based plotter over serial.

---

## Quick start

### Prerequisites

- Java 17+
- Maven 3.8+ (for building)
- A GRBL-based plotter connected via USB serial

### Build and launch

```bash
./scripts/build.sh        # or build.cmd on Windows
./scripts/start.sh        # or start.cmd on Windows
```

The GUI opens. For a first run, go to **Settings** and configure your machine
before doing anything else.

---

## Workflow

```
SVG file
   └─ Import SVG (optional: run SVGToolBox pre-processing)
        └─ Command model (JSON)
             └─ Optimize (optional: simplify + reorder strokes)
                  └─ Position / scale / rotate / mirror on canvas
                       └─ Start Plot
```

Alternatively, generate the command JSON headlessly with the CLI and load it
into the GUI for positioning and plotting.

---

## Settings

Open with the **Settings** button. Changes take effect after clicking **Save**.

### Connection

| Field | Description |
|---|---|
| Serial port | e.g. `/dev/ttyUSB0` (Linux), `COM3` (Windows) |
| Baud rate | Default 115200 |
| Mock backend | Simulates the plotter without a serial connection — useful for testing |

### Machine geometry

| Field | Description |
|---|---|
| Width / Height | Physical travel range in mm |
| Machine origin | Corner where the machine's (0,0) is: Top-Left, Top-Right, Bottom-Left, Bottom-Right |
| Orientation | Landscape or Portrait |
| Canvas alignment | Where on the bed to place the drawing: None, Top-Left, Top-Right, Bottom-Left, Bottom-Right, Center |
| Data rotation | Rotate the drawing 0/90/180/270° before sending to the machine |
| Padding X / Y | Additional margin in mm added to each side |
| Extra invert X/Y, Swap X/Y, Flip Y | Low-level axis overrides for unusual machine wiring |

### Pen / speed

| Field | Description |
|---|---|
| Pen mode | `servo` — servo angle · `zaxis` — G-code Z axis · `m3m5` — spindle on/off |
| Pen up / down | Servo angle (0–180°) or Z position (mm) for raised / lowered pen |
| Z up / down | Z-axis positions (mm) for pen-up / pen-down when using `zaxis` mode |
| Draw feed rate | Mm/min while drawing (default 1000) |
| Travel feed rate | Mm/min while moving without drawing (default 3000) |

### Refill stations

Used for watercolor painting. Each station has:

| Column | Description |
|---|---|
| Name | Station ID referenced in the command model (`Layer1`, `default_station`, …) |
| X / Y | Station position in mm |
| Z Down | Z dip depth (stored but currently uses global pendown position) |
| Behavior | `simple_dip` — dip and lift · `dip_swirl` — dip + left/right wiggle |

---

## Importing an SVG

Click **Import SVG…** and choose an SVG file. A two-tab dialog opens.

### Import tab

| Field | Description |
|---|---|
| Max draw distance (mm) | Insert a REFILL command every N mm of drawing. Set to 0 for no refill (pure pen plotting). |
| Default station ID | Refill station used for layers that have no explicit station assignment. |
| Curve step (mm) | Bezier curve linearization resolution (default 0.5 mm). |
| Fit to | Scale to fit a paper format: A5 · A4 · A3 · XL · Custom (WxH mm). |
| Custom size | WxH in mm, e.g. `210x297`. Active only when Fit to = Custom. |
| Padding (mm) | Margin inside the target format when using Fit to. |
| Keep aspect ratio | Prevents distortion when fitting to a format. |
| Mirror | Flip the drawing horizontally before importing. |

Inkscape layers (`inkscape:groupmode="layer"`) become separate `Layer1`, `Layer2`, … entries, each mapped to a refill station. SVGs without layers become a single "Default" layer.

### Process SVG tab (optional)

Check **Run SVGToolBox processing** to run the SVGToolBox pre-processing pipeline
on the SVG before importing. The pipeline runs in this order:

> Visibility → StyleNormalizer → Rotate → StrokeWidth → Palette →
> Simplify → Hatch → Linesimplify → Linemerge → Linesort → Reloop →
> Layer → Crop → PathOptimize (if enabled)

| Field | Description |
|---|---|
| Stroke width override | Force all strokes to this width (px). 0 = no change. |
| Palette | Quantize all colours to these hex values, e.g. `#000000,#FF0000`. Uses CIELAB distance. |
| Enable hatching | Fill closed shapes with hatch lines. |
| Hatch pattern | `linear` · `cross` · `zigzag` · `wave` · `dot` · `none` · `empty` |
| Hatch angle | Global hatch angle in degrees (default 45). |
| Hatch gap | Distance between hatch lines (default 5). |
| Hidden layers | Remove shapes with these hex stroke colours. |
| Simplify tolerance | Ramer–Douglas–Peucker tolerance on polygons/paths before hatching (0 = off). |
| Rotate | Rotate the SVG canvas by this many degrees. |
| Crop | Clip to A4 / Letter / Custom (WxH in px). |
| Optimize path order | Greedy nearest-neighbour + 2-opt reorder within each layer. |
| Linesimplify (RDP) | Simplify polylines after hatching (tolerance default 0.378). |
| Linemerge | Merge open paths whose endpoints are within tolerance (default 1.89). |
| Linesort | Reorder paths within each layer to minimise pen travel. |
| Linesort 2-opt | Enable 2-opt improvement pass on linesort (slower, better result). |
| Reloop closed paths | Rotate the start point of closed paths to minimise pen-lift distance. |
| Print statistics | Print element count and total path length to the console after processing. |

---

## Positioning on the canvas

After import, the drawing appears in the visualisation panel.

| Control | Action |
|---|---|
| **Drag** drawing | Move it on the bed |
| **Drag corner handle** | Resize (scale) uniformly |
| **Rotate 90°** | Rotate the drawing 90° clockwise |
| **Mirror** | Flip horizontally |
| **Reset Position** | Return to the canvas-alignment position from Settings |

The visualisation shows:
- Dark background with the machine bed outline
- Orange dot at the machine origin
- Red +X / green +Y axis labels
- Blue dots for refill stations (named)
- Dashed bounding box with resize handles
- HUD: current position, scale, rotation, alignment

---

## Optimising loaded commands

Use the **Optimize Loaded Commands** button to apply path optimisation to
already-loaded commands:

| Control | Description |
|---|---|
| Simplify tolerance | RDP simplification of polylines (0 = off, default 0.2 mm) |
| Reorder strokes | Greedy nearest-neighbour reordering to reduce travel |

---

## Plotting

1. Connect to the plotter with **Connect**.
2. Adjust speed if needed with **Speed +** / **Speed −** / **Reset**.
3. Click **Start Plot**.
4. For each layer, the plotter will pause and wait for you to click **Confirm Layer** before continuing (allows brush/pen changes).
5. Click **Stop** at any time to cancel.

Multipass: set **Passes** (1–10) to draw every stroke N times.

### Jog controls

| Control | Action |
|---|---|
| ▲ ▼ ◄ ► | Jog by the step amount |
| Step spinner | Jog step size (0.1–200 mm) |
| Pen Up / Pen Down | Raise / lower pen manually |

### Raw G-code

Type any G-code command in the text field and press Enter to send it directly.

---

## Exporting and replaying G-code

- **Export G-code** — save the current command model as a `.gcode` file without executing it.
- **Replay G-code** — load and stream a previously exported `.gcode` file to the plotter.

---

## Loading / saving command JSON

- **Load Commands** — load a pre-generated `.json` command model.
- **Save Commands** — save the current command model to `.json` for later.

---

## Headless CLI

```bash
java -jar cli/target/cli-1.0-SNAPSHOT.jar \
  -i drawing.svg \
  -o output.json \
  --fit-to A4 \
  --padding 10 \
  --max-dist 500 \
  --toolbox \
  --linesort \
  --reloop
```

Key flags:

| Flag | Description |
|---|---|
| `-i FILE` | Input SVG (required) |
| `-o FILE` | Output commands JSON (required) |
| `-d MM` | Max draw distance; 0 = no refill |
| `-s ID` | Default station ID |
| `-c MM` | Curve step (default 0.5) |
| `-f FORMAT` | Fit to: A5, A4, A3, XL, or WxH mm |
| `-p MM` | Padding for fit-to |
| `-m` | Mirror horizontally |
| `--toolbox` | Enable SVGToolBox pre-processing |
| `--palette COLORS` | Hex colours, comma-separated |
| `--hatch` | Enable hatching |
| `--hatch-pattern STYLE` | linear / cross / zigzag / wave / dot |
| `--linesort` | Sort paths for minimum travel |
| `--linesort-twoopt` | Add 2-opt pass |
| `--reloop` | Rotate closed-path start points |
| `--optimize` | Greedy path reorder |
| `--toolbox-crop FORMAT` | Crop: A4 / Letter / WxH |
| `--toolbox-stats` | Print statistics |

Run with `--help` for the full list.

---

## Paper formats

| Name | Size (mm) |
|---|---|
| A5 | 148 × 210 |
| A4 | 210 × 297 |
| A3 | 297 × 420 |
| XL | 430 × 600 |
| Custom | any WxH |
