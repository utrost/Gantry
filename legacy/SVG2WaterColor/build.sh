#!/usr/bin/env bash

# Optional: set JAVA_HOME if needed
# export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home

if [ -z "$1" ]; then
    mvn clean package
else
    mvn "$@"
fi
