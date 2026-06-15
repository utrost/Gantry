#!/bin/bash
JAR="target/svgtoolbox-1.0-SNAPSHOT.jar"
IN="example.svg"

# Ensure we use global gap settings for visibility
FLAGS="--hatch --hatch-gap 4.0 --stroke-width 1.0"

echo "Generating Linear..."
java -jar $JAR -i $IN -o output_linear.svg $FLAGS --pattern linear

echo "Generating Cross..."
java -jar $JAR -i $IN -o output_cross.svg $FLAGS --pattern cross

echo "Generating ZigZag..."
java -jar $JAR -i $IN -o output_zigzag.svg $FLAGS --pattern zigzag

echo "Generating Wave..."
java -jar $JAR -i $IN -o output_wave.svg $FLAGS --pattern wave

echo "Generating Dot..."
java -jar $JAR -i $IN -o output_dot.svg $FLAGS --pattern dot

echo "Done."
