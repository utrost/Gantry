@echo off
REM Pulls the latest changes for the current branch.
setlocal
cd "%~dp0\.."

where git >nul 2>nul
if errorlevel 1 (
    echo ERROR: git was not found on PATH.
    exit /b 1
)

for /f "delims=" %%b in ('git rev-parse --abbrev-ref HEAD') do set BRANCH=%%b
echo Updating branch '%BRANCH%'...
call git pull --ff-only origin %BRANCH%
if errorlevel 1 exit /b 1

echo.
echo Update complete. Run scripts\build.cmd to rebuild.
