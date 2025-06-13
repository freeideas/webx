# WebX Server-Side JavaScript (.jss) - The Full Power Guide

WebX's `.jss` files transform ordinary JavaScript into a server-side powerhouse with **full access to the Java ecosystem**. Think of .jss files as JavaScript with superpowers - they can do everything a Java application can do, plus they have built-in access to WebX's persistent database.

## What Makes .jss Special?

Unlike traditional server-side JavaScript environments that sandbox your code, WebX .jss files have **unrestricted access** to:

- **The entire Java standard library** - File I/O, networking, threading, cryptography, etc.
- **All WebX libraries** - Database persistence, HTTP utilities, JSON processing, email, etc.
- **Any JAR dependencies** - Load and use any Java library via Class.forName()
- **System operations** - Execute shell commands, access environment variables, manipulate processes
- **WebX's persistent database** - Shared with the `/db` endpoint for seamless data integration
- **Full Java reflection capabilities** - Create instances, invoke methods, access fields dynamically
- **Zero sandboxing** - Complete freedom to use any Java API or system resource

This means a single .jss file can be a complete web application - handling HTTP requests, processing data, calling external APIs, managing files, sending emails, and persisting state. **WebX .jss files are as powerful as any Java application** - they're essentially JavaScript syntax running with full JVM privileges.

🎯 **Live Demo**: Visit `/demo-java-access.jss` when your WebX server is running to see interactive examples of Java integration, file operations, networking, and reflection - all from a single .jss file!

## The .jss File Structure

Every .jss file must contain a `handle()` function that processes HTTP requests:

```javascript
function handle(request, database) {
    // Your server-side logic here
    return {
        status: 200,
        headers: {"Content-Type": "text/html"},
        body: "<h1>Hello from the server!</h1>"
    };
}
```

### Function Parameters

**1. `request` object** - Complete HTTP request information:
- `request.method` - HTTP method (GET, POST, PUT, DELETE, etc.)
- `request.url` - Full request URL including query parameters
- `request.headers` - Object containing all HTTP headers
- `request.body` - Raw request body content (string)
- `request.parsedBody` - Automatically parsed body (JSON objects, form data, etc.)
- `request.params` - Combined parameters from cookies, body, and query string

**2. `database` object** - WebX's persistent database (shared with `/db` endpoint):
- `database.get(key)` - Retrieve a value by key
- `database.put(key, value)` - Store a value (supports nested objects/arrays)
- `database.containsKey(key)` - Check if a key exists
- `database.remove(key)` - Delete a key and its value
- `database.size()` - Get number of top-level keys
- `database.clear()` - Remove all data

### Response Object

The `handle()` function must return an object with:
- `status` - HTTP status code (200, 404, 500, etc.)
- `headers` - Object with response headers
- `body` - Response content (string or bytes)

## Java Integration - The Real Power

WebX .jss files have complete access to Java through `Class.forName()` and built-in class bindings. WebX automatically provides access to:

- `Class` - For loading any Java class via `Class.forName('fully.qualified.ClassName')`
- `System` - Direct access to `java.lang.System` for properties, environment, etc.
- `String`, `Integer`, `Long`, `Double` - Common Java wrapper types for convenience

This means you can load and use any Java class in your classpath:

### Basic Java Class Access

```javascript
function handle(request, database) {
    // Load Java classes using built-in Class access
    var FileClass = Class.forName('java.io.File');
    var DateClass = Class.forName('java.util.Date');
    
    // Use Java classes directly (note: use java.lang.System, not System directly)
    var tmpDir = java.lang.System.getProperty('java.io.tmpdir');
    var javaVersion = java.lang.System.getProperty('java.version');
    var now = new DateClass();
    
    return {
        status: 200,
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({
            timestamp: now.toString(),
            tmpDir: tmpDir,
            javaVersion: javaVersion,
            availableProcessors: java.lang.Runtime.getRuntime().availableProcessors()
        })
    };
}
```

### File System Operations

