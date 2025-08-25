const WebSocket = require('ws');

const ws = new WebSocket('ws://localhost:9998');

ws.on('open', function open() {
    console.log('Connected to WebSocket server');
    console.log('Waiting for initial state display...');
    
    // Just connect and wait to see the initial state
    // Then send one event after 2 seconds to see the transition
    setTimeout(() => {
        const msg = {
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-9876" }
        };
        ws.send(JSON.stringify(msg));
        console.log('Sent INCOMING_CALL');
    }, 2000);
    
    // Close after 3 seconds
    setTimeout(() => {
        ws.close();
        console.log('Connection closed');
        process.exit(0);
    }, 3000);
});

ws.on('message', function message(data) {
    const msg = JSON.parse(data.toString());
    if (msg.type === 'CURRENT_STATE') {
        console.log('=== INITIAL STATE RECEIVED ===');
        console.log('Current State:', msg.currentState);
        if (msg.context) {
            console.log('Context:', JSON.stringify(msg.context, null, 2));
        }
    } else if (msg.type === 'STATE_CHANGE') {
        console.log('=== STATE CHANGE ===');
        console.log(`State: ${msg.stateBefore || msg.oldState} → ${msg.stateAfter || msg.newState}`);
    }
});

ws.on('error', function error(err) {
    console.error('WebSocket error:', err);
});