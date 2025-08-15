#!/bin/bash

# Script to start both the WebSocket server and React UI

echo "======================================"
echo "State Machine Demo - Starting Services"
echo "======================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to kill process on port
kill_port() {
    local port=$1
    local pid=$(lsof -ti:$port)
    if [ ! -z "$pid" ]; then
        echo -e "${RED}Killing process on port $port (PID: $pid)${NC}"
        kill -9 $pid
        sleep 1
    fi
}

# Clean up ports
echo -e "${BLUE}Cleaning up ports...${NC}"
kill_port 9999
kill_port 4001

# Start WebSocket server
echo -e "${GREEN}Starting WebSocket server on port 9999...${NC}"
cd /home/mustafa/telcobright-projects/statemachine
mvn compile exec:java -Dexec.mainClass="com.telcobright.statemachine.websocket.CallMachineRunnerWithWebServer" -Dexec.args="9999" &
WS_PID=$!

# Wait for WebSocket server to start
echo -e "${BLUE}Waiting for WebSocket server to start...${NC}"
sleep 5

# Start React UI
echo -e "${GREEN}Starting React UI on port 4001...${NC}"
cd /home/mustafa/telcobright-projects/statemachine/statemachine-ui-react
npm start &
UI_PID=$!

echo ""
echo "======================================"
echo -e "${GREEN}Services Started Successfully!${NC}"
echo "======================================"
echo -e "WebSocket Server: ${BLUE}ws://localhost:9999${NC} (PID: $WS_PID)"
echo -e "React UI:         ${BLUE}http://localhost:4001${NC} (PID: $UI_PID)"
echo ""
echo "Press Ctrl+C to stop all services"
echo "======================================"

# Function to cleanup on exit
cleanup() {
    echo ""
    echo -e "${RED}Stopping services...${NC}"
    kill $WS_PID 2>/dev/null
    kill $UI_PID 2>/dev/null
    kill_port 9999
    kill_port 4001
    echo -e "${GREEN}Services stopped.${NC}"
    exit 0
}

# Set up trap to cleanup on Ctrl+C
trap cleanup INT

# Wait for processes
wait