```javascript
function handle(request, database) {
    var File = Class.forName('java.io.File');
    var FileWriter = Class.forName('java.io.FileWriter');
    var Lib = Class.forName('jLib.Lib');
    
    if (request.method === 'POST') {
        // Create and write to a file
        var content = request.parsedBody.content || 'Default content';
        var filename = request.parsedBody.filename || 'test.txt';
        
        var tmpDir = Lib.getMethod('tmpDir').invoke(null);
        var file = new File(tmpDir, filename);
        var writer = new FileWriter(file);
        writer.write(content + '\nCreated at: ' + new Date().toISOString());
        writer.close();
        
        return {
            status: 201,
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({
                success: true,
                path: file.getAbsolutePath(),
                size: file.length()
            })
        };
    }
    
    if (request.method === 'GET') {
        // List files in temp directory
        var tmpDir = Lib.getMethod('tmpDir').invoke(null);
        var dir = new File(tmpDir);
        var files = dir.listFiles();
        var fileList = [];
        
        for (var i = 0; i < files.length; i++) {
            if (files[i].isFile()) {
                fileList.push({
                    name: files[i].getName(),
                    size: files[i].length(),
                    modified: new Date(files[i].lastModified()).toISOString()
                });
            }
        }
        
        return {
            status: 200,
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({
                directory: tmpDir,
                files: fileList,
                count: fileList.length
            })
        };
    }
    
    return {status: 405, body: "Method not allowed"};
}
```

### Network Operations & External APIs

```javascript
function handle(request, database) {
    var URL = Class.forName('java.net.URL');
    var HttpURLConnection = Class.forName('java.net.HttpURLConnection');
    var BufferedReader = Class.forName('java.io.BufferedReader');
    var InputStreamReader = Class.forName('java.io.InputStreamReader');
    
    var targetUrl = request.params.url || 'https://httpbin.org/json';
    
    try {
        var url = new URL(targetUrl);
        var connection = url.openConnection();
        connection.setRequestMethod('GET');
        connection.setRequestProperty('User-Agent', 'WebX-JSS/1.0');
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        
        var responseCode = connection.getResponseCode();
        var reader = new BufferedReader(
            new InputStreamReader(connection.getInputStream())
        );
        
        var response = '';
        var line;
        while ((line = reader.readLine()) !== null) {
            response += line;
        }
        reader.close();
        
        // Cache the response in the database
        var cacheKey = 'api_cache_' + encodeURIComponent(targetUrl);
        database.put(cacheKey, {
            url: targetUrl,
            response: response,
            cached: new Date().toISOString(),
            statusCode: responseCode
        });
        
        return {
            status: 200,
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({
                url: targetUrl,
                statusCode: responseCode,
                data: JSON.parse(response),
                cached: true
            })
        };
    } catch (e) {
        return {
            status: 500,
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({
                error: e.toString(),
                url: targetUrl
            })
        };
    }
}
```

### System Command Execution

```javascript
function handle(request, database) {
    var ProcessBuilder = Class.forName('java.lang.ProcessBuilder');
    var BufferedReader = Class.forName('java.io.BufferedReader');
    var InputStreamReader = Class.forName('java.io.InputStreamReader');
    
    var command = request.params.cmd || 'echo "Hello from system"';
    
    try {
        // Security check - only allow certain commands
        var allowedCommands = ['ls', 'echo', 'date', 'whoami', 'pwd'];
        var cmdParts = command.split(' ');
        var baseCmd = cmdParts[0];
        
        if (allowedCommands.indexOf(baseCmd) === -1) {
            return {
                status: 403,
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({
                    error: "Command not allowed: " + baseCmd,
                    allowed: allowedCommands
                })
            };
        }
        
        var pb = new ProcessBuilder(command.split(' '));
        pb.redirectErrorStream(true);
        var process = pb.start();
        
        var reader = new BufferedReader(
            new InputStreamReader(process.getInputStream())
        );
        
        var output = '';
        var line;
        while ((line = reader.readLine()) !== null) {
            output += line + '\n';
        }
        reader.close();
        
        var exitCode = process.waitFor();
        
        // Log command execution
        var executionLog = database.get('command_log') || [];
        executionLog.push({
            command: command,
            timestamp: new Date().toISOString(),
            exitCode: exitCode,
            userAgent: request.headers['User-Agent'] || 'Unknown'
        });
        database.put('command_log', executionLog);
        
        return {
            status: 200,
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({
                command: command,
                output: output.trim(),
                exitCode: exitCode,
                timestamp: new Date().toISOString()
            })
        };
    } catch (e) {
        return {
            status: 500,
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({
                error: e.toString(),
                command: command
            })
        };
    }
}
```

