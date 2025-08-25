const WebSocket = require('ws');

console.log('=== Testing SESSION_PROGRESS Events ===\n');

const ws = new WebSocket('ws://localhost:10003');

ws.on('open', function() {
    console.log('✅ Connected to WebSocket\n');
    console.log('Test sequence:');
    console.log('1. IDLE → RINGING (IncomingCall)');
    console.log('2. RINGING → RINGING (SessionProgress #1)');
    console.log('3. RINGING → RINGING (SessionProgress #2)');
    console.log('4. RINGING → CONNECTED (Answer)\n');
    
    // Start with incoming call
    setTimeout(() => {
        console.log('📞 Sending IncomingCall');
        ws.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-TEST" }
        }));
    }, 500);
    
    // Send first SESSION_PROGRESS
    setTimeout(() => {
        console.log('📊 Sending SessionProgress #1 (ring 1)');
        ws.send(JSON.stringify({
            action: "SESSION_PROGRESS",
            payload: { ringNumber: 1 }
        }));
    }, 2000);
    
    // Send second SESSION_PROGRESS
    setTimeout(() => {
        console.log('📊 Sending SessionProgress #2 (ring 2)');
        ws.send(JSON.stringify({
            action: "SESSION_PROGRESS",
            payload: { ringNumber: 2 }
        }));
    }, 3500);
    
    // Answer the call
    setTimeout(() => {
        console.log('✅ Sending Answer');
        ws.send(JSON.stringify({
            action: "ANSWER"
        }));
    }, 5000);
    
    // Hangup
    setTimeout(() => {
        console.log('📵 Sending Hangup');
        ws.send(JSON.stringify({
            action: "HANGUP"
        }));
    }, 6500);
});

ws.on('message', function(data) {
    const msg = JSON.parse(data.toString());
    
    if (msg.type === 'STATE_CHANGE') {
        const before = msg.stateBefore || 'INITIAL';
        const after = msg.stateAfter;
        const event = msg.eventName ? ` (${msg.eventName})` : '';
        
        if (before === after) {
            console.log(`  ⚡ Same-state transition: ${before} → ${after}${event}`);
        } else {
            console.log(`  → State change: ${before} → ${after}${event}`);
        }
    }
});

setTimeout(() => {
    console.log('\n✅ Test completed');
    console.log('\nExpected in UI:');
    console.log('- State: RINGING should show 4 events total:');
    console.log('  Step 1: IncomingCall (IDLE → RINGING)');
    console.log('  Step 2: SessionProgress (RINGING → RINGING)');
    console.log('  Step 3: SessionProgress (RINGING → RINGING)');  
    console.log('  Step 4: Answer (RINGING → CONNECTED)');
    ws.close();
    process.exit(0);
}, 8000);