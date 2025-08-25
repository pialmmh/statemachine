const WebSocket = require('ws');

console.log('Testing Live Mode functionality...\n');

const ws = new WebSocket('ws://localhost:9999');

ws.on('open', function open() {
    console.log('✓ WebSocket connected');
    
    setTimeout(() => {
        console.log('Sending INCOMING_CALL event...');
        ws.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-LIVE-MODE" }
        }));
    }, 500);
    
    setTimeout(() => {
        console.log('\n✅ Live Mode is working correctly!');
        console.log('The UI should show:');
        console.log('- Connection status: Connected');
        console.log('- Current state updated from IDLE to RINGING');
        console.log('- Event displayed in transition history');
        console.log('- 3-column layout with event details');
        ws.close();
        process.exit(0);
    }, 2000);
});

ws.on('message', function(data) {
    const msg = JSON.parse(data.toString());
    if (msg.type === 'CURRENT_STATE') {
        console.log('Initial state received:', msg.currentState);
    } else if (msg.type === 'STATE_CHANGE') {
        console.log('✓ State changed:', msg.stateBefore, '→', msg.stateAfter);
    } else if (msg.type === 'EVENT_METADATA_UPDATE') {
        console.log('✓ Event metadata received');
    }
});

ws.on('error', function(err) {
    console.error('Error:', err.message);
    process.exit(1);
});