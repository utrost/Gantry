# Watercolor Plotting Workflow: Architecture & System Specification

## 1. High-Level Architecture

The system transforms a multi-layered vector design into physical watercolor strokes using a pen plotter. The workflow is decoupled into two stages connected by a JSON contract, enabling independent development of the processing and execution layers.

### Data Flow

```
SVG (Inkscape layers) --> Java Processor --> commands.json --> Python Driver --> Physical Plot
```

1. **Design (Input):** `input.svg` (multi-layer SVG).
   - *Convention:* Inkscape layer names correspond to **Station IDs** (e.g., `red_wash`, `blue_detail`).
2. **Stage 1 - Preparation (Java):** Parses the SVG, identifies layers, converts shapes to paths, segments lines based on paint capacity, and inserts refill commands.
   - *Input:* `input.svg` + configuration.
   - *Output:* `commands.json` (multi-layer, sequential instructions).
3. **Stage 2 - Execution (Python):** Reads the command structure and drives the hardware via the selected backend (AxiDraw or G-code/GRBL).
   - *Input:* `commands.json`.
   - *Output:* Physical plot (iterating through layers with pauses for brush changes).

## 2. Stage 1: Java Pre-Processor

**Goal:** Convert vector geometry into a linear sequence of drawing and logistical (refill) commands, grouped by color layer.

### 2.0 Project Specifications

- **Project Name:** Watercolor Processor
- **Package:** `org.trostheide.watercolorprocessor`
- **Runtime:** Java 17 (LTS)
- **Build Tool:** Maven
- **Core Libraries:** Apache Batik 1.17 (SVG), Jackson 2.15 (JSON), Apache Commons CLI, Swing + FlatLaf 3.2 (GUI)

### 2.1 CLI Inputs

| Parameter | Type | Description |
|-----------|------|-------------|
| `-i` | String | Path to the input SVG file |
| `-o` | String | Path where the resulting JSON will be saved |
| `-d` | Double | Maximum distance (mm) the brush can travel before requiring a refill |
| `-s` | String | Fallback Station ID for unnamed layers. Default: `default_station` |
| `-c` | Double | Step size (mm) for linearizing curves. Default: `0.5` |
| `-f` | String | Target paper format (A5, A4, A3, XL). Default: none |
| `-p` | Double | Margin (mm) when using fit-to-format. Default: `10.0` |

```bash
java -jar target/watercolor-processor-1.0-SNAPSHOT.jar -i input.svg -o output.json -f A4 -p 10 -d 150
```

### 2.2 GUI Mode

Start the Swing GUI (FlatDarkLaf dark theme):
```bash
java -jar target/watercolor-processor-1.0-SNAPSHOT.jar --gui
```

The GUI has three tabs plus a settings dialog:

**Process SVG (Tab):** Configure and run the SVG-to-JSON conversion for watercolor plotting. Includes size/position controls (Width/Height with aspect ratio lock, Pos X/Y) and presets (A5/A4/A3/Custom).

**Draw SVG (Tab):** Quick pen-plotting mode for plain drawing without watercolor refills. Select an SVG, configure size (with "Machine" preset that auto-fills machine bed dimensions), and click "Convert & Plot" to generate commands and auto-switch to the Plot tab.

**Plot (Tab):** Control the physical plotting process. Features:
- Commands File field (auto-filled from Process SVG or Draw SVG)
- Live View digital twin showing machine bed, drawing paths, stations, and real-time cursor
- Interactive Positioning -- drag the drawing to reposition, drag corner/edge handles to resize. A dashed bounding box with 8 handles appears around loaded content. The transform is baked into the JSON on plot start.
- Manual Controls (left panel) -- jog buttons, pen up/down, raw G-code input
- Reset Position button to clear drag/resize adjustments

