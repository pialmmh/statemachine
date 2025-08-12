#!/bin/bash

echo "======================================"
echo "  State Machine Monitoring UI"
echo "======================================"
echo ""

# Default port
WEB_PORT=${1:-8091}

echo "Configuration:"
echo "  Web UI Port: $WEB_PORT"
echo ""

# Kill any existing processes on the port
echo "Cleaning up existing processes..."
lsof -ti:$WEB_PORT 2>/dev/null | xargs -r kill -9 2>/dev/null
sleep 1

# Compile project
echo "Compiling project..."
mvn compile -q
if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

# Start Simple Monitoring Server
echo "Starting Monitoring Server on port $WEB_PORT..."
mvn exec:java -Dexec.mainClass="com.telcobright.statemachine.monitoring.web.SimpleMonitoringServer" -Dexec.args="$WEB_PORT" -q 2>/dev/null &
WEB_PID=$!

sleep 2

echo ""
echo "======================================"
echo "  Monitoring UI Started Successfully!"
echo "======================================"
echo ""
echo "🚀 Open your browser and go to:"
echo "  http://localhost:$WEB_PORT"
echo ""
echo "Features:"
echo "  📸 Snapshot Mode - View historical transitions from database"
echo "  🔴 Live Mode - Connect to WebSocket server (must be started separately)"
echo ""
echo "Note: To use Live Mode, start a StateMachine with registry debug mode enabled"
echo "      or run CallMachineRunnerWithWebServer separately on port 9999"
echo ""
echo "Press Ctrl+C to stop the monitoring UI"
echo ""

# Cleanup function
cleanup() {
    echo ""
    echo "Stopping monitoring UI..."
    kill $WEB_PID 2>/dev/null
    echo "Monitoring UI stopped."
    exit 0
}

trap cleanup INT TERM

# Keep script running
while true; do
    sleep 1
done