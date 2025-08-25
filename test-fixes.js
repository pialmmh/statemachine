const WebSocket = require('ws');

console.log('=== Testing Event Names and Countdown Display ===\n');

const ws = new WebSocket('ws://localhost:9997');
let eventCount = 0;
let countdownReceived = false;

ws.on('open', function() {
    console.log('✅ Connected to WebSocket on port 9997');
    console.log('📊 Open browser: http://localhost:8091\n');
    console.log('Check for:');
    console.log('1. Event names (IncomingCall, Answer, Hangup) instead of STATE_CHANGE');
    console.log('2. Countdown in state card header (top right) for RINGING state\n');
    
    setTimeout(() => {
        console.log('📞 Sending IncomingCall event...');
        ws.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-FIX-TEST" }
        }));
    }, 1000);
    
    setTimeout(() => {
        console.log('✅ Sending Answer event...');
        ws.send(JSON.stringify({
            action: "ANSWER"
        }));
    }, 4000);
    
    setTimeout(() => {
        console.log('📵 Sending Hangup event...');
        ws.send(JSON.stringify({
            action: "HANGUP"
        }));
    }, 6000);
});

ws.on('message', function(data) {
    const msg = JSON.parse(data.toString());
    
    if (msg.type === 'STATE_CHANGE') {
        eventCount++;
        console.log(`\n📌 Event #${eventCount}:`);
        console.log(`   State: ${msg.stateBefore || 'INITIAL'} → ${msg.stateAfter}`);
        
        if (msg.eventName) {
            console.log(`   ✅ Event name included: "${msg.eventName}"`);
        } else if (msg.stateBefore === null) {
            console.log(`   ✅ Initial state (no event name expected)`);
        } else {
            console.log(`   ⚠️  No event name in message`);
        }
    } else if (msg.type === 'TIMEOUT_COUNTDOWN' && !countdownReceived) {
        countdownReceived = true;
        console.log('\n⏱️  Countdown timer active:');
        console.log(`   State: ${msg.state}`);
        console.log(`   Remaining: ${msg.remainingSeconds}s`);
        console.log('   ✅ Check UI - countdown should show in state card header');
    }
});

ws.on('error', function(err) {
    console.error('❌ Error:', err.message);
    process.exit(1);
});

setTimeout(() => {
    console.log('\n✅ Test completed');
    console.log('\nExpected results in UI:');
    console.log('- Step 1: Shows "Initial" for IDLE state');
    console.log('- Step 2: Shows "IncomingCall" for IDLE → RINGING');
    console.log('- Step 3: Shows "Answer" for RINGING → CONNECTED');
    console.log('- Step 4: Shows "Hangup" for CONNECTED → IDLE');
    console.log('- Countdown timer appears in RINGING state card header');
    ws.close();
    process.exit(0);
}, 8000);