### Advanced Database Operations

```javascript
function handle(request, database) {
    // Access WebX's persistent data classes directly
    var PersistentData = Class.forName('persist.PersistentData');
    var Lib = Class.forName('jLib.Lib');
    
    if (request.method === 'POST') {
        // Create a separate database for this application
        var appDb = PersistentData.getMethod('temp', Class.forName('java.lang.String'))
                                  .invoke(null, 'app_specific_db');
        var appMap = appDb.getRootMap();
        
        // Store complex nested data
        var userData = request.parsedBody;
        var userId = 'user_' + new Date().getTime();
        
        var userRecord = {
            id: userId,
            data: userData,
            created: new Date().toISOString(),
            sessionId: request.headers['X-Session-ID'] || 'anonymous',
            metadata: {
                userAgent: request.headers['User-Agent'],
                referer: request.headers['Referer'],
                ip: request.headers['X-Forwarded-For'] || 'unknown'
            }
        };
        
        appMap.put(userId, userRecord);
        
        // Also track in main database
        var allUsers = database.get('all_users') || [];
        allUsers.push(userId);
        database.put('all_users', allUsers);
        database.put('last_user_created', userId);
        
        return {
            status: 201,
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({
                success: true,
                userId: userId,
                totalUsers: allUsers.length
            })
        };
    }
    
    if (request.method === 'GET') {
        // Comprehensive user analytics
        var allUsers = database.get('all_users') || [];
        var stats = {
            totalUsers: allUsers.length,
            lastCreated: database.get('last_user_created'),
            databaseSize: database.size(),
            serverInfo: {
                javaVersion: System.getProperty('java.version'),
                osName: System.getProperty('os.name'),
                timestamp: new Date().toISOString(),
                uptime: System.currentTimeMillis()
            }
        };
        
        return {
            status: 200,
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(stats)
        };
    }
    
    return {status: 405, body: "Method not allowed"};
}
```

### Email Integration

```javascript
function handle(request, database) {
    var Email = Class.forName('jLib.Email');
    
    if (request.method === 'POST') {
        var emailData = request.parsedBody;
        
        if (!emailData.to || !emailData.subject || !emailData.body) {
            return {
                status: 400,
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({
                    error: "Missing required fields: to, subject, body"
                })
            };
        }
        
        try {
            // Use WebX's built-in email functionality
            var result = Email.getMethod('send', 
                Class.forName('java.lang.String'),
                Class.forName('java.lang.String'),
                Class.forName('java.lang.String'),
                Class.forName('java.lang.String')
            ).invoke(null, 
                'your-smtp-server.com',
                emailData.to,
                emailData.subject,
                emailData.body
            );
            
            // Log email sending
            var emailLog = database.get('email_log') || [];
            emailLog.push({
                to: emailData.to,
                subject: emailData.subject,
                timestamp: new Date().toISOString(),
                success: true
            });
            database.put('email_log', emailLog);
            
            return {
                status: 200,
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({
                    success: true,
                    message: "Email sent successfully",
                    timestamp: new Date().toISOString()
                })
            };
        } catch (e) {
            return {
                status: 500,
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({
                    error: "Failed to send email: " + e.toString()
                })
            };
        }
    }
    
    if (request.method === 'GET') {
        // Return email sending statistics
        var emailLog = database.get('email_log') || [];
        return {
            status: 200,
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({
                totalEmailsSent: emailLog.length,
                recentEmails: emailLog.slice(-10)
            })
        };
    }
    
    return {status: 405, body: "Method not allowed"};
}
```

## Complete Web Application Example

Here's a complete single-file web application using .jss:

