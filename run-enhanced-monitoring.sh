#!/bin/bash

# Enhanced Monitoring Server Runner
# This runs the better monitoring UI with table format

echo "🚀 Starting Enhanced State Machine Monitoring Server..."
echo "═══════════════════════════════════════════════════════════"

# Color codes for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if monitoring server is already running
if lsof -Pi :8091 -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo -e "${YELLOW}⚠️  Stopping existing monitoring server on port 8091...${NC}"
    kill $(lsof -Pi :8091 -sTCP:LISTEN -t) 2>/dev/null
    sleep 2
fi

echo -e "${BLUE}📊 Starting Enhanced Monitoring Server...${NC}"
echo "   • Table view with clickable events"
echo "   • Context before/after in columns"
echo "   • Registry status tracking"
echo "   • Step-by-step transition view"
echo

# Run the enhanced monitoring server
java -cp "target/classes:target/test-classes:$(cat classpath.txt)" \
    com.telcobright.statemachine.monitoring.web.EnhancedMonitoringServer &

echo
echo -e "${GREEN}✅ Enhanced Monitoring Server started!${NC}"
echo "📊 Open your browser and go to: http://localhost:8091"
echo
echo "Features:"
echo "  • Click on any event row to see full details"
echo "  • Context before/after shown in columns"
echo "  • Registry status for each transition"
echo "  • Step-by-step view of state changes"
echo
echo "Press Ctrl+C to stop the server"

# Keep script running
wait