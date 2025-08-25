const WebSocket = require('ws');

console.log('=== Testing Unique State Transitions ===\n');

const ws = new WebSocket('ws://localhost:10000');

ws.on('open', function() {
    console.log('✅ Connected to WebSocket\n');
    console.log('Test sequence: IDLE → RINGING → IDLE → RINGING → CONNECTED → IDLE');
    console.log('Expected: Each state should be shown separately in chronological order\n');
    
    // First RINGING
    setTimeout(() => {
        console.log('📞 Call 1: IDLE → RINGING');
        ws.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-0001" }
        }));
    }, 500);
    
    // Let it timeout (30s) to go back to IDLE
    setTimeout(() => {
        console.log('⏰ Timeout: RINGING → IDLE');
    }, 31000);
    
    // Second RINGING
    setTimeout(() => {
        console.log('📞 Call 2: IDLE → RINGING');
        ws.send(JSON.stringify({
            action: "INCOMING_CALL", 
            payload: { callerNumber: "+1-555-0002" }
        }));
    }, 32000);
    
    // Answer the second call
    setTimeout(() => {
        console.log('✅ Answer: RINGING → CONNECTED');
        ws.send(JSON.stringify({
            action: "ANSWER"
        }));
    }, 34000);
    
    // Hangup
    setTimeout(() => {
        console.log('📵 Hangup: CONNECTED → IDLE');
        ws.send(JSON.stringify({
            action: "HANGUP"
        }));
    }, 36000);
});

ws.on('message', function(data) {
    const msg = JSON.parse(data.toString());
    
    if (msg.type === 'STATE_CHANGE') {
        const before = msg.stateBefore || 'INITIAL';
        const event = msg.eventName ? ` (${msg.eventName})` : '';
        console.log(`  Transition: ${before} → ${msg.stateAfter}${event}`);
    }
});

setTimeout(() => {
    console.log('\n✅ Test completed');
    console.log('\nExpected UI display:');
    console.log('1. State: IDLE');
    console.log('2. State: RINGING (#1) - with IncomingCall event');
    console.log('3. State: IDLE (#2) - after timeout');
    console.log('4. State: RINGING (#2) - with IncomingCall event');
    console.log('5. State: CONNECTED - with Answer event');
    console.log('6. State: IDLE (#3) - with Hangup event');
    console.log('\nEach state should be in its own card, maintaining chronological order.');
    ws.close();
    process.exit(0);
}, 38000);