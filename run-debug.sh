#!/bin/bash
echo "Starting CallMachineRunnerWithWebServer for debugging..."
echo "Open debug-test.html in your browser to test"
echo ""
mvn exec:java -Dexec.mainClass="com.telcobright.statemachine.statemachinedebugger.CallMachineRunnerWithWebServer" -q