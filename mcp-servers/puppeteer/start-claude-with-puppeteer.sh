#!/bin/bash

# Script to start Claude Code with Puppeteer MCP server

echo "Starting Claude Code with Puppeteer MCP server..."

# Get the absolute path to the MCP server
MCP_SERVER_PATH="$(cd "$(dirname "$0")" && pwd)/node_modules/.bin/mcp-server-puppeteer"

# Set environment variables for headless Chrome
export PUPPETEER_EXECUTABLE_PATH=/snap/bin/chromium
export PUPPETEER_ARGS='--no-sandbox --disable-setuid-sandbox --disable-dev-shm-usage'

# Start Claude Code with the MCP server
# Replace 'claude' with your actual Claude Code command if different
claude --mcp puppeteer="$MCP_SERVER_PATH"