# Contributing to SVG2WaterColor

## Development Setup

### Java (Stage 1)
1. Ensure Java 17+ and Maven 3.6+ are installed
2. Build: `mvn clean package`
3. Run GUI: `java -jar target/watercolor-processor-1.0-SNAPSHOT.jar --gui`

### Python (Stage 2)
1. `pip install -r driver/requirements.txt`
2. For AxiDraw: install the official AxiDraw software (includes pyaxidraw API)
3. For G-code: `pip install pyserial`
4. Test with mock: `python driver/driver.py commands.json --mock --verbose`

## Architecture

The system is split into two independent stages connected by a JSON contract:

| Stage | Language | Responsibility |
|---|---|---|
| Preprocessor | Java 17 | SVG parsing, primitive normalization, segmentation, refill insertion |
| Driver | Python 3 | Hardware control via pluggable backends, coordinate transforms, physical plotting |

See [docs/architecture.md](docs/architecture.md) for the full system specification.

### Java Key Classes

| Class | Role |
|---|---|
| `ProcessorService` | Core pipeline: layer identification, normalization, linearization, segmentation, refill insertion. Supports no-refill mode (maxDist=0) for plain drawing. |
| `ProcessorPanel` | Watercolor SVG processing tab with size/position controls and aspect ratio coupling |
| `SvgDrawPanel` | Plain pen plotting tab (no refills). Machine/A5/A4/A3/Custom presets, auto-loads result in Plot tab. |
| `PlotterPanel` | Driver process management, command construction, interactive positioning bake, console output |
| `VisualizationPanel` | Digital twin rendering, origin-aware coordinate mapping, alignment preview, drag-to-move, handle-based resize |
| `SettingsPanel` | Hardware configuration UI, Machine Origin logic, config save/load |
| `SettingsDialog` | Modal wrapper for SettingsPanel (File > Settings or Ctrl+,) |
| `CoordinateTransform` | Java-side coordinate transform utilities matching Python `transforms.py` |
| `GeneralSettings` / `GcodeSettings` | Config POJOs for Jackson serialization |

### Python Modules

| Module | Role |
|---|---|
| `driver.py` | CLI entry point, argument parsing, backend factory, execution loop |
| `transforms.py` | Coordinate transform pipeline (rotate, swap, invert) and alignment offset calculation |
| `backend.py` | `PlotterBackend` abstract base class |
| `axidraw_backend.py` | AxiDraw adapter (pyaxidraw API) |
| `gcode_backend.py` | G-code/GRBL backend (USB via pyserial) |
| `mock_axidraw.py` | Simulation backend for testing |
| `config.py` | Default station and pen configuration |

### Adding a New Backend

1. Create `driver/your_backend.py` implementing `PlotterBackend` from `backend.py`
2. Implement: `connect()`, `disconnect()`, `pen_up()`, `pen_down(height)`, `move_to(x, y)`, `draw_to(x, y)`, `set_speed(drawing, travel)`, `set_pen_heights(up, down)`
3. Add the backend choice to `_create_backend()` in `driver.py`
4. Add a settings card in `SettingsPanel.java` (CardLayout swap on backend selection)
5. Add CLI argument construction in `PlotterPanel.java` (`startProcess()` and `ensureManualServer()`)

## Code Style

- **Java:** Standard conventions, 4-space indent, Java 17 features (records, text blocks)
- **Python:** PEP 8, type hints where practical

## Testing

- **Mock mode:** Both AxiDraw and G-code backends support `--mock` for full simulation without hardware
- **Validation scripts:** See `validation/` directory for manual test procedures
- **Visual verification:** Use the Live View digital twin to verify coordinate mapping before running on hardware

## Commit Messages

Use conventional prefixes: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`
