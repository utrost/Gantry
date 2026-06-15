@echo off
setlocal

echo ==========================================
echo   Watercolor Processor - Driver Setup
echo ==========================================

REM Check for Python
python --version >nul 2>&1
IF %ERRORLEVEL% NEQ 0 (
    echo Error: Python is not installed or not in your PATH.
    echo Please install Python 3.9+ from https://www.python.org/
    pause
    exit /b 1
)

echo Python found. Installing dependencies...
echo.

REM Install dependencies
pip install -r driver\requirements.txt
IF %ERRORLEVEL% NEQ 0 (
    echo.
    echo Error installing dependencies.
    echo Please check your internet connection and try again.
    pause
    exit /b 1
)

echo.
echo ==========================================
echo   Verifying Installation...
echo ==========================================

python -c "import pyaxidraw; print('PyAxiDraw imported successfully!')"
IF %ERRORLEVEL% NEQ 0 (
    echo Error: pyaxidraw could not be imported.
    pause
    exit /b 1
)

echo.
echo ==========================================
echo   Setup Complete!
echo ==========================================
echo.
echo Note: Ensure the AxiDraw USB drivers are installed.
echo These are usually installed automatically with the AxiDraw software.
echo.
pause
