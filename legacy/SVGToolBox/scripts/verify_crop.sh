#!/bin/bash
JAR="target/svgtoolbox-1.0-SNAPSHOT.jar"
IN="example.svg"
OUT="output_crop.svg"

# Crop to A4 (Standard size, likely covers the drawing)
java -jar $JAR -i $IN -o $OUT --crop A4

echo "Created output_crop.svg"
