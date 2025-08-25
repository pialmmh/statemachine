const WebSocket = require('ws');

console.log('=== Testing Hardcoded Debug Mode ===\n');

const ws = new WebSocket('ws://localhost:9998');
let countdownReceived = false;

ws.on('open', function() {
    console.log('✅ Connected to WebSocket');
    
    setTimeout(() => {
        console.log('📞 Sending INCOMING_CALL to trigger RINGING state...\n');
        ws.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-HARDCODED" }
        }));
    }, 1000);
});

ws.on('message', function(data) {
    const msg = JSON.parse(data.toString());
    
    if (msg.type === 'TIMEOUT_COUNTDOWN') {
        if (!countdownReceived) {
            console.log('✅ COUNTDOWN MESSAGES WORKING!');
            countdownReceived = true;
        }
        console.log(`⏱️  Countdown: ${msg.remainingSeconds}s (Debug: ${msg.debugMode})`);
        
        if (msg.remainingSeconds === 27) {
            console.log('\n✅ Test successful - countdown is working with hardcoded debug mode');
            ws.close();
            process.exit(0);
        }
    } else if (msg.type === 'STATE_CHANGE') {
        console.log(`State: ${msg.stateBefore} → ${msg.stateAfter}`);
    }
});

ws.on('error', function(err) {
    console.error('❌ Error:', err.message);
    process.exit(1);
});

setTimeout(() => {
    if (!countdownReceived) {
        console.log('\n❌ No countdown messages received!');
        console.log('Check server logs for debug mode status.');
    }
    ws.close();
    process.exit(countdownReceived ? 0 : 1);
}, 8000);