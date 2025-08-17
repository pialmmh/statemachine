#!/bin/bash

# Send INCOMING_CALL event to call-001 via WebSocket to trigger RINGING state
echo "Sending INCOMING_CALL event to call-001..."

# Use wscat or node to send WebSocket message
node -e "
const WebSocket = require('ws');
const ws = new WebSocket('ws://localhost:9999');

ws.on('open', () => {
    console.log('Connected to WebSocket');
    
    // Send INCOMING_CALL event
    const message = {
        type: 'EVENT',
        machineId: 'call-001',
        eventType: 'INCOMING_CALL',
        payload: 'test-call'
    };
    
    ws.send(JSON.stringify(message));
    console.log('Sent INCOMING_CALL event');
    
    // Keep connection open to receive updates
    console.log('Waiting for timeout (30 seconds)...');
});

ws.on('message', (data) => {
    const msg = JSON.parse(data);
    if (msg.type === 'STATE_CHANGE') {
        console.log('State change:', msg);
    }
});

ws.on('error', (err) => {
    console.error('WebSocket error:', err);
});
"