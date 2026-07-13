@echo off
REM Launches the Gantry GUI. Builds the app jar first if it doesn't exist yet.
setlocal
cd "%~dp0\.."

set JAR=app\target\app-1.0.0.jar

where java >nul 2>nul
if errorlevel 1 (
    echo ERROR: Java was not found on PATH. Install Java 17+.
    exit /b 1
)

if not exist "%JAR%" (
    echo %JAR% not found, building it first...
    call scripts\build.cmd
    if errorlevel 1 exit /b 1
)

echo Starting Gantry...
java -jar "%JAR%" %*
