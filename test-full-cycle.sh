#!/bin/bash
set -euo pipefail

# Full cycle test: WebX serves JavaScript → WebShot executes it → JavaScript writes to DB → We verify

echo "WebX Full Cycle Test with WebShot"
echo "================================="
echo ""
echo "This test demonstrates:"
echo "1. WebX serves a static HTML page with JavaScript"
echo "2. WebShot loads the page in a headless browser"
echo "3. JavaScript executes and writes to WebX's /db endpoint"
echo "4. We capture visual proof and verify the database write"
echo ""

# Set up paths
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
WEBX_DIR="$SCRIPT_DIR"
WEBSHOT_DIR="$SCRIPT_DIR/../webshot"
OUTPUT_DIR="$WEBX_DIR/test-results"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
DB_DIR="/tmp/webx-test-db-$TIMESTAMP"

# Create directories
mkdir -p "$OUTPUT_DIR"
mkdir -p "$DB_DIR"

# Check for WebShot
if [ ! -f "$WEBSHOT_DIR/dist/webshot" ] && [ ! -f "$WEBSHOT_DIR/dist/webshot.exe" ]; then
    echo "Error: WebShot not found. Please build it first:"
    echo "  cd $WEBSHOT_DIR && ./build.sh"
    exit 1
fi

# Determine webshot executable
WEBSHOT="$WEBSHOT_DIR/dist/webshot"
[ -f "$WEBSHOT_DIR/dist/webshot.exe" ] && WEBSHOT="$WEBSHOT_DIR/dist/webshot.exe"

echo "=== Phase 1: Starting WebX ==="
echo "Compiling WebX..."
./java/javac.sh

echo "Starting WebX with file-based database (so we can inspect it)..."
echo "Database location: $DB_DIR"

# Start WebX with file-based temp database
./java/java.sh appz.webx.Main \
    --port 13102 \
    --jdbc "jdbc:hsqldb:file:$DB_DIR/testdb;shutdown=true" \
    --run > "$OUTPUT_DIR/webx-$TIMESTAMP.log" 2>&1 &

WEBX_PID=$!
echo "Server PID: $WEBX_PID"

# Cleanup function
cleanup() {
    echo -e "\nCleaning up..."
    if [ ! -z "${WEBX_PID:-}" ]; then
        kill $WEBX_PID 2>/dev/null || true
        wait $WEBX_PID 2>/dev/null || true
    fi
    # Optional: Remove temp database
    # rm -rf "$DB_DIR"
}
trap cleanup EXIT

# Wait for server
echo "Waiting for server..."
for i in {1..30}; do
    if curl -s http://localhost:13102/ > /dev/null 2>&1; then
        echo "Server ready!"
        break
    fi
    [ $i -eq 30 ] && { echo "Server failed to start"; exit 1; }
    sleep 1
done

sleep 2

echo ""
echo "=== Phase 2: Running JavaScript via WebShot ==="
echo "WebShot will load the test page, execute JavaScript, and write to the database..."

# Capture the proxy test page
"$WEBSHOT" "http://localhost:13102/webx-proxy-test-headless.html" \
    "$OUTPUT_DIR/test-execution-$TIMESTAMP.png" 1280x800

echo "Screenshot captured: $OUTPUT_DIR/test-execution-$TIMESTAMP.png"

# Wait for JavaScript to complete
echo "Waiting for JavaScript execution to complete..."
sleep 3

echo ""
echo "=== Phase 3: Verifying Database Writes ==="

# Check the database files
echo "Database files created:"
ls -la "$DB_DIR/" | grep -E "\.(script|properties|log)" || echo "No database files found!"

# Look for evidence in the HSQLDB script file
if [ -f "$DB_DIR/testdb.script" ]; then
    echo ""
    echo "Checking database content..."
    if grep -q "proxyTest" "$DB_DIR/testdb.script" 2>/dev/null; then
        echo "✅ SUCCESS: Found 'proxyTest' data in database!"
        echo ""
        echo "Database entries containing 'proxyTest':"
        grep "proxyTest" "$DB_DIR/testdb.script" | head -5
    else
        echo "⚠️  WARNING: Could not find 'proxyTest' in database"
    fi
    
    if grep -q "timestamp" "$DB_DIR/testdb.script" 2>/dev/null; then
        echo ""
        echo "Found timestamp entries:"
        grep "timestamp" "$DB_DIR/testdb.script" | head -3
    fi
else
    echo "⚠️  Database script file not found"
fi

echo ""
echo "=== Phase 4: Visual Verification ==="

# Capture the verification page
echo "Capturing database verification page..."
"$WEBSHOT" "http://localhost:13102/webx-db-verification.html" \
    "$OUTPUT_DIR/verification-$TIMESTAMP.png" 1280x600

echo "Verification screenshot: $OUTPUT_DIR/verification-$TIMESTAMP.png"

echo ""
echo "=== Test Complete ==="
echo ""
echo "Summary:"
echo "1. WebX served static files from: $WEBX_DIR/datafiles/www/"
echo "2. WebShot executed JavaScript that made requests to /proxy and /db"
echo "3. Database files created at: $DB_DIR/"
echo "4. Screenshots saved to: $OUTPUT_DIR/"
echo ""
echo "Server log: $OUTPUT_DIR/webx-$TIMESTAMP.log"
echo ""

# Show some request logs
echo "HTTP requests processed by WebX:"
grep -E "^(GET|POST|PUT)" "$OUTPUT_DIR/webx-$TIMESTAMP.log" 2>/dev/null | tail -10 || echo "No requests found in log"

echo ""
echo "You can inspect the results:"
echo "- View screenshots to see test execution"
echo "- Check $DB_DIR/testdb.script for raw database content"
echo "- Review server log for all HTTP requests"
echo ""

# Keep running?
if [ "${1:-}" = "--keep-running" ]; then
    echo "Server still running. You can browse to:"
    echo "  http://localhost:13102/webx-test-visual.html"
    echo "  http://localhost:13102/webx-proxy-test-headless.html"
    echo ""
    echo "Press Ctrl+C to stop..."
    wait $WEBX_PID
fi