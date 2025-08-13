const WebSocket = require('ws');

console.log('Testing WebSocket connection to port 9999...');
const ws = new WebSocket('ws://localhost:9999');

ws.on('open', function open() {
    console.log('✓ Connected successfully');
    
    // Request event metadata
    ws.send(JSON.stringify({ action: 'GET_EVENT_METADATA' }));
    console.log('Requested event metadata');
    
    // Send a test event after 1 second
    setTimeout(() => {
        console.log('Sending INCOMING_CALL...');
        ws.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-TEST" }
        }));
    }, 1000);
    
    // Close after 2 seconds
    setTimeout(() => {
        console.log('✓ Test completed');
        ws.close();
        process.exit(0);
    }, 2000);
});

ws.on('message', function message(data) {
    const msg = JSON.parse(data.toString());
    console.log('Received:', msg.type);
    if (msg.type === 'CURRENT_STATE') {
        console.log('  Current state:', msg.currentState);
    } else if (msg.type === 'STATE_CHANGE') {
        console.log('  State change:', msg.stateBefore, '→', msg.stateAfter);
    } else if (msg.type === 'EVENT_METADATA_UPDATE') {
        console.log('  Got metadata with', msg.machines[0].supportedEvents.length, 'events');
    }
});

ws.on('error', function(err) {
    console.error('✗ Connection failed:', err.message);
    process.exit(1);
});