# Changelog

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- **Realtime plot cursor** -- The visualization head now tracks the plotter in (near) realtime. For GRBL, the driver runs a single serial reader thread and polls `?` status at ~10 Hz, streaming the pen's actual work position instead of the commanded waypoint (which on buffered controllers ran ahead of the pen). The GUI eases the cursor toward each update on a 60 FPS timer for smooth motion, which also smooths per-waypoint reporting on AxiDraw. Backends opt in via `set_position_callback`; mock/AxiDraw fall back to the previous per-waypoint reporting.
- **Draw SVG mode** -- New tab for plain pen plotting without watercolor refills. Select SVG, configure size/position, click "Convert & Plot" to generate commands and auto-switch to the Plot tab.
- **Interactive positioning** -- Drag-to-move and handle-based resize of drawing content directly on the visualization panel. Dashed bounding box with 8 handles appears around loaded content. Transform is baked into JSON on plot start.
- **Explicit size & position** -- Target width/height spinners with aspect ratio lock and X/Y position spinners for precise placement on the machine bed. Available in both Process SVG and Draw SVG tabs.
- **Robust SVG parsing** -- Falls back to generic XML parser (`DocumentBuilderFactory`) when Batik's strict SVG DOM rejects non-standard elements (e.g., `<plotdata>`).
- **G-code/GRBL backend** -- Full support for GRBL-compatible plotters via USB (pyserial). Three pen control modes: Servo (M280), Z-Axis, and M3/M5 (spindle/solenoid). Configurable serial port, baud rate, feed rates, servo angles, Z positions, and machine dimensions.
- **Backend abstraction** -- `PlotterBackend` ABC in `backend.py` with `AxiDrawBackend`, `GcodeBackend`, and `MockBackend` implementations. Backend factory in `driver.py`.
- **Machine Origin setting** -- Single dropdown to configure plotter home corner (Top-Left, Top-Right, Bottom-Left, Bottom-Right). Replaces confusing manual invertX/invertY checkboxes. Automatically derives correct axis inversions and origin-right flag.
- **`--machine-origin` CLI arg** -- Python driver accepts `--machine-origin {top-left,top-right,bottom-left,bottom-right}` to set origin from command line.
- **G-code settings UI** -- CardLayout panel in Settings tab swaps between AxiDraw and G-code configuration when backend is changed.
- **`GcodeSettings.java`** -- POJO for G-code backend configuration (serial port, baud rate, pen mode, feed rates, servo/Z positions, machine dimensions).
- **`SvgDrawPanel.java`** -- New GUI panel for Draw SVG mode with size presets (Machine/A5/A4/A3/Custom).
- **`CoordinateTransform.java`** -- Java-side coordinate transform utilities for visualization.
- **`SettingsDialog.java`** -- Modal dialog wrapper for settings (File > Settings or Ctrl+,).
- Comprehensive project documentation (README.md, architecture.md, Requirements.md)

### Fixed
- **Cannot plot after jogging (serial port conflict)** -- Jogging starts a persistent manual-control driver that holds the serial port open; the plot process could not then open the same port, which failed the plot and wedged the controller until power-cycled. The manual server is now terminated (releasing the port) before a plot starts, and the manual jog is blocked while a plot is running. The server is killed rather than disconnected so the pen stays where it was jogged instead of returning home.
- **G-code/GRBL plot driving off the bed** -- The driver now sets the pen's current position as the work origin (`G92 X0 Y0`) on connect. Previously `connect()` only cleared the GRBL alarm (`$X`) without homing or setting a work offset, so absolute plot moves (`G0/G1`) went to an undefined coordinate frame and shot the head off the bed. Relative jog (`G91`) was unaffected, which is why jogging worked but plotting did not.
- **Visualization `physicalToScreen()` hardcoded top-right origin** -- Now correctly maps motor coordinates to screen pixels for any origin corner, not just top-right.
- **`--origin-right` always sent to driver** -- Now conditional on actual machine origin, fixing alignment offsets for left-origin plotters.
- **Visualization alignment calculation** -- Origin-aware left/right edge semantics now mirror the Python driver's `calculate_alignment_offset()` for all origin corners.
- **Jog button directions** -- Manual control buttons and arrow keys now adapt to the selected machine origin instead of hardcoding AxiDraw conventions.

### Changed
- GUI modernized with FlatDarkLaf dark theme
- GUI restructured: three tabs (Process SVG, Draw SVG, Plot) plus Settings in a separate dialog (File > Settings or Ctrl+,)
- Settings panel reorganized into Hardware, Coordinate Mapping, and Paint Stations sections
- Manual jog controls moved to the Plot tab left panel
- ProcessorPanel now includes explicit width/height/position spinners with aspect ratio coupling
- Auto-scaling expanded with "Machine" preset that auto-fills machine bed dimensions
- Config format extended with `machineOrigin`, `backend`, and `gcode` fields
- Old config files without `machineOrigin` are automatically migrated from legacy `invertX`/`invertY` flags
- "Load JSON" label renamed to "Commands File" on Plot tab

### Removed
- `invertXCheckBox`, `invertYCheckBox`, `visualMirrorCheckBox` from Settings UI (replaced by Machine Origin dropdown)
- `ProcessingWorker.java` (inlined into ProcessorPanel and SvgDrawPanel)

## [1.0-SNAPSHOT] -- 2025

### Added
- Two-stage pipeline: Java SVG preprocessor -> Python hardware driver
- Paint capacity management with automatic refill insertion
- Multi-layer/multi-color support (Inkscape layers -> paint stations)
- Primitive normalization (rect, circle, ellipse, line, polyline, polygon -> path)
- Curve linearization with configurable step size
- Auto-scaling (fit-to-page A5/A4/A3/XL)
- Swing GUI with Process SVG, Plot, and Settings tabs
- Live visualization (digital twin) with real-time position tracking
- Python driver with pyaxidraw support, mock mode, coordinate transforms
- Canvas alignment (any corner or center), axis inversion, XY swap
- Manual jog controls and pen testing
- Configuration persistence (JSON)
- Validation test scripts
