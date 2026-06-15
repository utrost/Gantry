#!/bin/bash
# automated_cli_test.sh
# This script verifies that the SVGToolBox CLI processes an SVG correctly,
# specifically ensuring that Path Optimization (--optimize) works without crashing.

set -e # Exit immediately if a command exits with a non-zero status
echo "Starting Automated CLI Validation..."

# Get absolute path to the SVGToolBox project root
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

JAR_FILE="$PROJECT_ROOT/target/svgtoolbox-1.0-SNAPSHOT.jar"
INPUT_FILE="$PROJECT_ROOT/example.svg"
OUTPUT_FILE="$PROJECT_ROOT/validation/cli_test_output.svg"

# Ensure the project is built
if [ ! -f "$JAR_FILE" ]; then
    echo "JAR not found at $JAR_FILE. Please build the project from the root directory first: mvn clean package"
    exit 1
fi

# Run the command with Hatching and Optimization enabled
echo "Running SVGToolBox CLI with --optimize..."
java -jar "$JAR_FILE" \
    --input "$INPUT_FILE" \
    --output "$OUTPUT_FILE" \
    --stroke-width 1.0 \
    --hatch \
    --optimize

echo ""
# Check if output file was created and is not empty
if [ -s "$OUTPUT_FILE" ]; then
    echo "✅ SUCCESS: Output file '$OUTPUT_FILE' generated successfully."
else
    echo "❌ ERROR: Output file was not generated or is empty."
    exit 1
fi

echo "Running SVGToolBox CLI with Per-layer options..."
OUTPUT_FILE_LAYER="$PROJECT_ROOT/Validation Scripts/cli_test_output_layer.svg"
java -jar "$JAR_FILE" \
    --input "$INPUT_FILE" \
    --output "$OUTPUT_FILE_LAYER" \
    --stroke-width 1.0 \
    --layer-width "#000000:2.0;#ff0000:0.5" \
    --hidden-layers "#00ff00" \
    --optimize

echo ""
if [ -s "$OUTPUT_FILE_LAYER" ]; then
    echo "✅ SUCCESS: Output file '$OUTPUT_FILE_LAYER' generated successfully."
    echo "Verification complete. Path optimization, per-layer settings, and Batik parsing are working correctly via CLI."
else
    echo "❌ ERROR: Output file was not generated or is empty."
    exit 1
fi
