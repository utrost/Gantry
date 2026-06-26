# Gantry — Manual Test Run Record

Copy this file per run (e.g. `test-results-2026-06-26-uwe.md`) and fill it in
while executing the scripts in [`TESTING.md`](TESTING.md). One row per script ID.

- **Result:** `PASS` / `FAIL` / `BLOCKED` / `N/A`
- A script is `PASS` only if **every** checkbox in it is satisfied.
- For `FAIL` / `BLOCKED`, put the failing checkbox and what you saw in **Notes**.

## Run metadata

| Field | Value |
|---|---|
| Tester | |
| Date | |
| Build / commit | `git rev-parse --short HEAD` → |
| Backend | Mock / Hardware (model) |
| OS | |
| `mvn clean install` result | PASS / FAIL |

## Results

| ID | Area | Result | Notes |
|---|---|---|---|
| TS-A1 | Build, launch & layout | | |
| TS-B1 | First-run Setup Wizard offer | | |
| TS-B2 | Setup Wizard happy path | | |
| TS-B3 | Wizard from Settings | | |
| TS-C1 | Settings fields persisted | | |
| TS-C2 | Mock backend toggle | | |
| TS-C3 | Refill-station table | | |
| TS-D1 | Machine menu contents | | |
| TS-D2 | Connect/Disconnect toggle | | |
| TS-D3 | Home from Machine menu | | |
| TS-E1 | Calibrate guard (disconnected) | | |
| TS-E2 | Direction check | | |
| TS-E3 | Scale calibration read/write | | |
| TS-E4 | Direction flip persists | | |
| TS-F1 | Pre-flight guard (nothing loaded) | | |
| TS-F2 | Full checklist flow | | |
| TS-F3 | Frame stays inside bed | | |
| TS-F4 | Start-Plot toggle integration | | |
| TS-G1 | Import gating & page border | | |
| TS-G2 | Basic import & refill | | |
| TS-H1 | Layered import & checklist | | |
| TS-I1 | Hatch patterns | | |
| TS-I2 | Optimisation processors & stats | | |
| TS-J1 | Re-process source SVG | | |
| TS-K1 | Direct manipulation | | |
| TS-K2 | Live View context menu | | |
| TS-L1 | Optimise dialog | | |
| TS-L2 | Stroke welding | | |
| TS-M1 | Jog & pen | | |
| TS-M2 | Raw G-code field | | |
| TS-N1 | Map Colors to Stations | | |
| TS-O1 | Basic plot | | |
| TS-O2 | Refill / confirm-layer pauses | | |
| TS-O3 | Pause / Resume / Stop | | |
| TS-O4 | Multipass | | |
| TS-P1 | Pre-plot estimate | | |
| TS-P2 | Live tracking | | |
| TS-Q1 | G-code export & replay | | |
| TS-Q2 | Save/Open command JSON | | |
| TS-Q3 | File-chooser folder memory | | |
| TS-R1 | User Guide dialog | | |
| TS-R2 | About dialog | | |
| TS-S1 | CLI basic conversion | | |
| TS-S2 | CLI toolbox + stats | | |
| TS-T1 | Add station on canvas | | |
| TS-T2 | Drag station marker | | |
| TS-T3 | Test-run wizard guards | | |
| TS-T4 | Test-run: move/wet/nudge/persist | | |

## Defects raised

| Script ID | Severity | Summary | Tracker link |
|---|---|---|---|
| | | | |
