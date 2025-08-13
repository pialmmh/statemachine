const WebSocket = require('ws');

console.log('=== Testing timeout transition notification ===\n');

const ws = new WebSocket('ws://localhost:9998');
let messageCount = 0;

ws.on('open', function() {
    console.log('Connected to WebSocket server');
    
    // Send INCOMING_CALL to trigger RINGING state
    setTimeout(() => {
        console.log('Sending INCOMING_CALL to trigger RINGING state...');
        ws.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-TIMEOUT-UI-TEST" }
        }));
    }, 500);
});

ws.on('message', function(data) {
    messageCount++;
    const msg = JSON.parse(data.toString());
    console.log(`\nMessage #${messageCount}:`, JSON.stringify(msg, null, 2));
    
    if (msg.type === 'STATE_CHANGE') {
        if (msg.stateAfter === 'RINGING') {
            console.log('\n✅ Transitioned to RINGING state');
            console.log('⏳ Waiting for 30-second timeout...');
            console.log('   (The UI should receive another STATE_CHANGE when timeout occurs)');
        } else if (msg.stateBefore === 'RINGING' && msg.stateAfter === 'IDLE') {
            console.log('\n✅ TIMEOUT MESSAGE RECEIVED!');
            console.log('   The WebSocket correctly sent the timeout transition');
            console.log('   If UI is not updating, the issue is in the JavaScript handler');
            ws.close();
            process.exit(0);
        }
    }
});

ws.on('error', function(err) {
    console.error('Connection error:', err.message);
    process.exit(1);
});

// Timeout after 35 seconds
setTimeout(() => {
    console.log('\n❌ No timeout message received after 35 seconds');
    console.log('   The WebSocket is NOT sending the timeout transition');
    ws.close();
    process.exit(1);
}, 35000);