#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

echo "Building SVG Toolbox from sources..."

# Clean and package the application using Maven
mvn clean package

echo "Build complete!"
echo "The compiled jar can be found in the target/ directory."