**Settings (File > Settings or Ctrl+,):** Hardware and calibration in a separate dialog. Three sections:
- **Hardware** -- Select backend (AxiDraw or G-code), plotter model/size, orientation. Backend-specific settings swap in via CardLayout (AxiDraw: model, speeds, pen heights; G-code: serial port, baud rate, pen mode, feed rates, servo/Z positions, machine dimensions).
- **Coordinate Mapping** -- Machine Origin dropdown (replaces old invertX/invertY checkboxes), Swap XY toggle, canvas alignment, data rotation, padding.
- **Paint Stations** -- Add/edit/remove refill stations with X/Y positions, pen-down depth, and dip behavior.

### 2.3 Processing Pipeline

1. **SVG Loading:** Tries Batik's `SAXSVGDocumentFactory` first. Falls back to generic `DocumentBuilderFactory` XML parser if the SVG contains non-standard elements (e.g., `<plotdata>`).
2. **Layer Identification:** Scans the SVG DOM for `<g>` elements with `inkscape:groupmode="layer"`. Extracts `inkscape:label` as the Station ID. Falls back to `defaultStationId` if no layers exist.
3. **Primitive Normalization:** Converts `<rect>`, `<circle>`, `<ellipse>`, `<line>`, `<polyline>`, `<polygon>` into standard SVG path definitions.
4. **Size & Position Transform:** If explicit target width/height are specified, calculates a scale+translate transform to place content at the target size and position on the machine bed. Supports aspect ratio locking.
5. **Linearization & Scaling:** Applies global transforms (fit-to-page or explicit target size). Converts all paths into polylines using `PathIterator` with configurable step size.
6. **Segmentation & Refill Insertion:** Tracks cumulative distance per layer. Inserts REFILL at layer start and whenever a segment exceeds remaining paint capacity, calculating precise split points. Skipped entirely when `maxDrawDistance <= 0` (Draw SVG / no-refill mode).
7. **Serialization:** Writes `ProcessorOutput` (metadata + layers) to JSON.

## 3. JSON Command Contract

The strict interface between the Java preprocessor and Python driver.

### 3.1 Root Object

```json
{
  "metadata": {
    "source": "input.svg",
    "units": "mm",
    "totalCommands": 450,
    "bounds": { "minX": 0.0, "maxX": 210.0, "minY": 0.0, "maxY": 297.0 }
  },
  "layers": [
    {
      "id": "red_wash",
      "stationId": "red_wash",
      "commands": [ /* Command Objects */ ]
    }
  ]
}
```

### 3.2 Command Objects

| Command | Fields | Description |
|---------|--------|-------------|
| `MOVE` | `op`, `id`, `x`, `y` | Pen-up travel to absolute position (mm) |
| `DRAW` | `op`, `id`, `points[]` | Pen-down polyline through `{x, y}` points |
| `REFILL` | `op`, `id`, `stationId` | Refill at named paint station |

## 4. Stage 2: Python Driver

**Goal:** Translate abstract commands into hardware movements via the selected plotter backend.

### 4.1 Backend Abstraction

The driver uses a `PlotterBackend` abstract base class (`backend.py`) with three implementations:

| Backend | Module | Communication | Use Case |
|---------|--------|---------------|----------|
| AxiDraw | `axidraw_backend.py` | pyaxidraw API | AxiDraw V3 (A4) and V3 XL (A3) |
| G-code | `gcode_backend.py` | USB serial (pyserial) | GRBL-compatible CNC/plotters |
| Mock | `mock_axidraw.py` | Console output | Testing and preview |

All backends implement:
- `connect()` / `disconnect()`
- `pen_up()` / `pen_down(height)`
- `move_to(x, y)` / `draw_to(x, y)`
- `set_speed(drawing, travel)` / `set_pen_heights(up, down)`

The G-code backend supports three pen control modes:

| Mode | Commands | Use Case |
|------|----------|----------|
| Servo (`M280`) | `M280 P{pin} S{angle}` | RC servo for pen lift |
| Z-Axis | `G0/G1 Z{height}` | Motorized Z-axis |
| M3/M5 | `M3` (down) / `M5` (up) | Spindle relay or solenoid |

