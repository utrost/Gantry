@echo off
setlocal

REM Check if Python is installed
python --version >nul 2>&1
IF %ERRORLEVEL% NEQ 0 (
    echo Error: Python is not installed or not in your PATH.
    echo Please install Python from https://www.python.org/
    pause
    exit /b 1
)

IF "%~1"=="" (
    echo Usage: run_driver.bat [json_file]
    echo Example: run_driver.bat simpleSampleSVG.json
    exit /b 1
)

echo Running Driver with input: %1 %ARGS%
python driver\driver.py "%~1" %ARGS%
pause
