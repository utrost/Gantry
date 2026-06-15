# Validation Script 05: Hardware Integration (Real Plotter)

**Objective:** Verify end-to-end plotting with real AxiDraw hardware.

**Prerequisites:**
- AxiDraw connected via USB and powered on
- Drivers installed (`./setup_drivers.sh`)
- Mock mode **disabled** in settings

> ⚠️ **CAUTION:** These tests involve physical machine movement. Ensure the plotter area is clear and pen is secured.

---

## Test Cases

### 5.1 Connection & Safety Start
| Field | Value |
|---|---|
| **Steps** | Launch the GUI. Disable Mock Mode. Start a plot. |
| **Expected** | Console shows `INFO: Connection Successful`. Pen raises immediately on connect ("Safely raising pen..."). |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 5.2 Pen Up / Pen Down Heights
| Field | Value |
|---|---|
| **Steps** | Use Settings tab "Test UP" and "Test DOWN" buttons. |
| **Expected** | Pen physically moves to configured heights. Drawing height makes contact with paper. Travel height clears paper. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 5.3 Refill Station Movement
| Field | Value |
|---|---|
| **Steps** | Configure a station at known physical coordinates. Run a plot that triggers a `REFILL`. |
| **Expected** | Plotter head moves to the exact physical station location. Pen dips and swirls (if configured). Head returns to drawing area for next command. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 5.4 Full Plot Execution
| Field | Value |
|---|---|
| **Steps** | Load a multi-layer JSON. Start the plot. Confirm each layer prompt. Let it complete. |
| **Expected** | All layers drawn in sequence. Refills happen at correct intervals. Head returns to home (0,0) on completion. Physical output matches Live View preview. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 5.5 Emergency Stop
| Field | Value |
|---|---|
| **Steps** | During an active plot, click the "Stop" button. |
| **Expected** | Driver process is killed immediately. Plotter stops moving. No crash or hang in GUI. Controls re-enable. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 5.6 Return to Home
| Field | Value |
|---|---|
| **Steps** | After a completed or stopped plot, verify the head position. |
| **Expected** | Head returns to park position (Top-Right, 0,0). |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |
