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
before doing anything else — see **First start** below.

---

## First start

Do this once per machine, before importing or plotting anything. Open
**Settings**, work through the sections top to bottom, then **Save**.

### 1. Connection

- Set **Mock backend** checked if you just want to explore the GUI without
  hardware connected.
- Otherwise pick your **Serial Port** from the dropdown (click **Refresh**
  after plugging the cable in if it's not listed yet) and leave **Baud Rate**
  at 115200 unless your GRBL firmware was built with a different rate.

### 2. Plotter area (Machine geometry → Width / Height)

Measure the plotter's actual travel range in mm — not the paper size, the
**physical travel of the carriage** — and enter it as **Machine Width** /
**Machine Height**. Everything else (canvas alignment, fit-to-page, the bed
outline in the visualisation) is computed from this, so get it right first.

### 3. Machine origin corner

Figure out which corner of the bed the machine considers (0,0) — usually
wherever it sits when first powered on, or wherever the limit switches are.
Set **Machine Origin** to that corner (Top-Left / Top-Right / Bottom-Left /
Bottom-Right). Getting this wrong is the most common cause of the drawing
appearing mirrored or in the wrong corner.

### 4. Orientation

Leave this as **Landscape** unless your bed is physically wider than it is
tall (`Width > Height`) and you want to plot tall/portrait-format drawings
on it without rotating every SVG by hand. It's independent of Width/Height —
see [Troubleshooting](#troubleshooting) if you're unsure whether you need it.

### 5. Pen / Z-axis type

Set **Pen Mode** to match how your plotter physically lifts the pen:

| Pen Mode | Use when... |
|---|---|
| `zaxis` | The pen holder rides on a real Z axis (G-code `G0/G1 Z...`) — most CNC-style plotters. Set **Z Up** / **Z Down** to the clearance and contact heights in mm. |
| `servo` | A hobby servo (e.g. on an Arduino pin via `M280`) lifts the pen. Set **Servo Pin** and **Pen Up** / **Pen Down** servo angles (0–180°). |
| `m3m5` | The pen solenoid/lift is wired to the spindle on/off output (`M3`/`M5`), as on some laser/CNC conversions. **Pen Up** / **Pen Down** are the `M3` S-values used. |

Set **Draw Feed Rate** / **Travel Feed Rate** to sensible starting points
(defaults: 1000 / 3000 mm/min) — tune later once you see real plots.

### 6. Verify jogging before your first real plot

With the plotter connected (or Mock backend), use the **Jog** ▲▼◄► buttons.
Each one should move the pen toward that side of the *bed as you're looking
at it*. If any direction is wrong, **don't** ignore it — fix it now via the
**Extra Invert X/Y** / **Extra Swap X/Y** checkboxes (see
[Troubleshooting](#troubleshooting)), since the same settings also control
where imported drawings land and how the live cursor tracks. If your
controller has limit switches and GRBL homing enabled, use **Home (limit
switches)** to confirm homing works too.

### 7. Canvas alignment

Set **Canvas Alignment** to where on the bed you generally want drawings
placed by default (e.g. "Top Left" with some **Padding**) — this is the
position **Reset Position** returns to after you've dragged a drawing
around.

Once jogging feels right and the bed dimensions/origin are correct, you're
ready to import an SVG.

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
| Serial port | A dropdown of detected ports (click **Refresh** to rescan), or type one manually. On Windows this is `COM3`, `COM4`, etc. (check Device Manager → Ports (COM & LPT) if unsure); on Linux/macOS it's a device path such as `/dev/ttyUSB0` or `/dev/ttyACM0`. The field is editable so a port not yet plugged in can still be typed in. |
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

> Note: a full-page background/border rectangle (the kind Inkscape adds as the page outline) is
> detected and dropped automatically on import, so the pen no longer traces the outer frame before
> drawing the content.

### Import tab

| Field | Description |
|---|---|
| Max draw distance (mm) | Insert a REFILL command every N mm of drawing. Set to 0 for no refill (pure pen plotting). |
| Default station ID | Refill station used for layers that have no explicit station assignment. |
| Curve step (mm) | Bezier curve linearization resolution (default 0.5 mm). |
| Fit to | **Required.** Scale to fit a paper format: A6 · A5 · A4 · A3 · A2 · A1 · XL · Custom (WxH mm). You must choose a size before importing — the dialog starts on "-- Select size --" and will not import until a real format (or Custom) is picked. |
| Custom size | WxH in mm, e.g. `210x297`. Active only when Fit to = Custom. |
| Padding (mm) | Margin inside the target format when using Fit to. |
| Keep aspect ratio | Prevents distortion when fitting to a format. |
| Mirror | Flip the drawing horizontally before importing. |

Inkscape layers (`inkscape:groupmode="layer"`) become separate `Layer1`, `Layer2`, … entries, each mapped to a refill station. SVGs without layers become a single "Default" layer.

The importer flips the Y axis automatically: SVG uses a top-left origin with Y growing downward, while the plotter measures Y upward from the machine origin, so drawings are turned upright on import (they would otherwise appear upside down). The **Flip Y** setting remains available as a manual override for unusual hardware — leave it off for normal use.

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
| **X / Y (mm from origin) + Set** | Place the drawing precisely: the entered values become the position of the drawing's bounding-box corner nearest the machine origin. The fields also update live as you drag. |

Whatever you see in the preview — drag, resize, rotate, mirror or the numeric
position — is exactly what gets plotted and exported. The alignment offset shown
in the live view is carried through to the plotter, so positioning is no longer
overridden by the canvas-alignment re-centering at plot time.

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
2. Adjust speed if needed with **Speed +** / **Speed −** / **Reset** in the Jog section.
3. Click **Start Plot**.
4. For each layer, the plotter will pause and wait for you to click **Confirm Layer** before continuing (allows brush/pen changes). When a layer finishes, the head automatically raises the pen and returns to the origin (0, 0), so it's parked clear for a pen swap while you confirm the next layer.
5. Click **Pause** at any time to halt motion and raise the pen; click **Resume** (same button) to continue from where it left off.
6. Click **Stop** at any time to cancel. This immediately halts the plotter (including any motion already queued on the controller) and raises the pen, rather than just stopping new commands from being sent.

Multipass: set **Passes** (1–10) to draw every stroke N times.

While a plot is running, the jog/pen/edit controls (jog arrows and keyboard/numpad
jogging, Pen Up/Down, Home, position fields, Optimize, Overlay, Load/Import/Save,
Export/Replay and the raw G-code field) are disabled to prevent interfering with the
job. Only **Confirm Layer**, **Pause/Resume**, **Stop** and the **Speed +/−/Reset**
override remain active. The controls re-enable automatically when the plot finishes or
is stopped.

The Live View HUD shows the current feed-rate override as **Speed: N%**, alongside the
head position, so you can see the effect of the Speed buttons at a glance.

### Time estimate

As soon as commands are loaded, imported, or optimized, the Plot section shows
**Est: M:SS** (or **H:MM:SS**) — the predicted total plot duration, computed from each
stroke's draw/travel distance and the configured feed rates (Settings → Pen / speed),
plus a fixed dip time per refill. Hover over it for a per-layer breakdown. The estimate
scales with the realtime feed-rate override (the Speed +/-/Reset controls), so it tracks
whatever speed the machine is actually running at, not just the configured feed rates.

Once a plot starts, the label switches to live tracking: **Elapsed: … / Est: …**, plus
the current layer's own elapsed/estimated time once it starts drawing (layers spent
waiting at **Confirm Layer** don't count against their estimate). It reverts to the
pre-plot estimate when the plot finishes or is stopped.

Note: the live "Elapsed" clock is wall-clock time and keeps running while a plot is
paused, so a long pause will make elapsed time exceed the estimate until resumed.

### Live plot log

While plotting, the **Console** at the bottom of the window streams what's actually
happening:

- A `=== Layer 'L1' (1/3): 42 commands ===` line each time a new layer starts.
- Periodic `Layer 'L1': 100/420 commands (24%)` progress lines as it works through a layer.
- Every G-code line as it's sent to the machine (e.g. `> G1 X10.00 Y20.00 F3000`), prefixed
  with `>` — useful for confirming exactly what's being commanded in real time, or for
  diagnosing a stall.

### Jog controls

| Control | Action |
|---|---|
| ▲ ▼ ◄ ► | Jog by the step amount (large, touch-friendly buttons) |
| Arrow keys / numpad arrows (8/2/4/6) | Same as the ▲▼◄► buttons, usable from anywhere in the window except while a text field is focused |
| Shift + ▲ / Shift + ▼ | Pen Up / Pen Down |
| Step spinner | Jog step size (0.1–200 mm) |
| Pen Up / Pen Down | Raise / lower pen manually |
| Speed − / + / Reset | Decrease, increase, or reset the plotter's feed-rate override while jogging or plotting |
| Home (limit switches) | Runs GRBL's homing cycle (`$H`) against the machine's physical limit switches at 0/0, then zeroes the work origin at that position. Asks for confirmation first, since the plotter will move on its own. Requires GRBL homing to be enabled and configured on the controller (`$22=1` and the related `$23`/`$24`/`$25` settings) — Gantry just triggers the cycle, it doesn't configure GRBL. |

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

All file choosers (Import SVG, Load/Save Commands, Export/Replay G-code) remember the
last folder you opened or saved a file in and reopen there next time, even across restarts.

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

## Troubleshooting

### Jog buttons move the wrong direction (e.g. left moves the pen down)

The bed outline, origin marker and jog directions are all derived from
**Machine Origin** + **Orientation** + the **Extra Invert X/Y** / **Extra
Swap X/Y** checkboxes (Settings → Machine geometry). If your plotter's axis
wiring doesn't match what those settings assume, jogging will feel rotated
or mirrored — e.g. left moves the pen down, down moves it right, and so on.

This is **not a bug**, it's a wiring mismatch — fix it with the checkboxes,
not by changing Machine Origin/Orientation (those should match the
physical bed, not be tweaked to compensate):

1. Set **Machine Origin** to the corner the machine actually treats as
   (0,0), and **Orientation** to match the physical bed (see next section).
2. With the plotter connected (or **Mock backend**), jog ▲. If the pen
   moves toward the wrong bed edge, the X/Y axes are swapped on this
   hardware — check **Extra Swap X/Y** and try again.
3. Jog ◄ and ►. If left/right are reversed, check **Extra Invert X**.
4. Jog ▲ and ▼. If up/down are reversed, check **Extra Invert Y**.
5. Repeat until all four directions move the pen toward the correct edge
   of the bed *as you're looking at it*. Save.

These same flags also control where imported drawings land on the bed and
how the on-screen cursor tracks during jogging, so getting this right here
fixes both at once.

### Cursor / visualization moves on the wrong axis while jogging

If jogging itself moves the pen correctly but the on-screen cursor (or the
bed outline / origin dot / station markers) moves along the wrong axis —
e.g. the cursor moves horizontally when you jog Y — this was a known
rendering bug where the visualization recomputed its own axis
swap/invert instead of using the exact same composited values as the
hardware/jogging code, so the two could disagree whenever an "Extra"
flag and the Machine-Origin-implied invert were combined. This has been
fixed: the visualization now consumes the same effective swap/invert
values used for jogging and G-code, so the cursor always tracks the same
axes as the physical pen. If you still see this on a recent build, it's
worth filing as a bug rather than working around it with settings.

### Connecting on Windows fails or times out (CH340/USB-serial adapters)

- First connect attempt reports a read timeout, and the next attempt
  reports "Failed to open serial port": this was caused by a Windows-only
  serial driver quirk and a leaked port handle after a failed connect, and
  has been fixed. Make sure you're on a current build.
- If it still fails: confirm the port shown in **Serial Port** (click
  **Refresh**) matches the COM port in Windows Device Manager → Ports (COM
  & LPT), and that no other program (e.g. Arduino IDE's Serial Monitor) has
  the port open.

### Home (limit switches) does nothing or errors

`$H` only works if GRBL homing is enabled and configured on the
controller (`$22=1`, plus the matching `$23`/`$24`/`$25` settings) and the
limit switches are wired and triggering correctly. Gantry just sends the
command — it doesn't enable or configure homing on the controller itself.

---

## Paper formats

| Name | Size (mm) |
|---|---|
| A5 | 148 × 210 |
| A4 | 210 × 297 |
| A3 | 297 × 420 |
| XL | 430 × 600 |
| Custom | any WxH |
