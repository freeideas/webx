function handle(request, database) {
    // Simple implementation without complex parsing
    // Generate execution ID
    var executionId = 'exec_' + new Date().getTime();
    
    // Store a pending status
    if (database) {
        database.put(executionId, JSON.stringify({
            status: 'pending',
            timestamp: new Date().toISOString()
        }));
    }
    
    // Return success response
    return {
        status: 200,
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({
            success: true,
            executionId: executionId,
            message: "Command submitted for execution"
        })
    };
}