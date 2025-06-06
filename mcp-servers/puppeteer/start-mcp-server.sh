#!/bin/bash

# Start the Puppeteer MCP server
cd "$(dirname "$0")"

echo "Starting Puppeteer MCP server..."

# Set headless Chrome options for running in a VM
export PUPPETEER_EXECUTABLE_PATH=/snap/bin/chromium
export PUPPETEER_ARGS='--no-sandbox --disable-setuid-sandbox --disable-dev-shm-usage'

# Start the server
exec node node_modules/.bin/mcp-server-puppeteer