### 4.2 Machine Origin & Coordinate System

The plotter's home position (motor 0,0) can be at any of the four corners. This is configured as a single **Machine Origin** setting rather than independent invertX/invertY flags.

| Machine Origin | invertX | invertY | origin_right |
|---------------|---------|---------|-------------|
| Top-Left | false | false | false |
| Top-Right | true | false | true |
| Bottom-Left | false | true | false |
| Bottom-Right | true | true | true |

The origin corner *determines* the correct axis inversions:
- Origin on the **right** side: X is inverted (SVG +X is right, motor +X is left)
- Origin on the **bottom**: Y is inverted (SVG +Y is down, motor +Y is up)
- **Swap XY** is orthogonal (about motor axis wiring, not origin location)

### 4.3 Coordinate Transform Pipeline

Defined in `transforms.py`, the transform chain converts SVG coordinates to motor commands:

```
SVG point → rotate → swap XY → invert X → invert Y → motor command
```

`calculate_alignment_offset()` computes offsets to place content at the desired position on the machine bed, accounting for origin side (left vs right affects which edge is "near" vs "far").

### 4.4 Physical Configuration (config.json)

```json
{
  "general": {
    "backend": "axidraw",
    "machineOrigin": "Top-Right",
    "swapXY": true,
    "modelIndex": 1,
    "speedDown": 25,
    "speedUp": 75,
    "penUp": 60,
    "penDown": 30,
    "orientation": "Portrait",
    "canvasAlignment": "Top Right",
    "viewRotation": 0,
    "paddingX": 0.0,
    "paddingY": 0.0,
    "gcode": {
      "serial_port": "/dev/ttyUSB0",
      "baud_rate": 115200,
      "pen_mode": "servo",
      "servo_pin": 0,
      "feed_rate_draw": 1000,
      "feed_rate_travel": 3000,
      "pen_servo_up": 60,
      "pen_servo_down": 30,
      "z_up": 5.0,
      "z_down": 0.0,
      "machine_width": 300.0,
      "machine_height": 200.0
    }
  },
  "stations": {
    "red_wash": { "x": 5.0, "y": 100.0, "z_down": 30, "behavior": "dip_swirl" },
    "blue_detail": { "x": 30.0, "y": 100.0, "z_down": 30, "behavior": "simple_dip" }
  }
}
```

Old config files without `machineOrigin` are automatically migrated: the origin is inferred from legacy `invertX`/`invertY` flags.

### 4.5 Execution Logic

1. **Load JSON:** Parses the multi-layer command structure.
2. **Safety Check:** Validates `metadata.bounds` against physical limits.
3. **Layer Loop:** Iterates through layers. Pauses between layers for brush/paint changes.
4. **Command Loop:** Executes MOVE, DRAW, REFILL for each layer.
5. **Refill Execution:** Looks up station coordinates, performs dip sequence (move → pen down → wait/swirl → pen up). The return move is handled by the next command in the JSON.
6. **Position Reporting:** With `--report-position`, streams `POS:X:val:Y:val` to stdout for the GUI's live visualization.

### 4.6 Driver CLI Reference

