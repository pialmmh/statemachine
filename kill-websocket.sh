#!/bin/bash

echo "========================================"
echo "  WebSocket Server Cleanup"
echo "========================================"
echo ""

# Default port
PORT=${1:-9999}

echo "Checking for processes on port $PORT..."

# Find and kill processes using the port
PIDS=$(lsof -ti:$PORT 2>/dev/null)

if [ -z "$PIDS" ]; then
    echo "No processes found on port $PORT"
else
    echo "Found process(es): $PIDS"
    echo "Killing processes..."
    kill -9 $PIDS 2>/dev/null
    sleep 1
    
    # Verify they're gone
    REMAINING=$(lsof -ti:$PORT 2>/dev/null)
    if [ -z "$REMAINING" ]; then
        echo "✓ Successfully cleaned up port $PORT"
    else
        echo "⚠ Warning: Some processes may still be running on port $PORT"
        echo "You may need to run this script with sudo"
    fi
fi

echo ""
echo "Port $PORT is now available for use."