@echo off
setlocal
cd /d %~dp0\..
set VERSION=%1
if "%VERSION%"=="" set VERSION=1.0.0
call scripts\build.cmd || exit /b 1
if not exist dist mkdir dist
copy /Y app\target\app-%VERSION%.jar dist\Gantry-%VERSION%.jar >nul
if errorlevel 1 exit /b 1
copy /Y cli\target\cli-%VERSION%.jar dist\Gantry-CLI-%VERSION%.jar >nul
if errorlevel 1 exit /b 1
copy /Y LICENSE dist\LICENSE >nul
copy /Y README.md dist\README.md >nul
powershell -NoProfile -Command "$files = 'Gantry-%VERSION%.jar','Gantry-CLI-%VERSION%.jar'; $lines = foreach ($name in $files) { $hash = (Get-FileHash -Algorithm SHA256 (Join-Path 'dist' $name)).Hash.ToLower(); $hash + '  ' + $name }; Set-Content -Encoding ascii dist\SHA256SUMS $lines"
if errorlevel 1 exit /b 1
echo Release artifacts created in dist\
