# Changelog

All notable changes to SVGToolBox will be documented in this file.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.0.0] — 2026-03-20

### Added
- **GUI Overhaul:** Replaced global settings panel with per-layer controls as the sole configuration. Each layer now has independent pattern, angle, gap, and stroke width settings. Stroke width uses precise JSpinner controls with +/- buttons instead of sliders.
- **GUI Polish:** GridBagLayout for aligned layer rows, column headers, zebra striping, larger color swatches with borders, bold layer names, FlatLaf theming with rounded corners and smooth scrolling.
- **Config.Builder Tests:** New `ConfigBuilderTest` with tests for defaults, per-layer overrides, and null safety.
- GitHub Actions CI pipeline (Maven build + test on Java 17/21)
- CONTRIBUTING.md
- This CHANGELOG
- `Config.Builder` pattern for scalable and fluent configuration management
- Validation scripts for automated testing of CLI and guidelines for manual Swing GUI testing
- **Hatching Enhancements:** Added `empty` and `none` hatch patterns. Added `--hatch-angle` and `--hatch-gap` support per layer.
- **CLI Enhancements:** `--style` override now takes a pattern string (`none`, `empty`, `linear`, `cross`, `zigzag`, `wave`, `dot`) instead of a boolean.
- **GUI Enhancements:** New "Load SVG..." button, dynamic "Layer Overrides" table for per-color hatching, stroke width, and export visibility. Layer names are now displayed natively.
- **Build Script:** Added `build.sh` for easy compilation from source.
- `VisibilityProcessor` and `SvgAnalyzer` to support layer isolation and UI integration.

### Changed
- README.md rewritten for clarity and completeness
- .gitignore expanded (IDE files, build artifacts, logs)
- **Path Parsing:** `PathOptimizeProcessor` now uses robust Apache Batik parsing instead of naive regex, fixing coordinate corruption on complex SVGs.
- **UI Responsiveness:** Swing GUI now processes SVGs asynchronously via `SwingWorker`, preventing the Event Dispatch Thread (EDT) from freezing during heavy processing.

### Removed
- Stale files: `benchmark_stress.log`, `dependency-reduced-pom.xml`, `.idea/` directory

## [1.0-SNAPSHOT] — 2024

### Added
- Pipeline architecture with 8 processors
- CLI interface with full hatching control
- Swing GUI with live preview (`start_gui.sh`, `start_gui.bat`)
- Path optimizer (greedy nearest neighbor for pen travel)
- Multiple hatch patterns: linear, cross, zigzag, wave, dot
- Per-color style overrides (`-S` flag)
- Crop and rotate processors
- Style normalizer (CSS → inline attributes)
- SVG statistics output (`--stats`)
- JUnit 5 tests for core processors
