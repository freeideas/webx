#!/bin/bash

# Script to run WebX.jar
# The application will check if it's already running and won't start multiple instances

# Change to the WebX directory
cd /home/ace/prjx/webx

# Run the JAR file
java -cp "./java/dist/WebX.jar:./java/lib/*" appz.webx.Main -run