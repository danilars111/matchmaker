@echo off
rem This script is designed to be run from the Java application to restart it on Windows.
rem It runs silently in the background.

timeout /t 2 /nobreak > NUL
javaw -jar "{JAR_PATH}" {ARGS}

