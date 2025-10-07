#!/bin/bash
echo "Restarting application..."
sleep 2
java -jar "{JAR_PATH}" {ARGS} &
rm -- "$0"

