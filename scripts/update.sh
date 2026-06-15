#!/usr/bin/env bash
# Pulls the latest changes for the current branch. Works on Linux and macOS.
set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v git >/dev/null 2>&1; then
    echo "ERROR: git was not found on PATH." >&2
    exit 1
fi

branch="$(git rev-parse --abbrev-ref HEAD)"
echo "Updating branch '$branch'..."
git pull --ff-only origin "$branch"

echo
echo "Update complete. Run scripts/build.sh to rebuild."
