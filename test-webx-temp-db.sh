#!/bin/bash
# Simple script to run WebX with a temporary in-memory database for testing

echo "Starting WebX with temporary in-memory database..."
echo "================================================"
echo ""
echo "Configuration:"
echo "  Port: 13102"
echo "  Database: In-memory (jdbc:hsqldb:mem:testdb)"
echo "  Static files: ./datafiles/www"
echo ""
echo "Test URLs:"
echo "  Visual Test Suite: http://localhost:13102/webx-test-visual.html"
echo "  Auto Test Suite: http://localhost:13102/webx-test-auto.html"
echo "  Manual Test Suite: http://localhost:13102/webx-test.html"
echo ""
echo "Press Ctrl+C to stop the server"
echo ""

# Compile and run
cd "$(dirname "$0")"
./java/javac.sh && ./java/java.sh appz.webx.Main --jdbc "jdbc:hsqldb:mem:testdb" --run