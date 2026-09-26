@echo off
setlocal
cd /d "%~dp0\.."
if "%~1"=="" (
  python scripts\release.py 1.0.0-rc.2
) else (
  python scripts\release.py %*
)
exit /b %errorlevel%
