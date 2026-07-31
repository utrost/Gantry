@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d %~dp0\..
set RELEASE_VERSION=%1
if "%RELEASE_VERSION%"=="" set RELEASE_VERSION=1.0.0
for /f "usebackq delims=" %%v in (`powershell -NoProfile -Command "[xml]$pom = Get-Content pom.xml; $pom.project.version"`) do set PROJECT_VERSION=%%v
if "%PROJECT_VERSION%"=="" (
  echo ERROR: Could not read project.version from pom.xml 1>&2
  exit /b 1
)
call scripts\build.cmd || exit /b 1
if not exist dist mkdir dist
if not exist app\target\app-%PROJECT_VERSION%.jar (
  echo ERROR: expected GUI artifact not found: app\target\app-%PROJECT_VERSION%.jar 1>&2
  exit /b 1
)
if not exist cli\target\cli-%PROJECT_VERSION%.jar (
  echo ERROR: expected CLI artifact not found: cli\target\cli-%PROJECT_VERSION%.jar 1>&2
  exit /b 1
)
copy /Y app\target\app-%PROJECT_VERSION%.jar dist\Gantry-%RELEASE_VERSION%.jar >nul
if errorlevel 1 exit /b 1
copy /Y cli\target\cli-%PROJECT_VERSION%.jar dist\Gantry-CLI-%RELEASE_VERSION%.jar >nul
if errorlevel 1 exit /b 1
copy /Y LICENSE dist\LICENSE >nul
copy /Y README.md dist\README.md >nul
powershell -NoProfile -Command "$files = 'Gantry-%RELEASE_VERSION%.jar','Gantry-CLI-%RELEASE_VERSION%.jar'; $lines = foreach ($name in $files) { $hash = (Get-FileHash -Algorithm SHA256 (Join-Path 'dist' $name)).Hash.ToLower(); $hash + '  ' + $name }; Set-Content -Encoding ascii dist\SHA256SUMS $lines"
if errorlevel 1 exit /b 1
echo Release artifacts created in dist\
echo Project version: %PROJECT_VERSION%
echo Release label: %RELEASE_VERSION%
