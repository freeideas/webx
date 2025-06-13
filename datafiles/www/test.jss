function handle(request) {
    return {
        status: 200,
        headers: {"Content-Type": "text/html"},
        body: `
            <html>
                <body>
                    <h1>Server-Side JavaScript is Working!</h1>
                    <p><strong>Request Method:</strong> ${request.method}</p>
                    <p><strong>Request URL:</strong> ${request.url}</p>
                    <p><strong>Current Time:</strong> ${new Date().toISOString()}</p>
                    <p><strong>Request Headers:</strong></p>
                    <pre>${JSON.stringify(request.headers, null, 2)}</pre>
                    <p><strong>Query Parameters:</strong></p>
                    <pre>${JSON.stringify(request.params, null, 2)}</pre>
                </body>
            </html>
        `
    };
}