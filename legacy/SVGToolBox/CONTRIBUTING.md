# Contributing to SVGToolBox

## Development Setup

1. Clone the repository
2. Ensure Java 17+ and Maven 3.8+ are installed
3. Build: `mvn clean package`
4. Run tests: `mvn test`

## Architecture

SVGToolBox uses the **Pipeline Pattern**. Each processing step is a `Processor` implementation that modifies the SVG DOM in-place.

To add a new processor:

1. Create a class in `src/main/java/org/trostheide/svgtoolbox/processors/`
2. Implement the `Processor` interface
3. Add configuration options to `Config.java`
4. Register in the pipeline in `SvgToolboxRunner.java`
5. Add tests in `src/test/java/`

## Code Style

- Java 17 features welcome (records, sealed classes, pattern matching)
- No Lombok — keep it explicit
- Tests use JUnit 5 + Mockito
- Format: standard Java conventions, 4-space indent

## Commit Messages

Use conventional prefixes:
- `feat:` New feature
- `fix:` Bug fix
- `refactor:` Code restructuring
- `docs:` Documentation only
- `test:` Test additions/changes
- `chore:` Build, CI, tooling

## Pull Requests

- One logical change per PR
- Tests must pass (`mvn test`)
- Update CHANGELOG.md under `[Unreleased]`
