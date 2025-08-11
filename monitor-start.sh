#!/bin/bash

echo "======================================"
echo "  Enhanced State Machine Monitoring"
echo "======================================"
echo ""

# Default ports
WS_PORT=${1:-9999}
WEB_PORT=${2:-8091}

echo "Configuration:"
echo "  WebSocket Port: $WS_PORT"
echo "  Web UI Port: $WEB_PORT"
echo ""

# Compile project
echo "Compiling project..."
mvn compile -q
if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

# Start WebSocket server
echo "Starting WebSocket server on port $WS_PORT..."
mvn exec:java -Dexec.mainClass="com.telcobright.statemachine.websocket.StateMachineRunnerWithWebServer" -Dexec.args="$WS_PORT" -q 2>/dev/null &
WS_PID=$!

sleep 3

# Start Simple Monitoring Server (the perfect one)
echo "Starting Simple Monitoring Server on port $WEB_PORT..."
mvn exec:java -Dexec.mainClass="com.telcobright.statemachine.monitoring.web.SimpleMonitoringServer" -Dexec.args="$WEB_PORT" -q 2>/dev/null &
WEB_PID=$!

sleep 2

echo ""
echo "======================================"
echo "  Services Started Successfully!"
echo "======================================"
echo ""
echo "🚀 Open your browser and go to:"
echo "  http://localhost:$WEB_PORT"
echo ""
echo "Features:"
echo "  📸 Snapshot Mode - View historical transitions from database"
echo "  🔴 Live Mode - Real-time WebSocket monitoring"
echo ""
echo "WebSocket endpoint:"
echo "  ws://localhost:$WS_PORT"
echo ""
echo "Press Ctrl+C to stop all services"
echo ""

# Cleanup function
cleanup() {
    echo ""
    echo "Stopping services..."
    kill $WS_PID 2>/dev/null
    kill $WEB_PID 2>/dev/null
    echo "Services stopped."
    exit 0
}

trap cleanup INT TERM

# Keep script running
while true; do
    sleep 1
done