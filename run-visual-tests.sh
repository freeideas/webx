#!/bin/bash
set -euo pipefail

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}WebX Visual Test Runner${NC}"
echo "================================"

# Set up paths
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
WEBX_DIR="$SCRIPT_DIR"
WEBSHOT_DIR="$SCRIPT_DIR/../webshot"
TEMP_DB="/tmp/webx-test-$(date +%s)"

# Check if webshot exists
if [ ! -f "$WEBSHOT_DIR/dist/webshot" ] && [ ! -f "$WEBSHOT_DIR/dist/webshot.exe" ]; then
    echo -e "${RED}Error: WebShot not found. Please build WebShot first.${NC}"
    echo "Run: cd $WEBSHOT_DIR && ./build.sh"
    exit 1
fi

# Determine webshot executable
if [ -f "$WEBSHOT_DIR/dist/webshot" ]; then
    WEBSHOT="$WEBSHOT_DIR/dist/webshot"
else
    WEBSHOT="$WEBSHOT_DIR/dist/webshot.exe"
fi

echo -e "${BLUE}1. Compiling WebX...${NC}"
cd "$WEBX_DIR"
./java/javac.sh

echo -e "${BLUE}2. Starting WebX server with temporary database...${NC}"
echo "   Database: $TEMP_DB"

# Start WebX in background with temporary database
./java/java.sh appz.webx.Main \
    --port 13102 \
    --jdbc "jdbc:hsqldb:mem:testdb" \
    --run &

WEBX_PID=$!
echo "   Server PID: $WEBX_PID"

# Function to cleanup on exit
cleanup() {
    echo -e "\n${BLUE}Cleaning up...${NC}"
    if [ ! -z "$WEBX_PID" ]; then
        kill $WEBX_PID 2>/dev/null || true
    fi
    # Clean up temporary database files if any were created
    rm -f "${TEMP_DB}"* 2>/dev/null || true
}
trap cleanup EXIT

# Wait for server to start
echo -e "${BLUE}3. Waiting for server to start...${NC}"
for i in {1..30}; do
    if curl -s http://localhost:13102/ > /dev/null 2>&1; then
        echo -e "${GREEN}   Server is ready!${NC}"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "${RED}   Server failed to start after 30 seconds${NC}"
        exit 1
    fi
    sleep 1
done

echo -e "${BLUE}4. Running automated tests...${NC}"
# Open the test page with autorun parameter
TEST_URL="http://localhost:13102/webx-test-visual.html?autorun"

# Wait a bit for tests to complete
echo "   Waiting for tests to complete..."
sleep 5

echo -e "${BLUE}5. Capturing test results with WebShot...${NC}"
OUTPUT_FILE="$WEBX_DIR/test-results-$(date +%Y%m%d-%H%M%S).png"

# Capture the test results
"$WEBSHOT" "$TEST_URL" "$OUTPUT_FILE" 1920x1080

if [ -f "$OUTPUT_FILE" ]; then
    echo -e "${GREEN}✅ Test results captured successfully!${NC}"
    echo "   Output: $OUTPUT_FILE"
    
    # Try to open the image (works on most systems)
    if command -v xdg-open &> /dev/null; then
        xdg-open "$OUTPUT_FILE"
    elif command -v open &> /dev/null; then
        open "$OUTPUT_FILE"
    elif command -v wslview &> /dev/null; then
        wslview "$OUTPUT_FILE"
    else
        echo "   Please open the file manually to view results"
    fi
else
    echo -e "${RED}❌ Failed to capture test results${NC}"
    exit 1
fi

echo -e "${BLUE}6. Test run complete!${NC}"
echo "   You can also view the live results at: http://localhost:13102/webx-test-visual.html"
echo "   Press Ctrl+C to stop the server..."

# Keep server running so user can interact with it
wait $WEBX_PID