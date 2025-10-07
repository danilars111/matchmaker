#!/bin/bash
echo "Updating application..."
sleep 3
rm "{CURRENT_JAR_PATH}"
mv "{NEW_JAR_PATH}" "{CURRENT_JAR_PATH}"
echo "Update complete. Restarting..."
java -jar "{CURRENT_JAR_PATH}" &
rm -- "$0"

