@echo off
setlocal

REM Default java command
set "JAVA_CMD=java"

REM Set JAVA_HOME to JDK 17 explicitly if found
REM This is required because Maven needs a JDK 17 (target 17) and we need Java 17 runtime
if exist "C:\Program Files\Eclipse Adoptium\jdk-17.0.9.9-hotspot" (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.9.9-hotspot"
    set "JAVA_CMD=C:\Program Files\Eclipse Adoptium\jdk-17.0.9.9-hotspot\bin\java"
    REM Also prepend to PATH so Maven picks it up
    set "PATH=C:\Program Files\Eclipse Adoptium\jdk-17.0.9.9-hotspot\bin;%PATH%"
)

REM Ensure the JAR exists
if not exist "target\watercolor-processor-1.0-SNAPSHOT.jar" (
    echo JAR not found. Building project...
    call mvn package -DskipTests
    if %ERRORLEVEL% NEQ 0 (
        echo.
        echo Build failed. Please check the error messages above.
        pause
        exit /b 1
    )
)

REM Launch the GUI
echo Launching Watercolor Processor GUI...
"%JAVA_CMD%" -cp target\watercolor-processor-1.0-SNAPSHOT.jar org.trostheide.watercolorprocessor.gui.WatercolorGUI
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo An error occurred while running the application.
    pause
)
