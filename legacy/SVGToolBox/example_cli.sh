#!/bin/bash
# ==============================================================================
# SVGToolBox CLI Example Script (Linux/macOS)
# ==============================================================================
# This script demonstrates the various capabilities of the SVGToolBox CLI,
# including the newest hatching features, layer overrides, and optimization.
#
# PREREQUISITES:
# - Ensure the project is built. Run `./build.sh` or `mvn clean package` first.
# - Have an input SVG ready (e.g., example.svg).
# ==============================================================================

# Path to the compiled executable JAR
JAR_PATH="target/svgtoolbox-1.0-SNAPSHOT.jar"

if [ ! -f "$JAR_PATH" ]; then
    echo "Error: Executable JAR not found at $JAR_PATH."
    echo "Please build the project first: ./build.sh or mvn package"
    exit 1
fi

# Define I/O paths
INPUT_SVG="example.svg"
OUTPUT_SVG="output_example.svg"

# Ensure input file exists before running
if [ ! -f "$INPUT_SVG" ]; then
    echo "Warning: Input file '$INPUT_SVG' not found. This script provides an example"
    echo "of the command syntax, but will fail if the input file does not exist."
fi

echo "Running SVGToolBox CLI with advanced examples..."

# ==============================================================================
# EXAMPLE COMMAND EXPLANATION
# ==============================================================================
# 
# Core Options:
#   -i input.svg                  : The path to the source SVG file.
#   -o output.svg                 : The path to save the processed SVG file.
#   -p "#00FFFF,#FF00FF,#000000"  : Quantize all colors in the SVG to this specific palette.
#   -w 0.5                        : Set the global stroke width to 0.5px.
#
# Hatching & Processing Options:
#   -h                            : Enable the hatching engine.
#   --hatch-angle 45.0            : Global default hatching angle (in degrees).
#   --hatch-gap 5.0               : Global default hatching gap (line spacing in px).
#   --pattern linear              : Global default hatch pattern.
#                                   Options: none, empty, linear, cross, zigzag, wave, dot
#
# Layer Styling Overrides:
#   -S (or --style)               : Apply layer-specific pattern overrides.
#                                   Format: "HEX:ANGLE:GAP:PATTERN;HEX:ANGLE:GAP:PATTERN"
#                                   Example below:
#                                   #00FFFF gets linear hatch at 45 deg, 6px gap
#                                   #FF00FF gets none (no hatch, preserves fill)
#                                   #000000 gets empty (outlines only, no lines or fill)
#
# Visiblity & Overrides:
#   --layer-width                 : Override stroke width for specific colors.
#                                   Format: "HEX:WIDTH;HEX:WIDTH"
#   --hidden-layers               : Hide specific colors from the output.
#                                   Format: "HEX,HEX,HEX"
#
# Optimization & Geometry:
#   --simplify 1.0                : Simplify paths using Ramer-Douglas-Peucker with 1.0 tolerance.
#   --min-area 100                : Ignore paths with an area smaller than 100 px^2 for hatching.
#   --rotate 90                   : Rotate the drawing 90 degrees.
#   --crop A4                     : Crop bounds to A4 Landscape.
#   --optimize                    : Sort paths using nearest-neighbor greedy algorithm.
#   --stats                       : Print generated statistics to the console.
# ==============================================================================

java -jar "$JAR_PATH" \
    -i "$INPUT_SVG" \
    -o "$OUTPUT_SVG" \
    -p "#00FFFF,#FF00FF,#000000" \
    -w 0.5 \
    -h \
    --hatch-angle 45.0 \
    --hatch-gap 5.0 \
    --pattern linear \
    --style "#00FFFF:45.0:6.0:linear;#FF00FF:135.0:4.0:none;#000000:0.0:8.0:empty" \
    --layer-width "#00FFFF:1.0" \
    --simplify 1.0 \
    --min-area 100 \
    --optimize \
    --stats

echo "Processing finished. Check $OUTPUT_SVG if the command succeeded."
