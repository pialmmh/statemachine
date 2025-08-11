#!/bin/bash

# State Machine WebSocket Monitor Startup Script
# This script starts both the WebSocket server and the monitoring web UI

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Default ports
WEBSOCKET_PORT=9999
WEBSERVER_PORT=8080

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --ws-port)
            WEBSOCKET_PORT="$2"
            shift 2
            ;;
        --web-port)
            WEBSERVER_PORT="$2"
            shift 2
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --ws-port PORT    WebSocket server port (default: 9999)"
            echo "  --web-port PORT   Web UI server port (default: 8080)"
            echo "  --help           Show this help message"
            echo ""
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}  State Machine Monitoring System${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# Function to check if port is available
check_port() {
    local port=$1
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
        return 1
    else
        return 0
    fi
}

# Function to cleanup on exit
cleanup() {
    echo ""
    echo -e "${YELLOW}Shutting down services...${NC}"
    
    if [ ! -z "$WEBSOCKET_PID" ]; then
        echo "Stopping WebSocket server (PID: $WEBSOCKET_PID)..."
        kill $WEBSOCKET_PID 2>/dev/null
        wait $WEBSOCKET_PID 2>/dev/null
    fi
    
    if [ ! -z "$WEBSERVER_PID" ]; then
        echo "Stopping Web server (PID: $WEBSERVER_PID)..."
        kill $WEBSERVER_PID 2>/dev/null
        wait $WEBSERVER_PID 2>/dev/null
    fi
    
    echo -e "${GREEN}All services stopped.${NC}"
    exit 0
}

# Set up trap for cleanup
trap cleanup EXIT INT TERM

# Check if ports are available
echo "Checking port availability..."

if ! check_port $WEBSOCKET_PORT; then
    echo -e "${RED}Error: WebSocket port $WEBSOCKET_PORT is already in use${NC}"
    exit 1
fi

if ! check_port $WEBSERVER_PORT; then
    echo -e "${RED}Error: Web server port $WEBSERVER_PORT is already in use${NC}"
    exit 1
fi

# Compile the project if needed
echo "Ensuring project is compiled..."
mvn compile -q
if [ $? -ne 0 ]; then
    echo -e "${RED}Error: Maven compilation failed${NC}"
    exit 1
fi

# Start WebSocket server (State Machine Runner)
echo ""
echo -e "${YELLOW}Starting WebSocket Server on port $WEBSOCKET_PORT...${NC}"
mvn exec:java -Dexec.mainClass="com.telcobright.statemachine.websocket.StateMachineRunnerWithWebServer" \
    -Dexec.args="$WEBSOCKET_PORT" -q 2>/dev/null &
WEBSOCKET_PID=$!

# Wait a bit for WebSocket server to start
sleep 3

# Check if WebSocket server started successfully
if ! kill -0 $WEBSOCKET_PID 2>/dev/null; then
    echo -e "${RED}Error: WebSocket server failed to start${NC}"
    exit 1
fi

echo -e "${GREEN}✓ WebSocket server started (PID: $WEBSOCKET_PID)${NC}"

# Start Web UI server
echo ""
echo -e "${YELLOW}Starting Web UI Server on port $WEBSERVER_PORT...${NC}"
mvn exec:java -Dexec.mainClass="com.telcobright.statemachine.websocket.MonitorWebServer" \
    -Dexec.args="$WEBSERVER_PORT" -q 2>/dev/null &
WEBSERVER_PID=$!

# Wait a bit for web server to start
sleep 2

# Check if web server started successfully
if ! kill -0 $WEBSERVER_PID 2>/dev/null; then
    echo -e "${RED}Error: Web UI server failed to start${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Web UI server started (PID: $WEBSERVER_PID)${NC}"

# Display access information
echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}  Services Running Successfully!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo "Access the monitoring UI at:"
echo -e "  ${GREEN}http://localhost:$WEBSERVER_PORT${NC}"
echo ""
echo "WebSocket endpoint:"
echo -e "  ${GREEN}ws://localhost:$WEBSOCKET_PORT${NC}"
echo ""
echo -e "${YELLOW}Press Ctrl+C to stop all services${NC}"
echo ""

# Keep the script running
while true; do
    # Check if processes are still running
    if ! kill -0 $WEBSOCKET_PID 2>/dev/null; then
        echo -e "${RED}WebSocket server has stopped unexpectedly${NC}"
        break
    fi
    
    if ! kill -0 $WEBSERVER_PID 2>/dev/null; then
        echo -e "${RED}Web UI server has stopped unexpectedly${NC}"
        break
    fi
    
    sleep 5
done

cleanup