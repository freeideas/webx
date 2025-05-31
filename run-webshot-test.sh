#!/bin/bash
# Run the WebX WebShot integration test

echo "Running WebX WebShot Integration Test"
echo "====================================="
echo ""

# Compile the Java code
echo "Compiling Java code..."
./java/javac.sh

# Run the test
echo ""
echo "Running test..."
./java/java.sh appz.webx.WebXWebShotTest