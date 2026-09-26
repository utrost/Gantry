#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
exec python3 scripts/release.py "${@:-1.0.0-rc.2}"
