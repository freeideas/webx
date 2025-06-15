# Claude Code Web Interface Design

## Overview
A web application that provides a browser-based interface for sending commands to Claude Code CLI. The app handles authentication via email login and manages long-running commands through asynchronous execution and polling.

## Architecture

### Configuration

**config.json** - Application configuration file
- `username` - The system user Claude commands will run as
- `workingDirectory` - Directory where Claude commands will be executed
- `environment` - Environment variables to set for Claude processes
- `commandTimeout` - Maximum time (ms) a command can run before being killed (default: 30 minutes)
- `logRetentionHours` - How long to keep log files before cleanup (default: 24 hours)
- `maxOutputSize` - Maximum size (bytes) of command output to capture (default: 10MB)

### Components

1. **index.html** - Authentication gateway
   - Checks for presence of authentication cookie (simple check)
   - Shows login form if no auth cookie found
   - Redirects to app.html if auth cookie exists
   - Handles email-based login flow with /login endpoint

2. **app.html** - Command interface
   - Validates authentication on load (redirects to index.html if not)
   - Shows command input textarea (5 rows, 90% width)
   - Submits commands via AJAX POST to execute.jss
   - Polls status.jss for command results
   - Displays output and status updates

3. **execute.jss** - Command executor
   - Receives POST requests with command text
   - Validates user authentication (server-side deep check)
   - Creates unique execution ID
   - Writes request file to log directory: `claude_request_<id>.json`
   - Spawns claude command as background process
   - Returns immediately with execution ID

4. **status.jss** - Status checker
   - Receives GET requests with execution ID
   - Checks for existence of response file: `claude_response_<id>.json`
   - If response file exists, reads and returns the output
   - If no response file yet, returns "running" status

5. **output.jss** - Output retriever (optional, functionality can be merged into status.jss)
   - Receives GET requests with execution ID
   - Reads the response file: `claude_response_<id>.json`
   - Returns the full output of completed commands

## Security Features

- Email-based authentication using WebX's built-in /login endpoint
- Session validation on every request
- Command execution restricted to authenticated users only
- Process isolation with unique execution IDs

## Data Flow

1. User visits index.html
2. If not authenticated, user logs in via email verification
3. Upon successful login, redirected to app.html
4. User enters command in textarea (5 rows, 90% width)
5. JavaScript submits command to execute.jss
6. execute.jss:
   - Validates authentication (server-side deep check)
   - Creates unique execution ID
   - Writes request file: `log/claude_request_<id>.json`
   - Spawns: `claude --dangerously-skip-permissions -c -p "<command>"`
   - Redirects command output to: `log/claude_response_<id>.json`
   - Returns execution ID immediately
7. JavaScript polls status.jss with execution ID
8. status.jss:
   - Checks for `log/claude_response_<id>.json`
   - If found: returns status "completed" with output
   - If not found: returns status "running"
9. When response file exists, output is displayed to user

## Log File Format

### Request File: `log/claude_request_<id>.json`
```json
{
  "id": "exec_1234567890",
  "command": "user command text",
  "user": "user@example.com",
  "timestamp": "2024-01-01T12:00:00Z",
  "userAgent": "Mozilla/5.0..."
}
```

### Response File: `log/claude_response_<id>.json`
```json
{
  "id": "exec_1234567890",
  "status": "completed|error",
  "output": "Claude command output...",
  "exitCode": 0,
  "startTime": "2024-01-01T12:00:00Z",
  "endTime": "2024-01-01T12:05:00Z",
  "error": "error message if any"
}
```

## UI Features

- Clean, responsive design
- Real-time status updates during execution
- Command history (stored in localStorage)
- Output syntax highlighting
- Copy-to-clipboard for results
- Loading spinner during execution
- Error handling with user-friendly messages

## Implementation Notes

- Use ProcessBuilder for spawning claude commands
- Redirect command output directly to response log files
- Simple file-based coordination (no database needed)
- Response readiness determined by file existence
- Implement timeout handling (default: 30 minutes)
- Clean up old log files periodically (e.g., older than 24 hours)
- Handle browser tab closure gracefully