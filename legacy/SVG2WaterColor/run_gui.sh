#!/bin/bash

# Ensure the JAR exists
if [ ! -f target/watercolor-processor-1.0-SNAPSHOT.jar ]; then
    echo "JAR not found. Building project..."
    mvn package -DskipTests
fi

# Launch the GUI
echo "Launching Watercolor Processor GUI..."
java -cp target/watercolor-processor-1.0-SNAPSHOT.jar org.trostheide.watercolorprocessor.gui.WatercolorGUI
