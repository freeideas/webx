# WebX - Simple Web Application Server

A lightweight, feature-rich web server that provides everything needed to build modern web applications with just HTML, CSS, and JavaScript.

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
./java/java.sh http.HttpServer
```

For Windows PowerShell:
```powershell
powershell.exe -File ./java/javac.ps1
powershell.exe -File ./java/java.ps1 http.HttpServer
```

The server will start on `http://localhost:13102` and serve files from `./datafiles/www` by default.

### Command Line Options

```bash
# Run on a different port
./java/java.sh http.HttpServer --port 8080

# Serve files from a different directory
./java/java.sh http.HttpServer --dir /:/path/to/your/files

# Serve multiple directories
./java/java.sh http.HttpServer --dir /app:/path/to/app --dir /assets:/path/to/assets
```

## Core Features

### 1. Static File Server

By default, files are served from `./datafiles/www` at the root path `/`. 

- Automatic directory listings
- MIME type detection
- Support for multiple root directories

Example:
```bash
# Serve your web app from a custom directory
./java/java.sh http.HttpServer --dir /:/home/user/mywebapp
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

### Authentication

The server includes a built-in login handler at `/login` that provides:
- Email-based authentication
- Secure token generation
- Session management

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

## License

[Add your license information here]

## Contributing

[Add contribution guidelines here]