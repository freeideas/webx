#!/bin/bash
set -euo pipefail

# This script tests WebX proxy endpoint using WebShot in headless mode
# Perfect for CI/CD environments and headless Linux VMs

echo "WebX Headless Proxy Test"
echo "========================"
echo ""

# Set up paths
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
WEBX_DIR="$SCRIPT_DIR"
WEBSHOT_DIR="$SCRIPT_DIR/../webshot"
OUTPUT_DIR="$WEBX_DIR/log"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Check for WebShot
if [ ! -f "$WEBSHOT_DIR/dist/webshot" ] && [ ! -f "$WEBSHOT_DIR/dist/webshot.exe" ]; then
    echo "Building WebShot first..."
    cd "$WEBSHOT_DIR"
    if [ -f "./build.sh" ]; then
        ./build.sh
    else
        echo "Error: WebShot build script not found"
        exit 1
    fi
    cd "$WEBX_DIR"
fi

# Determine webshot executable
if [ -f "$WEBSHOT_DIR/dist/webshot" ]; then
    WEBSHOT="$WEBSHOT_DIR/dist/webshot"
else
    WEBSHOT="$WEBSHOT_DIR/dist/webshot.exe"
fi

echo "Step 1: Compiling WebX..."
./java/javac.sh

echo "Step 2: Starting WebX with in-memory database..."
# Start WebX in background
./java/java.sh appz.webx.Main \
    --port 13102 \
    --jdbc "jdbc:hsqldb:mem:testdb" \
    --run > "$OUTPUT_DIR/webx-server-$TIMESTAMP.log" 2>&1 &

WEBX_PID=$!
echo "Server PID: $WEBX_PID"

# Cleanup function
cleanup() {
    echo -e "\nCleaning up..."
    if [ ! -z "$WEBX_PID" ]; then
        kill $WEBX_PID 2>/dev/null || true
        wait $WEBX_PID 2>/dev/null || true
    fi
}
trap cleanup EXIT

# Wait for server to start
echo "Step 3: Waiting for server to start..."
MAX_WAIT=30
for i in $(seq 1 $MAX_WAIT); do
    if curl -s http://localhost:13102/ > /dev/null 2>&1; then
        echo "Server is ready!"
        break
    fi
    if [ $i -eq $MAX_WAIT ]; then
        echo "Error: Server failed to start after $MAX_WAIT seconds"
        echo "Check log: $OUTPUT_DIR/webx-server-$TIMESTAMP.log"
        exit 1
    fi
    sleep 1
    echo -n "."
done
echo ""

# Give server a moment to stabilize
sleep 2

echo "Step 4: Running headless proxy tests..."
TEST_URL="http://localhost:13102/webx-proxy-test-headless.html"
OUTPUT_IMAGE="$OUTPUT_DIR/proxy-test-$TIMESTAMP.png"

# Run WebShot to execute the JavaScript tests and capture results
echo "Executing JavaScript tests via WebShot..."
"$WEBSHOT" "$TEST_URL" "$OUTPUT_IMAGE" 1280x800

# Check if capture was successful
if [ -f "$OUTPUT_IMAGE" ]; then
    echo ""
    echo "✅ Test completed successfully!"
    echo "   Screenshot: $OUTPUT_IMAGE"
    
    # Also save a text log of what happened
    echo "Test completed at $(date)" > "$OUTPUT_DIR/proxy-test-$TIMESTAMP.txt"
    echo "Server log: $OUTPUT_DIR/webx-server-$TIMESTAMP.log" >> "$OUTPUT_DIR/proxy-test-$TIMESTAMP.txt"
    echo "Screenshot: $OUTPUT_IMAGE" >> "$OUTPUT_DIR/proxy-test-$TIMESTAMP.txt"
    
    # Try to determine if tests passed by checking server log
    # (In a real CI/CD setup, you might want to check the image with OCR or modify the test to write results to a file)
    echo ""
    echo "Server handled requests:"
    grep -E "(GET|POST) /(proxy|db)" "$OUTPUT_DIR/webx-server-$TIMESTAMP.log" | tail -10 || true
    
else
    echo "❌ Error: Failed to capture test results"
    exit 1
fi

echo ""
echo "Test artifacts saved in: $OUTPUT_DIR/"
echo ""

# Optional: Keep server running for manual inspection
if [ "${1:-}" = "--keep-running" ]; then
    echo "Server still running on http://localhost:13102"
    echo "Press Ctrl+C to stop..."
    wait $WEBX_PID
else
    echo "Stopping server..."
    cleanup
    trap - EXIT
fi

echo "Done!"