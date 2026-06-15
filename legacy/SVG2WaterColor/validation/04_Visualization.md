# Validation Script 04: Live Visualization (Digital Twin)

**Objective:** Verify that the Live View panel accurately represents the physical machine state in real-time, acting as a Digital Twin per Requirements §3, and that interactive positioning works correctly.

**Prerequisites:**
- GUI built and launchable (`./run_gui.sh`)
- Valid JSON command file

---

## Test Cases

### 4.1 Path Preview (Static)
| Field | Value |
|---|---|
| **Steps** | In the Plot tab, select a commands JSON file. Observe the Live View panel before starting a plot. |
| **Expected** | All drawing paths rendered in light gray. Machine bed boundary visible. Content scaled to fit the panel. Dashed bounding box with 8 handles appears around the drawing content. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 4.2 Real-Time Cursor Tracking
| Field | Value |
|---|---|
| **Steps** | Start a mock plot. Observe the Live View panel during execution. |
| **Expected** | Red dot/crosshair moves along the gray paths in sync with console `[MOVE]`/`[DRAW]` logs. Coordinate HUD updates. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 4.3 Portrait Orientation
| Field | Value |
|---|---|
| **Steps** | Observe the Live View panel shape. |
| **Expected** | The on-screen "paper" rectangle appears as Portrait (taller than wide), matching the A3 physical machine. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 4.4 Origin Sync (All Corners)
| Field | Value |
|---|---|
| **Steps** | Set Machine Origin to each of the four options. Observe the origin marker position. |
| **Expected** | Origin marker (0,0) appears at the correct screen corner for each setting: Top-Left → top-left corner, Top-Right → top-right corner, etc. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 4.5 Movement Direction Sync
| Field | Value |
|---|---|
| **Steps** | Watch the cursor as the driver reports increasing X and increasing Y values. |
| **Expected** | Cursor moves away from the origin corner. For Top-Right origin: X increasing → cursor moves Left, Y increasing → cursor moves Down. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 4.6 Data Rotation Preview
| Field | Value |
|---|---|
| **Steps** | Set Data Rotation to 90° in Settings. Load a landscape SVG. Observe the Live View. |
| **Expected** | The bounding box of the drawing content is visually rotated to fit within the portrait paper. The content preview matches the expected physical output. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 4.7 Station Markers
| Field | Value |
|---|---|
| **Steps** | Configure stations with known X/Y positions. Observe the Live View. |
| **Expected** | Station positions rendered as labeled markers on the canvas at the correct coordinates. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 4.8 Interactive Drag-to-Move
| Field | Value |
|---|---|
| **Steps** | Load a commands file. Click on the drawing content and drag it to a new position on the machine bed. |
| **Expected** | Drawing follows the mouse in real-time. Release places it at the new position. Bounding box and handles move with the content. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 4.9 Interactive Handle Resize
| Field | Value |
|---|---|
| **Steps** | Load a commands file. Hover over a corner handle (cursor changes to resize). Drag outward to enlarge, inward to shrink. |
| **Expected** | Drawing scales uniformly around its center. All 8 handles update position. Content maintains proportions. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 4.10 Reset Position
| Field | Value |
|---|---|
| **Steps** | Drag and resize the drawing. Click "Reset Position" button. |
| **Expected** | Drawing returns to its original size and position (overlay transform cleared). |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 4.11 Transform Baking on Plot Start
| Field | Value |
|---|---|
| **Steps** | Drag the drawing to a new position. Start a mock plot. Observe the driver output coordinates. |
| **Expected** | Driver receives coordinates matching the visually repositioned content (overlay baked into temp JSON). The physical plot matches the on-screen preview. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 4.12 Settings Summary Strip
| Field | Value |
|---|---|
| **Steps** | Observe the settings strip below the visualization on the Plot tab. Change settings and observe. |
| **Expected** | Strip shows current backend, origin, machine dimensions, alignment, and orientation at a glance. Updates when settings change. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |
