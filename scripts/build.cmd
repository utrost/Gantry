@echo off
REM Builds every module and runs tests.
REM Usage: scripts\build.cmd [--skip-tests]
setlocal
cd "%~dp0\.."

where mvn >nul 2>nul
if errorlevel 1 (
    echo ERROR: Maven ^(mvn^) was not found on PATH. Install Maven 3.8+ and Java 17+.
    exit /b 1
)

set GOAL=clean install
if "%~1"=="--skip-tests" set GOAL=clean install -DskipTests

echo Building Gantry (mvn %GOAL%)...
call mvn %GOAL%
if errorlevel 1 exit /b 1

echo.
echo Build complete. The standalone app jar is at app\target\app-1.0.0.jar
