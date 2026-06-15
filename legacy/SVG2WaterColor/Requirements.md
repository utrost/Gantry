# Watercolor Processor - System Requirements

This document outlines the core requirements and physical constraints of the Watercolor Processor system.

## 1. Hardware Constraints

These properties describe the physical machine.

- **Supported Backends**: AxiDraw (pyaxidraw) and G-code/GRBL (serial).
- **Machine Size**: Configurable per backend. AxiDraw: A4 (model 1) or A3/XL (model 2). G-code: arbitrary dimensions via `machine_width`/`machine_height`.
- **Physical Orientation**: Portrait (vertical) or Landscape, depending on plotter setup.
- **Machine Origin (Home)**: Configurable. The motor (0,0) can be at any of the four corners: Top-Left, Top-Right, Bottom-Left, or Bottom-Right.
- **Axis Directions**: Determined by the machine origin:
  - Origin on the **right**: motor +X moves left (away from origin).
  - Origin on the **left**: motor +X moves right (away from origin).
  - Origin on the **top**: motor +Y moves down (away from origin).
  - Origin on the **bottom**: motor +Y moves up (away from origin).
- **Swap XY**: Orthogonal toggle for plotters whose motor axes are physically rotated 90 degrees relative to the bed.

## 2. Input Data

- **Formats**: JSON command files derived from SVG by the Java preprocessor.
- **Input Coordinates**: Standard canvas convention (origin top-left, X right, Y down).
- **Variability**: Input files may be any size (A4, A3, etc.) and any orientation.

## 3. Visualization Requirements

The GUI Live View must act as a **Digital Twin** of the physical machine.

- **Origin Sync**: The visual (0,0) marker must appear at the correct screen corner matching the configured machine origin.
- **Axis Indicators**: Arrows showing +X and +Y motor directions, rendered from the origin corner.
- **Movement Sync**: When the driver reports position changes, the visual cursor must move in the physically correct direction for the configured origin.
- **WYSIWYG**: The drawing shown on screen must match what will be drawn on paper, including alignment, rotation, and axis inversions.
- **Alignment Preview**: Canvas alignment (top-left, top-right, bottom-left, bottom-right, center) must be accurately previewed with padding offsets.

## 4. Transformation Logic

To map input data to hardware, the system applies transforms in this order:

1. **Data Rotation**: Rotate drawing content (0/90/180/270 degrees) to preview how content maps onto the machine bed.
2. **Swap XY**: Optionally treat logical X as physical Y (and vice versa) for rotated mounting.
3. **Invert X**: Flip X axis when origin is on the right side.
4. **Invert Y**: Flip Y axis when origin is on the bottom.
5. **Alignment Offset**: Snap content to a corner or center of the machine bed with padding.

The **Machine Origin** setting drives steps 3-4 automatically. Users select a corner; the system derives the correct inversions.

## 5. Coordinate Mapping Model

The Machine Origin replaces the old manual invertX/invertY checkboxes with a single intuitive control:

| Machine Origin | invertX | invertY | origin_right |
|---------------|---------|---------|-------------|
| Top-Left | false | false | false |
| Top-Right | true | false | true |
| Bottom-Left | false | true | false |
| Bottom-Right | true | true | true |

This mapping is consistent across the Java GUI (SettingsPanel, VisualizationPanel) and the Python driver (transforms.py).

## 6. Draw SVG Mode (No-Refill)

- **Purpose**: Plain pen plotting without watercolor refill logic.
- **Processing**: When `maxDrawDistance <= 0`, the processor skips all refill insertion (no initial REFILL, no mid-stroke splits).
- **UI**: Dedicated "Draw SVG" tab with SVG selection, size/position controls, and "Convert & Plot" button.
- **Auto-Load**: Generated commands file is automatically loaded in the Plot tab and the UI switches to it.

## 7. Explicit Size & Position

- **Target Dimensions**: Width and height in mm. When both are specified, the drawing is scaled to fit within these dimensions.
- **Aspect Ratio Lock**: Checkbox that couples width and height changes. Changing one dimension auto-computes the other.
- **Position**: X and Y coordinates in mm for placing the drawing on the machine bed.
- **Presets**: A5 (148x210), A4 (210x297), A3 (297x420), Machine (auto-fills machine bed dimensions), Custom.
- **Available In**: Both Process SVG and Draw SVG tabs.

## 8. Interactive Positioning (Visualization)

- **Drag-to-Move**: Click and drag anywhere on the drawing content to reposition it on the machine bed.
- **Handle-based Resize**: 8 handles (4 corners + 4 edge midpoints) on a dashed bounding box allow uniform scaling.
- **Overlay Transform**: Applied in raw content space (before driver transform pipeline) for instant visual feedback without reprocessing.
- **Screen-to-mm Inversion**: Uses finite-difference Jacobian approach to correctly convert screen pixel deltas to mm deltas for any origin/swap/rotation configuration.
- **Transform Baking**: On plot start, overlay transform is baked into a temporary JSON copy by rewriting all MOVE/DRAW coordinates.
- **Reset**: "Reset Position" button clears all drag/resize adjustments.
- **WYSIWYG**: The visual position after drag/resize accurately represents where the plotter will draw.

## 9. Robust SVG Parsing

- **Primary Parser**: Apache Batik `SAXSVGDocumentFactory` for full SVG DOM support.
- **Fallback Parser**: Generic `DocumentBuilderFactory` (namespace-aware) when Batik rejects non-standard elements.
- **Trigger**: `DOMException` during initial SVG load triggers the fallback.
- **Result**: SVGs with non-standard elements (e.g., `<plotdata>`, custom metadata) are processed successfully.

## 10. Operational Requirements

- **Persistence**: All settings (backend, origin, alignment, rotation, speeds, pen heights, stations, G-code parameters) are saved to `config.json` and restored on startup.
- **Config Migration**: Old config files without `machineOrigin` are automatically migrated by inferring the origin from legacy `invertX`/`invertY` flags.
- **Startup State**: The application launches in a ready state with the visualization correctly representing the saved configuration.
- **Multi-Backend**: Switching between AxiDraw and G-code backends swaps the relevant settings panels and driver arguments without requiring restart.