```javascript
// blog.jss - A complete blog system in one file
function handle(request, database) {
    var path = request.url.split('?')[0];
    var method = request.method;
    
    // Initialize blog data structure
    if (!database.containsKey('blog_posts')) {
        database.put('blog_posts', []);
        database.put('blog_settings', {
            title: 'My WebX Blog',
            description: 'A blog powered by WebX .jss',
            created: new Date().toISOString()
        });
    }
    
    if (method === 'GET') {
        return renderBlogHTML(database);
    }
    
    if (method === 'POST') {
        return handleBlogPost(request, database);
    }
    
    return {status: 405, body: "Method not allowed"};
}

function renderBlogHTML(database) {
    var posts = database.get('blog_posts') || [];
    var settings = database.get('blog_settings') || {};
    
    var html = `
    <!DOCTYPE html>
    <html>
    <head>
        <title>${settings.title}</title>
        <style>
            body { font-family: Arial, sans-serif; max-width: 800px; margin: 0 auto; padding: 20px; }
            .post { border-bottom: 1px solid #eee; margin-bottom: 30px; padding-bottom: 20px; }
            .post-form { background: #f5f5f5; padding: 20px; margin-bottom: 30px; }
            .post-form input, .post-form textarea { width: 100%; margin-bottom: 10px; padding: 8px; }
            .post-meta { color: #666; font-size: 0.9em; margin-bottom: 10px; }
        </style>
    </head>
    <body>
        <h1>${settings.title}</h1>
        <p>${settings.description}</p>
        
        <div class="post-form">
            <h3>Write a new post</h3>
            <form method="POST">
                <input type="text" name="title" placeholder="Post title" required>
                <input type="text" name="author" placeholder="Your name" required>
                <textarea name="content" rows="6" placeholder="Post content" required></textarea>
                <button type="submit">Publish Post</button>
            </form>
        </div>
        
        <div class="posts">
            ${posts.map(post => `
                <div class="post">
                    <h2>${post.title}</h2>
                    <div class="post-meta">By ${post.author} on ${new Date(post.created).toLocaleDateString()}</div>
                    <div>${post.content.replace(/\n/g, '<br>')}</div>
                </div>
            `).join('')}
        </div>
        
        <footer>
            <p>Total posts: ${posts.length} | Blog created: ${new Date(settings.created).toLocaleDateString()}</p>
        </footer>
    </body>
    </html>`;
    
    return {
        status: 200,
        headers: {"Content-Type": "text/html"},
        body: html
    };
}

function handleBlogPost(request, database) {
    var formData = request.parsedBody;
    
    if (!formData.title || !formData.author || !formData.content) {
        return {
            status: 400,
            headers: {"Content-Type": "text/html"},
            body: "<h1>Error</h1><p>All fields are required!</p><a href='javascript:history.back()'>Go back</a>"
        };
    }
    
    var posts = database.get('blog_posts') || [];
    var newPost = {
        id: 'post_' + new Date().getTime(),
        title: formData.title,
        author: formData.author,
        content: formData.content,
        created: new Date().toISOString(),
        userAgent: request.headers['User-Agent'] || 'Unknown'
    };
    
    posts.unshift(newPost); // Add to beginning
    database.put('blog_posts', posts);
    
    // Redirect back to blog
    return {
        status: 302,
        headers: {
            "Location": request.url.split('?')[0],
            "Content-Type": "text/html"
        },
        body: "<h1>Post published!</h1><p>Redirecting...</p>"
    };
}
```

## Best Practices

### Security Considerations
- Always validate user input, especially when executing system commands
- Use whitelists for allowed operations rather than blacklists
- Be careful with file system access - validate paths to prevent directory traversal
- Consider implementing rate limiting for resource-intensive operations

### Performance Tips
- Cache expensive operations in the database
- Use early returns to avoid unnecessary processing
- Be mindful of memory usage when processing large files
- Consider using Java's concurrent utilities for parallel processing

### Error Handling
```javascript
function handle(request, database) {
    try {
        // Your main logic here
        return successResponse;
    } catch (e) {
        // Log the error
        var errorLog = database.get('error_log') || [];
        errorLog.push({
            error: e.toString(),
            timestamp: new Date().toISOString(),
            request: {
                method: request.method,
                url: request.url,
                userAgent: request.headers['User-Agent']
            }
        });
        database.put('error_log', errorLog);
        
        return {
            status: 500,
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({
                error: "Internal server error",
                id: errorLog.length // Error ID for tracking
            })
        };
    }
}
```

## Conclusion

WebX .jss files represent a unique approach to server-side development - they provide the simplicity and familiarity of JavaScript with the full power and ecosystem of Java. This combination enables rapid development of sophisticated web applications without the complexity of traditional enterprise frameworks.

Whether you're building a simple API endpoint, a complex web application, or integrating with external systems, .jss files give you the tools to accomplish your goals with minimal setup and maximum flexibility.