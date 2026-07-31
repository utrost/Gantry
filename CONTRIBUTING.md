# Contributing to Gantry

Gantry welcomes practical contributions that make GRBL pen plotting safer,
easier to understand, and more reliable. The project is adoption-led: real user
blockers and reproducible machine workflows outrank speculative features.

## Before opening a PR

1. Read [`README.md`](README.md) and [`docs/USER_GUIDE.md`](docs/USER_GUIDE.md).
2. For architecture changes, read [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
   and [`docs/LESSONS_LEARNED.md`](docs/LESSONS_LEARNED.md).
3. Check [`ROADMAP.md`](ROADMAP.md) and [`docs/ADOPTION_ROADMAP.md`](docs/ADOPTION_ROADMAP.md)
   to see whether the idea fits the current direction.
4. Open an issue first for hardware support, machine-motion behavior, file-format
   changes, or large UI changes.

## Development setup

Requirements:

- Java 17 or newer.
- Maven 3.8 or newer.

Build and test:

```bash
mvn clean package
mvn test
```

Run the GUI from source:

```bash
./scripts/start.sh
```

On Windows use the matching `.cmd` scripts in `scripts\`.

## Pull request expectations

A PR should include:

- a clear problem statement;
- tests for code changes where practical;
- documentation updates for user-visible behavior;
- screenshots or short recordings for major UI changes;
- hardware acceptance notes for real-machine behavior.

For movement-related code, state explicitly how stop/cancel/error handling keeps
or restores the pen-safe state.

## Safety rules

Do not weaken these invariants:

- stopping, cancelling, or failing raises the pen;
- setup and preflight use the same production actions as direct controls;
- start, export, framing, and estimation use the same prepared output;
- slow work stays off the Swing Event Dispatch Thread;
- basic and advanced UI presentations operate on the same document, backend, and
  session state;
- a disconnected preview must never imply that hardware is connected.

## Scope rules

Currently favored work:

- first-run and installation improvements;
- serial/hardware troubleshooting;
- reproducible compatibility evidence;
- beginner-usability fixes from observed blockers;
- tests that prevent known regressions;
- small docs fixes that help another user complete a first plot.

Currently demand-driven or deferred:

- new plotter backends;
- DXF/HPGL import;
- full vector-node editing;
- resume across application restarts;
- broad plugin architecture;
- major visual restyling unrelated to comprehension, feedback, or safety.

## License

By contributing, you agree that your contribution is licensed under Gantry's
AGPL-3.0 license.
