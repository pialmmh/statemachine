#!/bin/bash

# Demo script for Console State Runner
# Shows a sequence of state machine operations

echo "Starting Console State Runner Demo..."
echo ""
echo "This demo will:"
echo "1. Send INCOMING_CALL"
echo "2. Send ANSWER"
echo "3. Send HANGUP"
echo "4. Exit"
echo ""
echo "Press Enter to continue..."
read

# Create input sequence
cat << EOF | java -cp "target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout)" com.telcobright.statemachine.cli.ConsoleStateRunner 2>/dev/null
r
1
+1-555-1234
r
2
r
3
r
q
EOF

echo ""
echo "Demo completed!"