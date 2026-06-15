#!/bin/bash

JAR="target/svgtoolbox-1.0-SNAPSHOT.jar"

if [ ! -f "$JAR" ]; then
    echo "Jar not found. Building..."
    mvn package -DskipTests
fi

# Check for specific JDK 17 (headful)
JAVA_CMD="java"
if [ -f "/usr/lib/jvm/java-17-openjdk-amd64/bin/java" ]; then
    JAVA_CMD="/usr/lib/jvm/java-17-openjdk-amd64/bin/java"
fi

echo "Starting SVG Toolbox GUI using $JAVA_CMD..."
$JAVA_CMD -cp "$JAR" org.trostheide.svgtoolbox.ui.GuiRunner
