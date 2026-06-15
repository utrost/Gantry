@echo off
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.9.9-hotspot"
if "%~1"=="" (
    mvn clean package
) else (
    mvn %*
)
