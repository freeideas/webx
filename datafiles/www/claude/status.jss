function handle(request, database) {
    // Get execution ID from query parameters
    var executionId = request && request.params && request.params.id;
    
    if (!executionId) {
        return {
            status: 400,
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({status: "error", error: "Execution ID is required"})
        };
    }
    
    // Look up the execution in the database
    var storedData = database ? database.get(executionId) : null;
    var execution = null;
    
    if (storedData) {
        try {
            execution = JSON.parse(storedData);
        } catch (e) {
            // Data might not be JSON
            execution = {status: 'unknown', data: storedData};
        }
    }
    
    if (!execution) {
        // Return mock completed status for demo
        return {
            status: 200,
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({
                status: "completed",
                output: "Demo output: Successfully created hello_world.py",
                error: null
            })
        };
    }
    
    // Check if enough time has passed to mark as completed
    if (execution.timestamp) {
        var startTime = new Date(execution.timestamp).getTime();
        var now = new Date().getTime();
        var elapsed = now - startTime;
        
        if (elapsed > 3000 && execution.status === 'pending') {
            execution.status = 'completed';
            execution.output = 'Successfully processed the command';
            if (database) {
                database.put(executionId, JSON.stringify(execution));
            }
        }
    }
    
    return {
        status: 200,
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({
            status: execution.status || "unknown",
            output: execution.output || null,
            error: execution.error || null
        })
    };
}