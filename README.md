# Gantry

An all-Java toolkit that prepares SVGs for pen plotters and drives the plotter
directly: optimize → position → process → stream/export G-code.

Gantry merges the SVG-prep features of [SVGToolBox](https://github.com/utrost/SVGToolBox)
with the processing and plotter-driving of SVG2WaterColor into a single
Java 17 / Maven multi-module project. Pen-plotting is the first-class, default
path; watercolor (paint stations + refill) is an optional stage layered on top.

See [ROADMAP.md](ROADMAP.md) for the full design, pipeline, module layout and
phased delivery plan.

See [docs/USER_GUIDE.md](docs/USER_GUIDE.md) for operating instructions and
[docs/TESTING.md](docs/TESTING.md) for the test suite and manual acceptance
checklist.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for a detailed technical
reference of the module graph, data model, pipeline, threading model, and
extension points (written to be consumed by an LLM or a new contributor).

## Modules

| Module | Purpose |
|---|---|
| `model` | Shared DTOs (Point, Layer, Command, ...) and coordinate transforms |
| `svgtoolbox-core` | SVG→SVG processors: path optimize, simplify, hatch, palette, crop, rotate |
| `pipeline-core` | Flatten, position, multipass, command model, output orchestration — pen plotting works end-to-end with just this module |
| `watercolor` | Optional: paint station mapping and refill-split |
| `plotter` | G-code backend (jSerialComm), mock backend, `.gcode` file writer |
| `cli` | Headless entry point |
| `app` | Swing/FlatLaf GUI and orchestration service |

The original projects this was merged from live in their own repositories
([SVGToolBox](https://github.com/utrost/SVGToolBox),
[SVG2WaterColor](https://github.com/utrost/SVG2WaterColor)); they were kept
under `legacy/` as a reference during the port and have since been removed.

## Building

```
mvn clean package
```

## Scripts

The `scripts/` directory has helper scripts for Linux/macOS (`.sh`) and
Windows (`.cmd`), all run from the repo root:

| Script | Purpose |
|---|---|
| `update.sh` / `update.cmd` | `git pull` the current branch |
| `build.sh` / `build.cmd` | `mvn clean install` everything (pass `--skip-tests` to skip tests) |
| `start.sh` / `start.cmd` | Launch the Gantry GUI (`app/target/app-1.0-SNAPSHOT.jar`), building it first if missing |

Requires Java 17+ and Maven 3.8+ on `PATH`.
