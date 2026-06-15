#!/bin/bash
JAR="target/svgtoolbox-1.0-SNAPSHOT.jar"
IN="example.svg"
OUT="output_rotated.svg"

# Rotate 90 degrees
java -jar $JAR -i $IN -o $OUT --rotate 90 --stroke-width 1.0

# Verify output dimensions have swapped?
# (Bash verification of SVG dimensions is tricky, we'll manually inspect output or trust the success message for now)
echo "Generated $OUT"
