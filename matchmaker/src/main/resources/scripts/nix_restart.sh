#!/bin/bash
echo "Restarting application..."
sleep 2
java -jar "{JAR_PATH}" &
rm -- "$0"

