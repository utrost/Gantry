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

The GUI opens. On a brand-new install (no `config.json` yet) it offers to run
the guided **Setup Wizard** right away — take it, or configure manually via
**Settings** — see **First start** below either way.

---

## First start

Do this once per machine, before importing or plotting anything.

The easiest way is **Machine > Setup Wizard...** (also offered automatically
on first launch, and from a button inside **Settings**): it walks through
Connection, Machine geometry/origin/orientation, and Pen/speed step by step,
using the same fields described below, then saves on **Finish**.

Equivalently, open **Settings** directly and work through the sections top to
bottom, then **Save**:

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

If a commanded jog distance doesn't match what the carriage actually moved
(steps/mm is off), use **Machine > Calibrate Axes...** — see
[Machine menu](#machine-menu) below — rather than fudging Width/Height to
compensate.

---

## Machine menu

| Item | What it does |
|---|---|
| **Connect** / **Disconnect** | Opens or closes the serial connection to the plotter (same as the **Connect** button). |
| **Home** | Runs the homing cycle against the limit switches (`$H`) — same as **Home (limit switches)** in the Jog section. |
| **Pre-Plot Checklist...** | A guided wizard covering connect → home → frame-the-job (traces the current drawing's bounding box on the bed so you can confirm it fits and the pen tracks correctly) → a final physical-checks page (paper taped down, pen loaded, bed clear). Can also be launched from the **Pre-flight...** button next to **Start Plot**, and runs automatically before **Start Plot** when the Settings toggle below is on. |
| **Setup Wizard...** | The guided first-time setup described in [First start](#first-start) — Connection, Machine geometry/origin/orientation, Pen/speed, in order, re-using the exact same fields as **Settings**. Its final step offers **"Continue to axis calibration now"** (ticked by default), so a first run flows straight from settings into calibration. Safe to re-run any time to revisit those settings step by step. |
| **Calibrate Axes...** | A guided pass over machine motion. It **connects first** (a Connect step using your saved settings or mock — no need to connect manually beforehand), then: **Direction:** centre the head, jog each motor axis, and click the arrow for the way the pen *actually* moved — the wizard derives the correct swap/invert in one shot (it catches *swapped* axes, not just reversed ones, and applies the result to the live preview). **Scale:** for X and Y, jog a commanded distance, measure what actually moved with a ruler, and the wizard computes a corrected `$100`/`$101` and writes it on request. **Limit switches:** record whether the machine has them, enable the homing cycle (`$22`), and press each switch by hand to watch it register live (a real wiring test, nothing moves). **Pen lift:** pick the lift type (servo / Z-axis / M3-M5), set the up/down values, and **Test** them — edits apply on the next test with no reconnect. The scale, limit and pen steps are each optional (use **Skip**). |
| **Test Color Stations...** | A guided test run over every configured refill station (watercolor). For each station you can **Move here** (pen-up dry visit so you can eyeball whether the brush lines up with the physical pot), run a **Wet test** (the station's real dip/swirl, so you can check the dip depth and swirl radius clear the pot rim), and **nudge** the position with the **−X/+X/−Y/+Y** buttons if it's off — a nudge moves the head *and* the stored coordinate, and corrected positions are saved when you click **Finish**. Each station step is optional (use **Skip**). Requires a real or mock connection and at least one configured station. See also [placing stations on the canvas](#placing-refill-stations) below. |

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

### The three file formats

Gantry deals with three different kinds of file, and every menu item now names
which one it works on so you always know what you're handling:

| Format | What it is | How Gantry uses it |
|---|---|---|
| **SVG** (`.svg`) | Vector *artwork* — the drawing you start from | **File > Import SVG (artwork)…** reads it and converts it into the command model. This is the only way artwork *enters* Gantry. **Edit > Re-process Source SVG…** re-runs the conversion against the same file. |
| **Commands — JSON** (`.json`) | Gantry's own editable *working format*: layers, moves, draws and refills | **File > Open / Save Commands (JSON)…** loads and saves it; **Edit > Optimize Commands (JSON)…** edits it in place. This is the format you keep working on between sessions. |
| **G-code** (`.gcode`) | Machine instructions for the *plotter* | **File > Export G-code (for plotter)…** writes it; **File > Replay G-code…** streams an existing one straight to the machine. One-way output — G-code can't be reopened for editing. |

Rule of thumb: **SVG comes in, JSON is what you edit, G-code goes out.** Importing
an SVG and opening a JSON command file both land you at the same place (a drawing
ready to position and plot); they just start from a different format. Hovering any
File/Edit menu item shows a tooltip reminding you which format it touches.

---

## Settings

Open via **Settings ▸ Preferences…**. The dialog is organised into tabs —
**Connection**, **Geometry**, **Pen / Speed**, **Stations** — so it stays a
sensible size; pick a tab to edit that group. Changes take effect after clicking
**Save** (OK).

### Connection

| Field | Description |
|---|---|
| Serial port | A dropdown of detected ports (click **Refresh** to rescan), or type one manually. On Windows this is `COM3`, `COM4`, etc. (check Device Manager → Ports (COM & LPT) if unsure); on Linux/macOS it's a device path such as `/dev/ttyUSB0` or `/dev/ttyACM0`. The field is editable so a port not yet plugged in can still be typed in. |
| Baud rate | Default 115200 |
| Mock backend | Simulates the plotter without a serial connection — useful for testing |
| Run Pre-Plot Checklist before Start | When on (default), clicking **Start Plot** opens the **Pre-Plot Checklist** wizard (see [Machine menu](#machine-menu)) instead of plotting immediately. Turn off to skip straight to plotting. |

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
| Pen down delay (ms) | Dwell after lowering the pen before drawing starts (default 80). If your lines begin with a small ink blob/dot — the pen sitting still on the paper while ink bleeds — lower this (try 40, or 0 for a fast pen). Raise it only if a slow pen mechanism skips the first millimetre of a line. |

### Refill stations

Used for watercolor painting. Each station has:

| Column | Description |
|---|---|
| Name | Station ID referenced in the command model (`Layer1`, `default_station`, …) |
| X / Y | Station position in mm |
| Z Down | Per-station dip depth in mm. On `zaxis` machines the pen lowers to this depth at the station (servo/M3 pens fall back to the global pen-down position). |
| Behavior | `simple_dip` — dip and lift · `dip_swirl` — dip + circular swirl · `rinse` — dip + swirl used to clean the brush between colours |
| Color | Hex colour assigned to this station, used by **Map Colors to Stations** to route each drawing colour to its nearest station. |
| Dwell (ms) | How long to pause at the station while dipping. |
| Swirl (mm) | Radius of the circular swirl motion for `dip_swirl` / `rinse` behaviours. |

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
| Curve step (mm) | Bezier curve linearization resolution (default 0.1 mm — smaller = smoother curves, more points). |
| Fit to | **Required.** Scale to fit a paper format: A6 · A5 · A4 · A3 · A2 · A1 · XL · Custom (WxH mm). You must choose a size before importing — the dialog starts on "-- Select size --". The **Import** button stays disabled (and grey) until a real format is picked (or Custom with a valid WxH), then turns **green** to show the import is ready to run. |
| Custom size | WxH in mm, e.g. `210x297`. Active only when Fit to = Custom. |
| Padding (mm) | Margin inside the target format when using Fit to. |
| Keep aspect ratio | Prevents distortion when fitting to a format. |
| Mirror | Flip the drawing horizontally before importing. |

Inkscape layers (`inkscape:groupmode="layer"`) become separate `Layer1`, `Layer2`, … entries, each mapped to a refill station. If a file has no Inkscape layers but groups its content into two or more top-level `<g>` elements (common with non-Inkscape SVG exporters), each such group is also treated as its own layer. SVGs with neither become a single "Default" layer.

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
| Amplitude | Wave/zigzag wave height (0 = auto, derived from gap). Ignored by other patterns. |
| Wavelength | Wave/zigzag wave length (0 = auto, derived from gap). Ignored by other patterns. |
| Dot radius | Dot pattern dot radius (0 = auto, uses the stroke width). Ignored by other patterns. |
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

These fall into three groups: **style** (stroke width, palette, hidden layers),
**fill** (the hatch options), and **geometry/optimization** (simplify, crop,
rotate, and the bottom block: Optimize path order, Linesimplify, Linemerge,
Linesort, Linesort 2-opt, Reloop). The geometry/optimization block here runs at
the SVG-DOM level *during import*; the same kinds of clean-up are also available
*after* import at the command level via **Edit > Optimize Commands (JSON)...**.
Use whichever fits your workflow — there's no need to enable both.

---

## Importing an Image (vectorize)

To bring a **raster image** (PNG/JPG) into Gantry, click **Import Image
(vectorize)…** (File menu, Ctrl+Shift+I). Gantry traces the image into vector
paths, then hands the result to the same SVG import you'd use for hand-authored
artwork — so a photo, scan, sketch or logo can become a plot.

The flow is two dialogs:

1. **Vectorize studio** — a live-preview workspace: the source image on the
   left, the traced **vector preview** on the right, and controls on the right
   edge. The preview re-traces automatically (debounced) as you change anything,
   and a status line shows the strategy and path count — so you tune against what
   you'll actually get rather than committing blind. Start from a **Preset**
   (Line art, Centerline (plotter), Photo, Paint by Numbers, …) and adjust from
   there; only the controls relevant to the selected **Strategy** are enabled.
   **Crop** lets you drag a region on the source to trace just part of the image.
   The status line shows plotter-aware numbers — layers, strokes, points and the
   **pen-up travel %** (lower plots faster) — plus a hint when a trace would plot
   inefficiently (e.g. suggesting *Centerline* for single-stroke work). Click
   **Vectorize** when the preview looks right.

   After importing, **Edit ▸ Re-vectorize Image…** reopens the studio on the same
   image with your parameters restored, so you can re-tune without re-importing.

   Strategy guide:

   | Strategy | Good for | Key parameters |
   |---|---|---|
   | Line art / contours (Douglas–Peucker) | line art, technical drawings | Tolerance, Detail, Auto/manual Canny, colour-aware edges |
   | Straight-line fit · Raw contours · Convex hull | variants of contour tracing | as above |
   | Centerline / single-stroke (skeleton) | plotters/laser — one stroke per shape | Centerline threshold, Tolerance |
   | Bézier outlines (DrPTrace) | smooth filled/outlined shapes | Bézier colours, Bézier detail |
   | Colour fills (ImageTracer) | many-colour photo → SVG | ImageTracer colours, Outline mode |
   | Paint by Numbers | colouring-book style regions + legend | Number of colours |

   Stroke colour/width and Smooth curves apply to the polyline/centerline/bézier
   outputs (the colour-fill tracers carry their own colours).

2. **Import SVG dialog** — the *same* dialog as [Importing an SVG](#importing-an-svg):
   set Fit-to size, refill, curve step, and optional SVGToolBox processing. The
   traced SVG flows through unchanged, so everything downstream (positioning,
   layers, hatching, plotting) behaves exactly as for any imported SVG.

> The generated SVG is the only hand-off between the two halves — vectorization
> is a front stage, not a separate pipeline.

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

### Zooming and panning the view

The Live View has a viewport zoom/pan that is independent of the drawing's
position — it changes only what you *see*, never where the drawing plots:

| Control | Action |
|---|---|
| **Mouse wheel** | Zoom in/out toward the cursor (the point under the pointer stays put) |
| **Middle-button drag** | Pan the view (works anywhere, including in hatch mode) |
| **Shift + left-drag** | Pan the view from anywhere — over the drawing or while hatching, handy when zoomed in with no empty bed to grab |
| **Left-drag on empty canvas** | Pan the view (left-drag *on* the drawing still moves/resizes it) |
| **Double-click empty canvas** | Reset to fit-to-window (100%) |
| **Right-click → Reset View (Zoom/Pan)** | Reset to fit-to-window (100%) |

The current zoom level shows in the HUD as **View: N%**. Loading a new drawing
resets the view to 100%. Zoom is purely a viewing aid: G-code, positioning and
the bed coordinates are unaffected.

**Right-click the Live View** for a context menu with **Add station here** (drops
a new refill station at the clicked bed position — see [Placing refill stations](#placing-refill-stations)),
**Reset View (Zoom/Pan)** (snaps the viewport back to fit-to-window),
**Remove Drawing** (clears the canvas and discards the loaded drawing, so nothing
is left to plot or export) plus the same **Reset Position / Rotate 90° / Mirror**
actions, reachable directly on the canvas. The drawing-related items are greyed out
when no drawing is loaded; **Add station here** and **Reset View** are always available.

### Hatch-filling a region (click to fill)

Outline-style traces (the `bezier` outline mode, `dp`, `centerline`, or
ImageTracer `--b2-outline`) produce closed *outlines* with no fill, so an area
that looks solid in the source plots as just its border. To fill such an area
with hatching:

1. Enable **Edit ▸ Hatch Region (click areas to fill)**. The cursor becomes a
   crosshair, and the region under it is **highlighted** so you can see what a
   click will fill before you commit — both single closed shapes and areas
   *enclosed by several separate strokes* (the latter previews after a brief
   pause). If nothing highlights, that area isn't sealed (look for a gap).
2. Click inside a region. It's filled with hatch strokes added to that region's
   own layer/pen, so they plot together. Tip: zoom in first (mouse wheel) to
   click small regions precisely.
3. Repeat for other regions, then toggle the menu item off when done.

**Quicker: the right-click menu.** Right-click the canvas to toggle **Hatch mode**
(kept in sync with the Edit menu), or — without entering hatch mode at all —
**Hatch area here**, **Clear hatch in this area**, or **Hatch style…** act directly
on the region under the pointer. (The highlight preview only appears in hatch mode.)

**Removing a fill.** **Clear hatch in this area** (right-click) removes the hatch
strokes you added inside that region, leaving the region's outline — the "empty"
state. It only removes fills added this session; a re-import or load-from-disk
treats everything as plain artwork again.

**What counts as a region.** Both a single closed outline *and* an area fenced in
by several **separate** strokes can be filled — clicking inside a shape that's
bounded by independent lines flood-fills the enclosed area (as long as the
boundary actually closes; a gap lets the fill "leak" and nothing is filled).
Nested shapes fill the innermost region under the cursor.

**Different styles per area.** Open **Edit ▸ Hatch Region Style…** to choose the
pattern (linear, cross, zig-zag, wave, dot), spacing (mm) and angle. The current
style applies to each click, so you can change it between clicks — e.g. dense
cross-hatch for the hair, sparse linear for the coat, nothing on the face.

The fill is a normal edit — **Undo** (Ctrl/⌘-Z) reverses the last one, and the
time estimate updates to include the added strokes. Clicking empty space while
in this mode still pans, so you can navigate without leaving hatch mode.

### Editing lines (delete / add)

Light touch-ups to the drawing itself, from the **Edit** menu or the canvas
right-click menu (one mode active at a time — hatch, delete, or add):

- **Delete Line** mode — the line under the cursor highlights **red**; click to
  remove it. Or right-click → **Delete nearest line** for a one-off without
  entering the mode. Good for clearing stray strokes a trace left behind.
- **Add Line** mode — click a **start** point then an **end** point to drop a
  straight line (a green rubber-band previews it). Endpoints **snap** onto nearby
  strokes, so a gap-bridging line connects cleanly. Tip: use this to **bridge a
  gap** in an outline so an area becomes enclosed — then hatch-fill it. The seal
  must actually close (the flood fill leaks through even a small gap, which is
  why snapping matters); if hatch mode doesn't highlight the area afterwards, the
  boundary isn't sealed yet.
- **Move Line** mode — the line under the cursor highlights **cyan**; drag it to
  reposition (it moves with the cursor). Pan still works (middle-drag or
  Shift+drag) so you can navigate mid-edit.
- **Duplicate** — right-click a line → **Duplicate nearest line** drops a copy
  nudged a few mm into the same pen/layer.

All of these are undoable and update the time estimate. For anything heavier than
touch-ups (moving individual points, editing curves), edit the source SVG in a
vector editor and re-import — Gantry's canvas is for quick fixes, not full node
editing.

### Placing refill stations

Refill stations (watercolor) appear as labelled dots on the Live View. You can place
them visually instead of typing raw coordinates into the **Refill Stations** table in
Settings:

- **Drag a station dot** to reposition it — the stored coordinate updates and is saved
  immediately, and the Settings table stays in sync (both edit the one underlying
  station list).
- **Right-click an empty spot → Add station here** drops a new station at that bed
  position. It gets a default name/behaviour and no colour; open **Settings** to set its
  colour and dip behaviour.

Once placed, verify they line up with the physical pots with
**Machine > Test Color Stations...** (above), which drives the head to each one.

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

## Re-processing an imported SVG (Edit > Re-process Source SVG)

Once an SVG is imported you can re-run the SVGToolBox pipeline against the
*original file* — without re-importing from scratch — via **Edit > Re-process
Source SVG...**. This is the "creative tweak" loop: change the hatching, recolour to a
palette, crop or rotate, click **Apply**, and see the result.

This dialog exposes **exactly the same option set** as the import dialog's
"Process SVG" tab (see the table above) — stroke width, palette, hidden layers,
the full hatch options, simplify tolerance, rotate, crop, and the optimize block
(Optimize path order, Linesimplify, Linemerge, Linesort, Linesort 2-opt,
Reloop). The two share one underlying control panel, so they can never drift
apart. Field values are remembered for the session **and carried across both
dialogs**, so reopening either one to adjust a single value doesn't reset the
rest.

> Note: the optimize block here runs at the SVG-DOM level. The same kinds of
> geometry clean-up are also available *after* import, at the command level, via
> **Edit > Optimize Commands (JSON)...** (simplify, reorder, merge) — that one
> works on the loaded drawing regardless of how it was imported. Use whichever
> fits your workflow; there's no need to run both.

---

## Optimising the command model (Edit > Optimize Commands (JSON))

Use **Edit > Optimize Commands (JSON)...** to apply path optimisation to the
current drawing (the command model — works the same whether it came from an SVG
import or an opened `.json` file). A small dialog prompts for:

| Control | Description |
|---|---|
| Simplify tolerance | RDP simplification of polylines (0 = off, default 0.2 mm) |
| Reorder strokes | Greedy nearest-neighbour reordering to reduce travel |
| Merge | Weld strokes that touch end-to-end into one continuous line (0 = off, default 0.2 mm). Many SVGs (especially from non-Inkscape generators) split a single visible line into lots of short segments; without merging, the pen lifts and re-lowers between every one — wasted motion, and an ink dot at each segment's start. Merging draws them as one smooth stroke. The console reports `strokes X -> Y` so you can see how many were welded. |

> Tip: Merge pairs with the **Pen down delay** setting — fewer pen-downs means
> fewer start-of-line ink dots. Merge removes most of them structurally; lower
> the pen-down delay to clean up whatever remains.

---

## Watercolor painting (optional)

For watercolor work, configure each paint pot as a refill station in
**Settings → Refill stations**, giving each one a **Color** (the paint it holds),
a dip **Behavior** (`simple_dip`, `dip_swirl`, or `rinse`), a **Z Down** dip depth,
a **Dwell** time and a **Swirl** radius (see [Refill stations](#refill-stations)).

Then, with a colour drawing loaded, use **Map Colors to Stations**: each drawing
colour is routed to the station whose configured **Color** is closest to it (by
perceptual RGB distance), so the brush picks up the right paint per layer. The
station markers in the Live View are tinted with their assigned colours, and the
mapping is logged to the Console. Stations set to `rinse` are used to clean the
brush between colour changes.

A station's dip depth (**Z Down**) drives a real Z move on `zaxis` machines, so a
pot of paint can sit lower than the paper; servo / `m3m5` pens fall back to the
global pen-down position.

---

## Plotting

1. Connect to the plotter with **Connect**.
2. Adjust speed if needed with **Speed +** / **Speed −** / **Reset** in the Jog section.
3. Click **Start Plot** (or the **Pre-flight...** button to run the checklist on demand without
   starting). By default **Start Plot** opens the **Pre-Plot Checklist** wizard first — see
   [Machine menu](#machine-menu); disable that in Settings to skip straight to plotting.
4. For each layer, the plotter will pause and wait for you to click **Confirm Layer** before continuing (allows brush/pen changes). When a layer finishes, the head automatically raises the pen and returns to the origin (0, 0), so it's parked clear for a pen swap while you confirm the next layer.
5. Click **Pause** at any time to halt motion and raise the pen; click **Resume** (same button) to continue from where it left off.
6. Click **Stop** at any time to cancel. This immediately halts the plotter (including any motion already queued on the controller) and raises the pen, rather than just stopping new commands from being sent.

Multipass: set **Passes** (1–10) to draw every stroke N times.

### Choosing which layers to show and plot (per-pen workflow)

The **Layers** checklist in the Plot section lets you pick any subset of layers to
show and plot instead of the whole drawing — useful when each layer is a different
pen or ink colour:

1. The checklist shows one tickbox per layer (labelled with the layer's id and source
   colour, and tinted with that layer's preview colour so the list doubles as a
   legend). All layers start ticked.
2. **Tick the layers you want, untick the rest.** Ticked layers are drawn in full
   colour in the Live View; unticked layers are **ghosted** (drawn dimmed) so you can
   still see where the visible layers sit relative to the whole piece and confirm
   you've loaded the right pen. Use **All** / **None** to toggle everything at once.
3. The time estimate updates to reflect just the ticked layers.
4. Click **Start Plot** — only the ticked layers are plotted (and exported, if you use
   **Export G-code**). The drawing's position on the bed is unchanged, so every layer
   lands in register with the others.
5. When it finishes, swap the pen, change the ticked layers, and plot again.

Leave every layer ticked (the default) to plot the whole drawing in one job; you'll
still be prompted to **Confirm Layer** between layers. With no layers ticked, Start
Plot / Export do nothing and report that no layers are selected.

**Colour layers:** with the **Colour layers** checkbox ticked (default), each layer
is drawn in its own colour — taken from the layer's source colour, brightened if
needed so dark colours (e.g. black line art) stay visible on the dark canvas, and
falling back to a distinct hue when a layer has no colour or two layers would look
alike. This makes it easy to tell layers/pens apart at a glance. Untick it to draw
every layer in one uniform colour. Unticked (ghosted) layers are shown as dimmed
versions of their own colours.

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
| ▲ ▼ ◄ ► (tap) | Jog by the **Step (mm)** amount — one move per tap (large, touch-friendly buttons) |
| ▲ ▼ ◄ ► (press and hold) | Jog **continuously** in that direction until you release the button |
| Arrow keys / numpad arrows (8/2/4/6) | Same as a tap of the ▲▼◄► buttons, usable from anywhere in the window except while a text field is focused |
| Shift + ▲ / Shift + ▼ | Pen Up / Pen Down |
| Step (mm) spinner | Distance per jog tap (0.1–1000 mm) |
| Pen Up / Pen Down | Raise / lower pen manually |
| Speed − / + / Reset | Decrease, increase, or reset the plotter's feed-rate override while jogging or plotting |
| Home (limit switches) | Runs GRBL's homing cycle (`$H`) against the machine's physical limit switches at 0/0, then zeroes the work origin at that position. Asks for confirmation first, since the plotter will move on its own. Requires GRBL homing to be enabled and configured on the controller (`$22=1` and the related `$23`/`$24`/`$25` settings) — Gantry just triggers the cycle, it doesn't configure GRBL. |

**Soft limits.** With **Soft limits** enabled (Settings → Geometry; on by
default), jog moves — including press-and-hold continuous jogging — are clamped
so the commanded position can't leave the bed: it stops at 0/0 on one side and at
the configured Machine Width / Height on the other, so you can't over-travel an
axis. The clamp uses the same orientation geometry as the on-screen cursor, so
the soft stops stay on the correct physical edges even when X/Y are inverted or
swapped or the machine origin is a different corner. The limits track the
position from the last **Home** (and from live position reports), so home first
for them to be accurate. Disable the checkbox to jog without clamping.

### Raw G-code

Type any G-code command in the text field and press Enter to send it directly.

---

## Exporting and replaying G-code

- **Export G-code (for plotter)** — save the current command model as a `.gcode` file without executing it.
- **Replay G-code** — load and stream an existing `.gcode` file straight to the plotter (bypasses the command model).

---

## Opening / saving the command model (JSON)

- **Open Commands (JSON)** — open a previously saved `.json` command model and pick up editing where you left off.
- **Save Commands (JSON)** — save the current command model to `.json` for later. This is Gantry's working format, *not* G-code.

All file choosers (Import SVG, Open/Save Commands, Export/Replay G-code) remember the
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
| `-c MM` | Curve step (default 0.1) |
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

### Vectorizing an image headlessly

`VectorizeCli` traces a raster image to SVG and can chain straight into the SVG
import above. Arguments are split on a literal `--`: everything before it goes to
the vectorizer (image → SVG), everything after it to `SvgImportCli` (SVG →
commands), with the produced SVG injected as the import's `-i`.

```bash
# image -> SVG only
java -cp cli/target/cli-1.0-SNAPSHOT.jar org.trostheide.gantry.cli.VectorizeCli \
  -i photo.jpg -o photo.svg -s dp --canny-auto

# image -> SVG -> command JSON in one command (fit to A4)
java -cp cli/target/cli-1.0-SNAPSHOT.jar org.trostheide.gantry.cli.VectorizeCli \
  -i photo.jpg -o photo.svg -s centerline -- -o photo.json --fit-to A4
```

The vectorize options (`-s` strategy and its parameters) mirror the standalone
vectorizer; run `VectorizeCli` with no arguments for usage, and see the
`vectorize` module for the full option list.

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
physical bed, not be tweaked to compensate). The Machine Origin corner sets a
baseline axis direction and **Extra Invert X/Y toggle (correct) it** — so they
can un-invert an axis the origin already inverted, not only add inversion. With
**Extra Swap X/Y** on, screen up/down drives one machine axis and left/right the
other, so the Extra Invert that fixes a reversed direction may be the opposite
one — just toggle whichever flips the direction you see:

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
