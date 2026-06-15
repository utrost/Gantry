#!/usr/bin/env bash

echo "=========================================="
echo "  Watercolor Processor - Driver Setup"
echo "=========================================="

# Check for Python
if ! command -v python3 &> /dev/null; then
    echo "Error: Python 3 is not installed or not in your PATH."
    echo "Please install Python 3.9+ from https://www.python.org/"
    exit 1
fi

echo "Python found. Installing dependencies..."
echo ""

# Install dependencies
pip3 install -r driver/requirements.txt
if [ $? -ne 0 ]; then
    echo ""
    echo "Error installing dependencies."
    echo "Please check your internet connection and try again."
    exit 1
fi

echo ""
echo "=========================================="
echo "  Verifying Installation..."
echo "=========================================="

python3 -c "import pyaxidraw; print('PyAxiDraw imported successfully!')"
if [ $? -ne 0 ]; then
    echo "Error: pyaxidraw could not be imported."
    exit 1
fi

echo ""
echo "=========================================="
echo "  Setup Complete!"
echo "=========================================="
echo ""
echo "Note: Ensure the AxiDraw USB drivers are installed."
echo ""
