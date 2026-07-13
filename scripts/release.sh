#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
VERSION="${1:-1.0.0}"
./scripts/build.sh
mkdir -p dist
cp app/target/app-${VERSION}.jar "dist/Gantry-${VERSION}.jar"
cp cli/target/cli-${VERSION}.jar "dist/Gantry-CLI-${VERSION}.jar"
cp LICENSE README.md dist/
(cd dist && shasum -a 256 "Gantry-${VERSION}.jar" "Gantry-CLI-${VERSION}.jar" > SHA256SUMS)
echo "Release artifacts created in dist/"
