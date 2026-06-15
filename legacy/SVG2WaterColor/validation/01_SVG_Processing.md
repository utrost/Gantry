# Validation Script 01: SVG Processing (Stage 1)

**Objective:** Verify that the Java pre-processor correctly parses SVGs, identifies layers, segments paths, and generates valid JSON output.

**Prerequisites:**
- Project built (`./build.sh` or `mvn package`)
- Sample SVG with at least 2 named Inkscape layers

---

## Test Cases

### 1.1 Basic SVG Processing (CLI)
| Field | Value |
|---|---|
| **Steps** | Run: `java -jar target/watercolor-processor.jar -i input.svg -o output.json -d 50` |
| **Expected** | JSON file created with `metadata`, `layers[]`, and `commands[]` per layer |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 1.2 Layer Identification
| Field | Value |
|---|---|
| **Steps** | Open output JSON. Check `layers[].id` and `layers[].stationId` fields. |
| **Expected** | Layers named `Layer1`, `Layer2`, etc. regardless of Inkscape label. Layer display name includes original label in parentheses. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 1.3 Refill Insertion
| Field | Value |
|---|---|
| **Steps** | Set `-d 20` (short max draw distance). Check output JSON for `REFILL` commands within each layer. |
| **Expected** | `REFILL` commands inserted when cumulative draw distance exceeds 20mm. First command of each layer is always a `REFILL`. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 1.4 Fit-to-Format Scaling
| Field | Value |
|---|---|
| **Steps** | Run with `-f A4 -p 10`. Compare `metadata.bounds` in output JSON. |
| **Expected** | Bounds fit within A4 dimensions (210x297mm) minus 10mm padding on each side. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 1.5 Primitive Normalization
| Field | Value |
|---|---|
| **Steps** | Use an SVG containing `<rect>`, `<circle>`, `<ellipse>`, `<line>`, and `<polygon>` elements. Process it. |
| **Expected** | All primitives converted to `DRAW` polyline commands in output JSON. No missing shapes. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 1.6 GUI Processing Tab
| Field | Value |
|---|---|
| **Steps** | Launch GUI (`./run_gui.sh`). Go to "Process SVG" tab. Select input SVG, choose output path, set parameters, click "Process". |
| **Expected** | Progress feedback shown. JSON file generated. Success dialog appears. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 1.7 No-Refill Mode (Draw SVG)
| Field | Value |
|---|---|
| **Steps** | Use "Draw SVG" tab. Select an SVG, click "Convert & Plot". Inspect generated JSON. |
| **Expected** | No `REFILL` commands in the output JSON. All paths converted to MOVE/DRAW commands. Commands file auto-loaded in Plot tab. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 1.8 Explicit Size & Position
| Field | Value |
|---|---|
| **Steps** | In Process SVG tab, set Width=100, Height=100, Pos X=50, Pos Y=50. Process an SVG. Check output bounds. |
| **Expected** | Output `metadata.bounds` shows content scaled to ~100x100mm and offset to start at approximately (50, 50). |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 1.9 Aspect Ratio Lock
| Field | Value |
|---|---|
| **Steps** | Check "Keep Aspect Ratio". Set Width=200. Observe Height field. |
| **Expected** | Height auto-updates to maintain the original aspect ratio of the loaded SVG. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 1.10 Robust SVG Parsing (Non-Standard Elements)
| Field | Value |
|---|---|
| **Steps** | Process an SVG containing non-standard elements (e.g., `<plotdata>`, `<custom:metadata>`). |
| **Expected** | Processing succeeds with fallback parser. Standard drawing elements are correctly extracted and converted. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |
