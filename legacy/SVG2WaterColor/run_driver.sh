#!/usr/bin/env bash

# Check if Python is installed
if ! command -v python3 &> /dev/null; then
    echo "Error: Python 3 is not installed or not in your PATH."
    echo "Please install Python 3 from https://www.python.org/"
    exit 1
fi

if [ -z "$1" ]; then
    echo "Usage: ./run_driver.sh [json_file] [OPTIONS]"
    echo "Example: ./run_driver.sh simpleSampleSVG.json --mock"
    exit 1
fi

echo "Running Driver with input: $1"
python3 driver/driver.py "$@"
