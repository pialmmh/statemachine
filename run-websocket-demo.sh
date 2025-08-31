#!/bin/bash

echo "WebSocket State Machine Demo"
echo "============================"
echo ""
echo "1. Start WebSocket Server (port 9999)"
echo "2. Start WebSocket Monitor Client"
echo "3. Run demo (server + client test)"
echo ""
echo -n "Choose option [1-3]: "
read option

case $option in
    1)
        echo "Starting WebSocket Server on port 9999..."
        mvn exec:java -Dexec.mainClass="com.telcobright.statemachine.statemachinedebugger.StateMachineRunnerWithWebServer"
        ;;
    2)
        echo -n "Enter WebSocket server URL [ws://localhost:9999]: "
        read url
        if [ -z "$url" ]; then
            url="ws://localhost:9999"
        fi
        echo "Connecting to $url..."
        mvn exec:java -Dexec.mainClass="com.telcobright.statemachine.cli.WebSocketMonitor" -Dexec.args="$url"
        ;;
    3)
        echo "Starting demo..."
        echo "Starting WebSocket Server in background..."
        mvn exec:java -Dexec.mainClass="com.telcobright.statemachine.statemachinedebugger.StateMachineRunnerWithWebServer" -q &
        SERVER_PID=$!
        
        echo "Waiting for server to start..."
        sleep 5
        
        echo "Starting Monitor Client..."
        mvn exec:java -Dexec.mainClass="com.telcobright.statemachine.cli.WebSocketMonitor" -Dexec.args="ws://localhost:9999"
        
        echo "Stopping server..."
        kill $SERVER_PID 2>/dev/null
        ;;
    *)
        echo "Invalid option"
        exit 1
        ;;
esac