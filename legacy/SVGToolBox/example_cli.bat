@echo off
REM ==============================================================================
REM SVGToolBox CLI Example Script (Windows)
REM ==============================================================================
REM This script demonstrates the various capabilities of the SVGToolBox CLI,
REM including the newest hatching features, layer overrides, and optimization.
REM
REM PREREQUISITES:
REM - Ensure the project is built. Run `mvn clean package` first.
REM - Have an input SVG ready (e.g., example.svg).
REM ==============================================================================

SET JAR_PATH=target\svgtoolbox-1.0-SNAPSHOT.jar

IF NOT EXIST "%JAR_PATH%" (
    echo Error: Executable JAR not found at %JAR_PATH%.
    echo Please build the project first: mvn package
    exit /b 1
)

REM Define I/O paths
SET INPUT_SVG=example.svg
SET OUTPUT_SVG=output_example.svg

IF NOT EXIST "%INPUT_SVG%" (
    echo Warning: Input file '%INPUT_SVG%' not found. This script provides an example
    echo of the command syntax, but will fail if the input file does not exist.
)

echo Running SVGToolBox CLI with advanced examples...

REM ==============================================================================
REM EXAMPLE COMMAND EXPLANATION
REM ==============================================================================
REM 
REM Core Options:
REM   -i input.svg                  : The path to the source SVG file.
REM   -o output.svg                 : The path to save the processed SVG file.
REM   -p "#00FFFF,#FF00FF,#000000"  : Quantize all colors in the SVG to this specific palette.
REM   -w 0.5                        : Set the global stroke width to 0.5px.
REM
REM Hatching & Processing Options:
REM   -h                            : Enable the hatching engine.
REM   --hatch-angle 45.0            : Global default hatching angle (in degrees).
REM   --hatch-gap 5.0               : Global default hatching gap (line spacing in px).
REM   --pattern linear              : Global default hatch pattern.
REM                                   Options: none, empty, linear, cross, zigzag, wave, dot
REM
REM Layer Styling Overrides:
REM   -S (or --style)               : Apply layer-specific pattern overrides.
REM                                   Format: "HEX:ANGLE:GAP:PATTERN;HEX:ANGLE:GAP:PATTERN"
REM                                   Example below:
REM                                   #00FFFF gets linear hatch at 45 deg, 6px gap
REM                                   #FF00FF gets none (no hatch, preserves fill)
REM                                   #000000 gets empty (outlines only, no lines or fill)
REM
REM Visiblity & Overrides:
REM   --layer-width                 : Override stroke width for specific colors.
REM                                   Format: "HEX:WIDTH;HEX:WIDTH"
REM   --hidden-layers               : Hide specific colors from the output.
REM                                   Format: "HEX,HEX,HEX"
REM
REM Optimization & Geometry:
REM   --simplify 1.0                : Simplify paths using Ramer-Douglas-Peucker with 1.0 tolerance.
REM   --min-area 100                : Ignore paths with an area smaller than 100 px^2 for hatching.
REM   --rotate 90                   : Rotate the drawing 90 degrees.
REM   --crop A4                     : Crop bounds to A4 Landscape.
REM   --optimize                    : Sort paths using nearest-neighbor greedy algorithm.
REM   --stats                       : Print generated statistics to the console.
REM ==============================================================================

java -jar "%JAR_PATH%" ^
    -i "%INPUT_SVG%" ^
    -o "%OUTPUT_SVG%" ^
    -p "#00FFFF,#FF00FF,#000000" ^
    -w 0.5 ^
    -h ^
    --hatch-angle 45.0 ^
    --hatch-gap 5.0 ^
    --pattern linear ^
    --style "#00FFFF:45.0:6.0:linear;#FF00FF:135.0:4.0:none;#000000:0.0:8.0:empty" ^
    --layer-width "#00FFFF:1.0" ^
    --simplify 1.0 ^
    --min-area 100 ^
    --optimize ^
    --stats

echo Processing finished. Check %OUTPUT_SVG% if the command succeeded.
