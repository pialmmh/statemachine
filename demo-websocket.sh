#!/bin/bash

echo "WebSocket State Machine Demo"
echo "============================"
echo ""
echo "1. Start WebSocket Server (port 9999)"
echo "2. Start WebSocket Monitor Client"
echo "3. Start both in separate terminals"
echo ""
echo -n "Choose option [1-3]: "
read option

case $option in
    1)
        echo "Starting WebSocket Server on port 9999..."
        java -cp target/classes com.telcobright.statemachine.websocket.StateMachineRunnerWithWebServer
        ;;
    2)
        echo -n "Enter WebSocket server URL [ws://localhost:9999]: "
        read url
        if [ -z "$url" ]; then
            url="ws://localhost:9999"
        fi
        echo "Connecting to $url..."
        java -cp target/classes com.telcobright.statemachine.cli.WebSocketMonitor $url
        ;;
    3)
        echo "Starting WebSocket Server in background..."
        gnome-terminal --title="WebSocket Server" -- bash -c "java -cp target/classes com.telcobright.statemachine.websocket.StateMachineRunnerWithWebServer; read -p 'Press Enter to close...'"
        
        sleep 2
        
        echo "Starting WebSocket Monitor Client..."
        java -cp target/classes com.telcobright.statemachine.cli.WebSocketMonitor
        ;;
    *)
        echo "Invalid option"
        exit 1
        ;;
esac