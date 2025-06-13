# WebX - Simple Web Application Server

A lightweight, feature-rich web server that provides everything needed to build modern web applications with just HTML, CSS, and JavaScript.

**Entry Point:** `java/src/appz/webx/Main.java`

## Overview

WebX is a simple yet powerful web server that provides five core services out of the box:

- **Static File Serving** (`/www`) - Serve your HTML, CSS, JS, and other static assets
- **Server-Side JavaScript** (`.jss` files) - Execute JavaScript on the server to generate dynamic content
- **API Proxy** (`/proxy`) - Allow web pages to access any external API, bypassing CORS restrictions
- **JSON Database** (`/db`) - A persistent JSON data store that accepts POST/PUT requests and merges data into a persistent structure
- **Email Authentication** (`/login`) - JSON API for email-based login with 6-digit codes and JWT tokens

With these five services, you can build fully-functional web applications with minimal backend code. Your HTML/CSS/JS pages have everything they need to store data, access external APIs, authenticate users, and serve content, plus server-side JavaScript for dynamic page generation.

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
./java/java.sh appz.webx.Main --static=files@./public --proxy=api@./keys.json --db=data@jdbc:hsqldb:mem:myapp --login=auth@MyApp
```

**Parameter Format:**
- `--static=path@directory` - Static files served at `/path` from `directory`
- `--proxy=path@config-file` - Proxy endpoint at `/path` using `config-file` for API keys
- `--db=path@jdbc-url` - Database endpoint at `/path` using `jdbc-url`
- `--login=path@app-name` - Login endpoint at `/path` with `app-name` for email subjects

**Disabling Endpoints:**
Any endpoint can be disabled by setting it to `NONE` (case-insensitive):
```bash
# Disable static files
./java/java.sh appz.webx.Main --static=NONE --run

# Disable proxy 
./java/java.sh appz.webx.Main --proxy=none --run

# Disable database
./java/java.sh appz.webx.Main --db=None --run

# Disable login
./java/java.sh appz.webx.Main --login=NONE --run

# Run with only proxy enabled
./java/java.sh appz.webx.Main --static=NONE --db=NONE --login=NONE --run
```

**Note:** Command line arguments must use the `--arg=value` format (with equals sign). Space-separated arguments like `--port 8080` are not supported.

## Core Features

### 1. Static File Server & Server-Side JavaScript

By default, files are served from `./datafiles/www` at the path `/www`. 

**Static Files:**
- Intelligent directory handling with index file support
- MIME type detection
- Support for multiple root directories

**Server-Side JavaScript (`.jss` files):**
WebX automatically executes `.jss` files as server-side JavaScript instead of serving them as static files. These files can generate dynamic HTML, process form data, or perform server-side logic.

**Directory Index Handling:**
When a request is made to a directory (path ending with `/`), WebX follows this priority order:
1. **`index.jss`** - If found, executes the server-side JavaScript file to generate the response
2. **`index.html`** - If found, serves the static HTML file
3. **Auto Directory Listing** - If neither index file exists, generates an HTML page with a clickable directory listing table

**Creating a `.jss` file:**
```javascript
// Example: ./datafiles/www/hello.jss
function handle(request) {
    return {
        status: 200,
        headers: {"Content-Type": "text/html"},
        body: `
            <html>
                <body>
                    <h1>Hello from Server-Side JavaScript!</h1>
                    <p>Request method: ${request.method}</p>
                    <p>Request URL: ${request.url}</p>
                    <p>Current time: ${new Date().toISOString()}</p>
                </body>
            </html>
        `
    };
}
```

**Request Object Properties:**
- `request.method` - HTTP method (GET, POST, etc.)
- `request.url` - Full request URL including query parameters
- `request.headers` - Object containing all HTTP headers
- `request.body` - Raw request body content (string or bytes)
- `request.parsedBody` - Automatically parsed body content (JSON objects, form data, etc.)
- `request.params` - Combined parameters from cookies, parsed body, and query string (query string takes precedence)

**Response Object Format:**
- `status` - HTTP status code (200, 404, etc.)
- `headers` - Object with response headers
- `body` - Response content (string or bytes)

**Directory Examples:**
```bash
# Basic usage - visit http://localhost:13102/www/ to see directory listing
./java/java.sh appz.webx.Main --run

# Custom directory with index.jss for dynamic home page
./java/java.sh appz.webx.Main --static=app@./my-web-app --run
# http://localhost:13102/app/ will execute index.jss if present
# Otherwise falls back to index.html or directory listing
```

**Directory Structure Example:**
```
datafiles/www/
├── index.jss          # Dynamic home page (executed for /www/)
├── about/
│   └── index.html     # Static about page (served for /www/about/)
├── products/          # No index files (shows directory listing for /www/products/)
│   ├── item1.html
│   └── item2.html
└── api/
    └── users.jss      # API endpoint at /www/api/users.jss
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

### 4. Email Authentication (`/login`)

A JSON API for email-based authentication using 6-digit login codes sent via email, with JWT token management.

**Usage from JavaScript:**

**Send Login Code:**
```javascript
// Request a login code be sent to email
const response = await fetch('/login?command=sendEmail&email=user@example.com', {
    method: 'POST'
});
const result = await response.json();
// Returns: {"success": true, "message": "Email sent successfully"}
```

**Validate Login Code:**
```javascript
// Validate the 6-digit code and get auth token
const response = await fetch('/login?command=validateLoginCode&email=user@example.com&loginCode=123456', {
    method: 'POST'
});
const result = await response.json();
// Returns: {"success": true, "authToken": "...", "email": "user@example.com"}
// Also sets Authorization cookie automatically
```

**Get Current User:**
```javascript
// Get email from any valid auth token in request
const response = await fetch('/login?command=userEmail', {
    method: 'GET'
});
const result = await response.json();
// Returns: {"success": true, "email": "user@example.com"}
```

Features:
- Email-based authentication with 6-digit codes
- 1-hour login code expiration for security
- JWT tokens set as cookies and returned in JSON
- No passwords required - just email verification
- Persistent login sessions across browser restarts
- Works with any valid AuthToken found in request headers or cookies

## Additional Features

### Security & Access Control

WebX provides extensible security through the `SecurityGuard` class (`java/src/http/SecurityGuard.java`), which allows you to implement custom authorization rules for both proxy and database access.

**Example Use Cases:**
- **Cookie-based database access control** - Extend SecurityGuard to read user cookies and restrict database writes to user-owned data paths
- **API proxy restrictions** - Control which external APIs users can access based on their authentication level
- **Role-based permissions** - Implement arbitrary access rules based on request headers, paths, or content

**Implementation:**
```java
// Custom security rule example
SecurityGuard guard = new SecurityGuard();
guard.rules.add(request -> {
    String cookie = request.headerBlock.headers.get("Cookie");
    String path = request.headerBlock.path;
    // Only allow users to write to their own database namespace
    return path.startsWith("/db/user_" + extractUserId(cookie));
});
```

The SecurityGuard uses a simple predicate-based system where all rules must pass for a request to be allowed. This provides fine-grained control over application security without requiring framework-specific knowledge.

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
# Run basic tests
./java/java.sh Test

# Run ALL tests across the entire project (recommended!)
./java/java.sh buildtools.TestAllClasses
```

**TestAllClasses** is our comprehensive test discovery engine that automatically finds and tests every Java class in the project. It provides detailed reporting, handles missing dependencies gracefully, and returns proper exit codes for CI/CD integration.

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
