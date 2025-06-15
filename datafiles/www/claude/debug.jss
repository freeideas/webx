function handle(request, database) {
    // Debug: Check what we receive
    var response = {
        status: 200,
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({
            requestType: typeof request,
            requestKeys: request ? Object.keys(request) : null,
            method: request ? request.method : null,
            hasBody: request ? (request.body ? true : false) : null,
            bodyType: request && request.body ? typeof request.body : null,
            bodyLength: request && request.body ? request.body.length : null,
            bodyContent: request && request.body ? request.body.substring(0, 100) : null
        }, null, 2)
    };
    return response;
}