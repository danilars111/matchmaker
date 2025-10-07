@echo off
rem This script runs silently in the background.

timeout /t 3 /nobreak > NUL
del "{CURRENT_JAR_PATH}"
move "{NEW_JAR_PATH}" "{CURRENT_JAR_PATH}"
start "" javaw -jar "{CURRENT_JAR_PATH}"

