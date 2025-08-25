const WebSocket = require('ws');

console.log('=== Testing WITHOUT Debug Mode (no countdown expected) ===\n');

const ws = new WebSocket('ws://localhost:9998');
let countdownReceived = false;

ws.on('open', function() {
    console.log('✅ Connected to WebSocket');
    
    setTimeout(() => {
        console.log('📞 Triggering RINGING state...\n');
        ws.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-NO-DEBUG" }
        }));
    }, 1000);
});

ws.on('message', function(data) {
    const msg = JSON.parse(data.toString());
    
    if (msg.type === 'TIMEOUT_COUNTDOWN') {
        countdownReceived = true;
        console.log('❌ ERROR: Received countdown message when debug mode is OFF!');
        console.log('   Message:', JSON.stringify(msg));
    } else if (msg.type === 'STATE_CHANGE') {
        console.log(`✅ State change: ${msg.stateBefore} → ${msg.stateAfter}`);
    }
});

// Check after 5 seconds
setTimeout(() => {
    if (!countdownReceived) {
        console.log('\n✅ CORRECT: No countdown messages received (debug mode is OFF)');
    } else {
        console.log('\n❌ FAILED: Countdown messages should not be sent when debug mode is OFF');
    }
    ws.close();
    process.exit(countdownReceived ? 1 : 0);
}, 5000);