See the full argument table in [README.md](../README.md#driver-cli-reference).

Key arguments:
- `--backend {axidraw,gcode}` -- Select plotter backend
- `--machine-origin {top-left,top-right,bottom-left,bottom-right}` -- Origin corner (derives invert/origin flags)
- `--canvas-align POSITION` -- Align content on machine bed
- `--mock` -- Simulation mode
- `--interactive-server` -- Persistent stdin/stdout server for GUI
- `--config PATH` -- Path to config.json

## 5. Visualization (Digital Twin)

The `VisualizationPanel` provides a real-time preview of the physical plot with interactive positioning.

### 5.1 Rendering Pipeline

1. **Load paths** from `commands.json` (MOVE/DRAW sequences per layer)
2. **Apply overlay transform**: scale and offset from interactive drag/resize (applied in raw content space before driver transforms)
3. **Simulate driver transforms**: rotate → swap → invert, matching `transforms.py` exactly
4. **Calculate alignment offset**: origin-aware, mirrors `calculate_alignment_offset()`
5. **Map physical to screen**: `physicalToScreen()` converts motor coordinates to screen pixels based on the machine origin corner
6. **Render**: machine bed outline, origin marker, axis indicators, drawing paths, bounding box with handles, station markers, real-time cursor

### 5.2 Origin-Aware Screen Mapping

```java
double screenX = isOriginRight() ? (machineWidth - motorX) : motorX;
double screenY = isOriginBottom() ? (machineHeight - motorY) : motorY;
```

The origin marker and axis arrows render in the correct corner. Stations pass through the same mapping.

### 5.3 Interactive Positioning

Users can visually adjust drawing placement on the machine bed:

- **Drag-to-move**: Click and drag anywhere on the drawing content to reposition it
- **Handle-based resize**: 8 handles (4 corners + 4 edge midpoints) on a dashed bounding box allow uniform scaling
- **Screen-to-mm inversion**: Uses a finite-difference Jacobian approach to convert screen pixel deltas to mm deltas, correctly handling any combination of origin/swap/rotation settings
- **Transform baking**: On plot start, the overlay transform (scale around content center + offset) is baked into a temporary copy of the JSON file, rewriting all MOVE/DRAW coordinates
- **Reset**: "Reset Position" button clears all drag/resize adjustments

### 5.4 HUD Overlay

Displays current settings: origin corner, alignment, rotation, swap state, padding, machine dimensions, drawing bounds.

## 6. Project Structure

```
SVG2WaterColor/
├── src/main/java/.../watercolorprocessor/
│   ├── WatercolorProcessor.java        # CLI entry point
│   ├── ProcessorService.java           # SVG parsing, segmentation, refill logic
│   ├── dto/                            # Data transfer objects
│   │   └── command/                    # Command types (MOVE, DRAW, REFILL)
│   └── gui/                            # Swing GUI
│       ├── WatercolorGUI.java          # App launcher (FlatDarkLaf theme)
│       ├── MainFrame.java              # Top-level frame with tabs + status bar
│       ├── ProcessorPanel.java         # Watercolor SVG processing controls
│       ├── SvgDrawPanel.java           # Plain pen plotting (no refills)
│       ├── PlotterPanel.java           # Driver control & live visualization
│       ├── SettingsPanel.java          # Hardware, coordinate mapping, stations
│       ├── SettingsDialog.java         # Modal settings dialog wrapper
│       ├── VisualizationPanel.java     # Digital twin / live view + interactive positioning
│       ├── CoordinateTransform.java    # Java-side coordinate transform utilities
│       ├── GeneralSettings.java        # Settings POJO (config.json serialization)
│       ├── GcodeSettings.java          # G-code backend settings POJO
│       ├── StationConfig.java          # Station definition record
│       └── AppConfig.java              # Root config wrapper
├── driver/                             # Python hardware driver
│   ├── driver.py                       # Main entry point + CLI + backend factory
│   ├── transforms.py                   # Coordinate transformation library
│   ├── backend.py                      # PlotterBackend ABC
│   ├── axidraw_backend.py              # AxiDraw adapter
│   ├── gcode_backend.py                # G-code/GRBL backend
│   ├── mock_axidraw.py                 # Simulation backend
│   ├── config.py                       # Default station + pen config
│   └── requirements.txt                # Python dependencies
├── docs/
│   └── architecture.md                 # This file
├── validation/                         # Manual test procedures
├── pom.xml                             # Maven build config
├── config.json                         # Default runtime configuration
└── README.md                           # Project overview
```
