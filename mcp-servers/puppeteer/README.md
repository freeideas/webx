# Puppeteer MCP Server

The Puppeteer MCP server has been successfully installed and configured for this headless VM environment.

## Installation Summary

1. **Node.js**: v20.19.2
2. **Puppeteer MCP Server**: @modelcontextprotocol/server-puppeteer@2025.5.12
3. **Chromium**: Installed via snap with all required dependencies

## Starting the Server

The MCP server is ready to use. To start it manually:

```bash
./start-mcp-server.sh
```

## Configuration

The server is configured to run Chromium in headless mode with the following settings:
- Executable: `/snap/bin/chromium`
- Args: `--no-sandbox --disable-setuid-sandbox --disable-dev-shm-usage`

## Testing

To verify Puppeteer is working correctly:

```bash
node test-puppeteer.js
```

## Usage with Claude Code

The MCP server needs to be integrated with Claude Code's configuration. The server executable is located at:
```
/home/ace/prjx/webx/mcp-servers/puppeteer/node_modules/.bin/mcp-server-puppeteer
```

## Notes

- The server runs in headless mode suitable for VM environments
- All necessary Chrome/Chromium dependencies have been installed
- The server will start and wait for MCP client connections