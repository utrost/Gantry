#!/bin/bash
JAR="target/svgtoolbox-1.0-SNAPSHOT.jar"
IN="example.svg"
OUT="output_stats.svg"

# Run with stats
java -jar $JAR -i $IN -o $OUT --stats
