@echo off
setlocal

rem Define the known Java 17 path
set "KNOWN_JAVA=C:\Program Files\Eclipse Adoptium\jdk-17.0.9.9-hotspot"

rem Check if this specific Java exists. If not, skip to checking system PATH.
if not exist "%KNOWN_JAVA%" goto :CHECK_SYSTEM_JAVA

rem Setup environment to use the known Java
set "JAVA_HOME=%KNOWN_JAVA%"
set "PATH=%KNOWN_JAVA%\bin;%PATH%"
echo Using Java from: %JAVA_HOME%

:CHECK_SYSTEM_JAVA
rem Check if java is available now
java -version >nul 2>&1
if not errorlevel 1 goto :JAVA_FOUND

echo Java is not found in PATH. Please install Java 17.
pause
exit /b 1

:JAVA_FOUND
set "JAR=target\svgtoolbox-1.0-SNAPSHOT.jar"

if exist "%JAR%" goto :RUN_APP

echo Jar not found. Building...
call mvn package -DskipTests
if not errorlevel 1 goto :RUN_APP

echo Build failed. Please ensure Maven is installed and Java 17 is configured.
pause
exit /b 1

:RUN_APP
echo Starting SVG Toolbox GUI...
java -cp "%JAR%" org.trostheide.svgtoolbox.ui.GuiRunner
if errorlevel 1 (
    echo Application exited with error.
    pause
)
