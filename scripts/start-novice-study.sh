#!/usr/bin/env bash
# Launch Gantry from an isolated clean working directory for novice-study runs.
# Gantry stores config.json, recovery files, and plot-history.json relative to
# the process working directory, so this script keeps each participant profile
# separate from the developer checkout and from prior participants.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/app/target/app-1.0.0.jar"
DEFAULT_PROFILE_ROOT="$ROOT/.novice-study-profiles"
PROFILE_DIR=""
RESET=false
DRY_RUN=false

usage() {
    cat <<'USAGE'
Usage: scripts/start-novice-study.sh [--profile DIR] [--reset] [--dry-run]

Launch Gantry from a clean participant profile for the novice usability study.
The profile directory becomes Gantry's working directory, isolating config.json,
plot-history.json, recovery files, projects, and file-chooser history.

Options:
  --profile DIR  Profile directory to use. Defaults to
                 .novice-study-profiles/YYYYMMDD-HHMMSS
  --reset        Delete an existing profile directory before launch.
  --dry-run      Print what would happen without building or launching Gantry.
  -h, --help     Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --profile)
            if [[ $# -lt 2 ]]; then
                echo "ERROR: --profile requires a directory." >&2
                exit 2
            fi
            PROFILE_DIR="$2"
            shift 2
            ;;
        --reset)
            RESET=true
            shift
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "ERROR: Unknown option: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

if [[ -z "$PROFILE_DIR" ]]; then
    PROFILE_DIR="$DEFAULT_PROFILE_ROOT/$(date +%Y%m%d-%H%M%S)"
fi

case "$PROFILE_DIR" in
    /*) ;;
    *) PROFILE_DIR="$ROOT/$PROFILE_DIR" ;;
esac

if [[ "$RESET" == true && -e "$PROFILE_DIR" ]]; then
    if [[ "$PROFILE_DIR" == "$ROOT" || "$PROFILE_DIR" == "/" ]]; then
        echo "ERROR: Refusing to reset unsafe profile directory: $PROFILE_DIR" >&2
        exit 2
    fi
    rm -rf "$PROFILE_DIR"
fi

if [[ -d "$PROFILE_DIR" ]] && [[ -n "$(find "$PROFILE_DIR" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
    cat >&2 <<EOF
ERROR: Profile directory is not empty: $PROFILE_DIR
Use --reset for a deliberate clean run, or choose a new --profile directory.
EOF
    exit 2
fi

mkdir -p "$PROFILE_DIR"

cat > "$PROFILE_DIR/README-novice-study-profile.txt" <<EOF
Gantry novice-study profile
===========================

This directory is intentionally isolated for one novice-study participant/run.
Gantry writes config.json, plot-history.json, recovery files, projects, and file
chooser history relative to this working directory.

Protocol: $ROOT/docs/NOVICE_STUDY.md
Results template: $ROOT/docs/NOVICE_STUDY_RESULTS_TEMPLATE.md
EOF

if [[ "$DRY_RUN" == true ]]; then
    echo "Novice-study profile: $PROFILE_DIR"
    echo "Repository root: $ROOT"
    echo "Application jar: $JAR"
    echo "Would run from profile directory: java -jar $JAR"
    exit 0
fi

if ! command -v java >/dev/null 2>&1; then
    echo "ERROR: Java was not found on PATH. Install Java 17+." >&2
    exit 1
fi

if [[ ! -f "$JAR" ]]; then
    echo "$JAR not found, building it first..."
    "$ROOT/scripts/build.sh"
fi

cat <<EOF
Starting Gantry novice-study session.
Profile: $PROFILE_DIR

Before handing over to the participant:
- confirm the window opens at 1024x800 or larger;
- confirm Your first plot is visible;
- do not pre-open Advanced controls, Console, guide, or import dialogs.
EOF

cd "$PROFILE_DIR"
exec java -jar "$JAR"
