const WebSocket = require('ws');

const ws = new WebSocket('ws://localhost:9998');

ws.on('open', function open() {
    console.log('Connected to WebSocket server');
    
    // Send INCOMING_CALL
    setTimeout(() => {
        const msg = {
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-1234" }
        };
        ws.send(JSON.stringify(msg));
        console.log('Sent INCOMING_CALL');
    }, 500);
    
    // Send SESSION_PROGRESS after 1.5 seconds
    setTimeout(() => {
        const msg = {
            action: "SESSION_PROGRESS",
            payload: { sdp: "v=0", ringNumber: 1 }
        };
        ws.send(JSON.stringify(msg));
        console.log('Sent SESSION_PROGRESS (ring)');
    }, 1500);
    
    // Send ANSWER after 2.5 seconds  
    setTimeout(() => {
        const msg = { action: "ANSWER" };
        ws.send(JSON.stringify(msg));
        console.log('Sent ANSWER');
    }, 2500);
    
    // Send HANGUP after 3.5 seconds
    setTimeout(() => {
        const msg = { action: "HANGUP" };
        ws.send(JSON.stringify(msg));
        console.log('Sent HANGUP');
    }, 3500);
    
    // Close after 4 seconds
    setTimeout(() => {
        ws.close();
        console.log('Test completed');
        process.exit(0);
    }, 4000);
});

ws.on('message', function message(data) {
    const msg = JSON.parse(data.toString());
    if (msg.type === 'STATE_CHANGE') {
        console.log(`  → State: ${msg.stateBefore || msg.oldState} → ${msg.stateAfter || msg.newState}`);
        if (msg.context) {
            console.log(`    Context: callId=${msg.context.callId}, ringCount=${msg.context.ringCount}`);
        }
    }
});

ws.on('error', function error(err) {
    console.error('WebSocket error:', err);
});