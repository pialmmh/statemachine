#!/bin/bash

# Interactive CallMachine Demo Runner

echo "🎯 TelcoBright Interactive CallMachine Demo"
echo "=========================================="

# Color codes for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}📱 This demo allows you to manually control a CallMachine:${NC}"
echo "   • Press Enter to send events"
echo "   • Wait 30 seconds for automatic timeout"
echo "   • Watch live countdown in console"
echo "   • View monitoring at http://localhost:8091"
echo

# Check if monitoring server is running
if lsof -Pi :8091 -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo -e "${GREEN}✅ Monitoring server is running on port 8091${NC}"
else
    echo -e "${YELLOW}⚠️  Starting monitoring server...${NC}"
    java -cp "target/classes:target/test-classes:$(cat classpath.txt)" \
        com.telcobright.statemachine.monitoring.web.SimpleMonitoringServer &
    echo "🚀 Monitoring server started in background"
    sleep 2
fi

echo
echo -e "${BLUE}🎮 Starting interactive CallMachine demo...${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo

# Run the interactive demo
java -cp "target/classes:target/test-classes:$(cat classpath.txt)" \
    com.telcobright.statemachine.test.InteractiveCallMachineDemo

echo
echo -e "${GREEN}✅ Interactive demo completed!${NC}"
echo "📊 View monitoring history at: http://localhost:8091"