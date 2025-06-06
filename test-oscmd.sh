#!/bin/bash

# Test script for OS command endpoint

echo "Starting WebX server with OS command endpoint..."

# Start the server in background
./java/java.sh appz.webx.Main \
    --port=13102 \
    --static=www@./datafiles/www \
    --proxy=NONE \
    --db=NONE \
    --login=NONE \
    --oscmd=oscmd@./datafiles/allowed-commands.json \
    --shutdown=SHUTDOWN13102 \
    --run &

SERVER_PID=$!

# Wait for server to start
sleep 2

echo "Testing OS command endpoint..."

# Test 1: Echo command
echo "Test 1: Echo command"
curl -X POST http://localhost:13102/oscmd \
  -H "Content-Type: application/json" \
  -d '{"command":"echo","args":["Hello from OS command handler!"]}'
echo

# Test 2: Date command
echo -e "\nTest 2: Date command"
curl -X POST http://localhost:13102/oscmd \
  -H "Content-Type: application/json" \
  -d '{"command":"date"}'
echo

# Test 3: Invalid command
echo -e "\nTest 3: Invalid command (should fail)"
curl -X POST http://localhost:13102/oscmd \
  -H "Content-Type: application/json" \
  -d '{"command":"rm","args":["-rf","/tmp/test"]}'
echo

# Test 4: Invalid arguments
echo -e "\nTest 4: Invalid arguments for ls (should fail)"
curl -X POST http://localhost:13102/oscmd \
  -H "Content-Type: application/json" \
  -d '{"command":"ls","args":["/etc/passwd"]}'
echo

# Shutdown the server
echo -e "\nShutting down server..."
curl -X POST http://localhost:13102/oscmd \
  -H "Content-Type: text/plain" \
  -d "SHUTDOWN13102"

# Wait for server to shut down
wait $SERVER_PID

echo "Test completed."