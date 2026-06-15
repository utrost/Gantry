# Validation Script 02: Python Driver (Mock Mode)

**Objective:** Verify the Python driver correctly reads JSON commands and executes the plotting sequence in mock (simulated) mode.

**Prerequisites:**
- Python 3 installed
- Dependencies installed (`./setup_drivers.sh`)
- Valid JSON command file from Stage 1

---

## Test Cases

### 2.1 Basic Mock Execution
| Field | Value |
|---|---|
| **Steps** | Run: `./run_driver.sh output.json --mock` |
| **Expected** | Driver starts, prints config info, prompts "Press Enter" per layer, logs `[MOVE]`/`[DRAW]` commands, prints "Plot Complete". |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 2.2 Config File Loading
| Field | Value |
|---|---|
| **Steps** | Ensure `config.json` exists in project root. Run: `./run_driver.sh output.json --mock` |
| **Expected** | Console prints `INFO: Loading Configuration from config.json`. Speed, pen heights, and flags match config file values. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 2.3 CLI Argument Override
| Field | Value |
|---|---|
| **Steps** | Run: `./run_driver.sh output.json --mock --speed-down 50 --speed-up 90` |
| **Expected** | Console shows `Setting Speeds -> Draw: 50%, Travel: 90%` regardless of config.json values. CLI takes precedence. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 2.4 Invert / Swap Transforms
| Field | Value |
|---|---|
| **Steps** | Run with `--invert-y --verbose`. Note the `[MOVE]` coordinates in console. Compare against raw JSON `x`/`y` values. |
| **Expected** | Y coordinates are inverted (e.g., `MaxY - original_y`). X coordinates unchanged. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 2.5 Data Rotation
| Field | Value |
|---|---|
| **Steps** | Run with `--data-rotation 90 --verbose`. Compare reported coordinates against unrotated run. |
| **Expected** | Content is rotated 90° CCW. Transformed coordinates differ from unrotated run. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 2.6 Canvas Alignment
| Field | Value |
|---|---|
| **Steps** | Run with `--canvas-align center --verbose`. Check console for offset values. |
| **Expected** | Driver calculates and applies X/Y offsets to center the content on the machine bed. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 2.7 Manual Pen Mode
| Field | Value |
|---|---|
| **Steps** | Run: `python3 driver/driver.py --mock --manual-pen DOWN` |
| **Expected** | Driver connects, lowers pen, prints "Manual operation complete. Exiting." and disconnects. No input file needed. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 2.8 Invalid Input Handling
| Field | Value |
|---|---|
| **Steps** | Create a malformed JSON (e.g., missing `layers`). Run: `./run_driver.sh bad.json --mock` |
| **Expected** | Driver prints `ERROR: Invalid command file: ...` with a descriptive message and exits cleanly without crashing. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |
