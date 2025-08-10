#!/bin/bash

# TelcoBright State Machine Monitoring Server Startup Script

set -e

echo "🎯 TelcoBright State Machine Monitoring"
echo "======================================"

# Color codes for output
BLUE='\033[0;34m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# Check if port is available
check_port() {
    if lsof -Pi :8091 -sTCP:LISTEN -t >/dev/null 2>&1; then
        print_warning "Port 8091 is already in use"
        echo "You may need to stop the existing service first"
        return 1
    fi
    return 0
}

# Check if classes are compiled
check_classes() {
    if [ ! -f "target/classes/com/telcobright/statemachine/monitoring/web/SimpleMonitoringServer.class" ]; then
        print_status "Compiling project..."
        mvn compile -q || {
            echo "❌ Compilation failed"
            exit 1
        }
    fi
}

# Main function
main() {
    print_status "Starting monitoring server setup..."
    
    # Check prerequisites
    if ! check_port; then
        read -p "Continue anyway? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
    
    # Ensure classes are compiled
    check_classes
    
    print_status "Launching monitoring server on port 8091..."
    
    # Start the monitoring server
    java -cp "target/classes:target/test-classes:$(cat classpath.txt)" \
        com.telcobright.statemachine.monitoring.web.SimpleMonitoringServer &
    
    SERVER_PID=$!
    sleep 2
    
    # Check if server started successfully
    if kill -0 $SERVER_PID 2>/dev/null; then
        print_success "Monitoring server started successfully!"
        echo
        echo "🌐 Access your monitoring dashboard:"
        echo "   URL: http://localhost:8091"
        echo "   Features: State machine transitions, event details, context analysis"
        echo
        echo "🎯 Generate sample data by running:"
        echo "   java -cp \"target/classes:target/test-classes:\$(cat classpath.txt)\" \\"
        echo "       com.telcobright.statemachine.test.MonitoredCallMachineDemo"
        echo
        echo "🔄 Server PID: $SERVER_PID"
        echo "📋 To stop: kill $SERVER_PID"
        
        # Keep script running to show server output
        echo "📊 Server output (Ctrl+C to stop):"
        echo "=================================="
        wait $SERVER_PID
    else
        echo "❌ Failed to start monitoring server"
        exit 1
    fi
}

# Run main function
main "$@"