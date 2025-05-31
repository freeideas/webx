# WebX - Simple Web Application Server

A lightweight, feature-rich web server that provides everything needed to build modern web applications with just HTML, CSS, and JavaScript.

**Entry Point:** `java/src/appz/webx/Main.java`

## Overview

WebX is a simple yet powerful web server that provides three core services out of the box:

- **Static File Serving** (`/www`) - Serve your HTML, CSS, JS, and other static assets
- **API Proxy** (`/proxy`) - Allow web pages to access any external API, bypassing CORS restrictions
- **JSON Database** (`/db`) - A persistent JSON data store that accepts POST/PUT requests and merges data into a persistent structure

With these three services, you can build fully-functional web applications without any backend code. Your HTML/CSS/JS pages have everything they need to store data, access external APIs, and serve content.

## Quick Start

### Running the Server

```bash
# Compile the Java source
./java/javac.sh

# Start the server (default port: 13102)
./java/java.sh appz.webx.Main
```

For Windows PowerShell:
```powershell
powershell.exe -File ./java/javac.ps1
powershell.exe -File ./java/java.ps1 appz.webx.Main
```

The server will start on `http://localhost:13102` and serve files from `./datafiles/www` at `/www` by default.

### Command Line Options

WebX uses a unified `path@config` format for all endpoints:

```bash
# Run on a different port
./java/java.sh appz.webx.Main --port=8080

# Serve files from a different directory (available at /www)
./java/java.sh appz.webx.Main --static=www@/path/to/your/files

# Custom endpoint paths
./java/java.sh appz.webx.Main --static=files@./public --proxy=api@./keys.json --db=data@jdbc:hsqldb:mem:myapp
```

**Parameter Format:**
- `--static=path@directory` - Static files served at `/path` from `directory`
- `--proxy=path@config-file` - Proxy endpoint at `/path` using `config-file` for API keys
- `--db=path@jdbc-url` - Database endpoint at `/path` using `jdbc-url`

**Disabling Endpoints:**
Any endpoint can be disabled by setting it to `NONE` (case-insensitive):
```bash
# Disable static files
./java/java.sh appz.webx.Main --static=NONE --run

# Disable proxy 
./java/java.sh appz.webx.Main --proxy=none --run

# Disable database
./java/java.sh appz.webx.Main --db=None --run

# Run with only proxy enabled
./java/java.sh appz.webx.Main --static=NONE --db=NONE --run
```

**Note:** Command line arguments must use the `--arg=value` format (with equals sign). Space-separated arguments like `--port 8080` are not supported.

## Core Features

### 1. Static File Server

By default, files are served from `./datafiles/www` at the path `/www`. 

- Automatic directory listings
- MIME type detection
- Support for multiple root directories

Examples:
```bash
# Serve your web app from a custom directory (will be available at /www)
./java/java.sh appz.webx.Main --static=www@/home/user/mywebapp

# Use a custom path for static files
./java/java.sh appz.webx.Main --static=app@./my-web-app
# Files will be available at http://localhost:13102/app/
```

### 2. API Proxy (`/proxy`)

The proxy handler allows your web pages to access any external API without CORS restrictions.

**Usage from JavaScript:**
```javascript
const response = await fetch('/proxy', {
    method: 'GET',
    headers: {
        'X-Target-URL': 'https://api.example.com/data'
    }
});
const data = await response.json();
```

Features:
- Supports all HTTP methods (GET, POST, PUT, DELETE, etc.)
- Forwards headers and request bodies
- Automatically adds CORS headers
- Perfect for accessing third-party APIs from browser-based applications
- Optional API key replacement (configure with `--proxy=proxy@/path/to/keys.json`)
- Hierarchical URL-based configuration for secure API key management

### 3. JSON Database (`/db`)

A simple but powerful persistent JSON storage system. Send POST or PUT requests with JSON data, and it will be merged into the persistent store.

**Note:** The `/db` handler needs to be configured with a persistent data store. Here's an example of how to set it up:

```java
// In a custom server setup
Map<Object,Object> persistentData = new HashMap<>();
server.handlers.put("/db", new HttpJsonHandler(persistentData));
```

**Usage from JavaScript:**
```javascript
// Store user data
await fetch('/db', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
        users: {
            'user123': {
                name: 'John Doe',
                email: 'john@example.com'
            }
        }
    })
});

// Update nested data (merges with existing)
await fetch('/db', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
        users: {
            'user123': {
                lastLogin: new Date().toISOString()
            }
        }
    })
});
```

Features:
- Accepts any JSON-representable data
- Deep merging of nested objects
- Persistent storage across server restarts
- Simple POST/PUT interface

## Additional Features

### Request Logging

All HTTP requests are automatically logged to `./log/` directory for debugging and monitoring.

## Build System

### Compile All Java Files
```bash
./java/javac.sh
```

### Compile Specific Files
```bash
# Single file
./java/javac.sh java/src/http/HttpServer.java

# Directory
./java/javac.sh java/src/http
```

### Run Tests
```bash
./java/java.sh Test
```

### Download Dependencies
```bash
./java/java.sh DownloadJars
```

## Project Structure

```
webx/
├── java/
│   ├── src/
│   │   ├── http/          # HTTP server and handlers
│   │   ├── jLib/          # Java utility libraries
│   │   └── persist/       # Persistence utilities
│   ├── class/             # Compiled Java classes
│   ├── lib/               # External JAR dependencies
│   └── scripts            # Build scripts (.sh/.ps1)
├── datafiles/
│   └── www/               # Default web root
└── log/                   # Request logs
```

## Development

### Code Style

This project follows strict formatting guidelines (see CODE_GUIDELINES.md):
- Brevity is prioritized
- Early returns to avoid nesting
- Minimal comments
- 120 character line limit

### Testing

Java tests use a simple convention:
- Test methods end with `_TEST_`
- Tests return boolean (true = pass)
- Run with `Lib.testClass()`
