# Validation Script 03: GUI Settings & Configuration

**Objective:** Verify the Settings dialog correctly manages plotter configuration, station definitions, and persists/loads state.

**Prerequisites:**
- GUI built and launchable (`./run_gui.sh`)

---

## Test Cases

### 3.1 Settings Dialog Layout
| Field | Value |
|---|---|
| **Steps** | Launch GUI. Open Settings (File > Settings or Ctrl+,). |
| **Expected** | Dialog displays three sections: Hardware (backend, model, orientation, backend-specific settings), Coordinate Mapping (Machine Origin dropdown, Swap XY, canvas alignment, rotation, padding), Paint Stations (table with add/edit/remove). |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 3.2 Station CRUD (Add / Edit / Remove)
| Field | Value |
|---|---|
| **Steps** | 1. Add a new station: ID=`TestStation`, X=50, Y=100, Z=30, Behavior=`dip_swirl`. 2. Verify it appears in the table. 3. Select and edit it (change X to 60). 4. Remove it. |
| **Expected** | Station appears in table on Add. Edits update the row. Remove deletes the row. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 3.3 Save Configuration
| Field | Value |
|---|---|
| **Steps** | Modify settings (change speed, add a station). Close Settings dialog. |
| **Expected** | Config file saved automatically. Settings summary strip on Plot tab updates. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 3.4 Load Configuration on Startup
| Field | Value |
|---|---|
| **Steps** | Save a config with distinct values. Close and relaunch the GUI. |
| **Expected** | All saved values (speeds, pen heights, origin, stations) are restored on startup. No manual refresh needed. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 3.5 Manual Jog Controls (Plot Tab)
| Field | Value |
|---|---|
| **Steps** | On the Plot tab, connect to plotter (or mock). Use directional jog buttons in the left panel. |
| **Expected** | Each button sends a movement command in the direction matching the screen label (origin-aware). Console/log confirms direction and distance. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 3.6 Pen Test Buttons
| Field | Value |
|---|---|
| **Steps** | On the Plot tab, click "Pen UP" and "Pen DOWN" buttons. |
| **Expected** | Pen physically raises / lowers (or mock logs it). Button feedback is immediate. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 3.7 Machine Origin Selection
| Field | Value |
|---|---|
| **Steps** | In Settings > Coordinate Mapping, change Machine Origin dropdown between all four options (Top-Left, Top-Right, Bottom-Left, Bottom-Right). |
| **Expected** | Visualization updates origin marker position. Jog buttons adapt directions. Settings summary strip reflects change. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 3.8 Old Config Migration
| Field | Value |
|---|---|
| **Steps** | Place a config.json with `invertX: true, invertY: false` but no `machineOrigin` field. Launch the GUI. |
| **Expected** | Machine Origin dropdown shows "Top-Right" (inferred from invertX=true). Settings function correctly. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |
