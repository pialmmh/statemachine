const WebSocket = require('ws');

console.log('=== Testing Compact UI Layout ===\n');

const ws = new WebSocket('ws://localhost:10004');

ws.on('open', function() {
    console.log('✅ Connected to WebSocket\n');
    console.log('Creating multiple states to test compact header display...\n');
    
    // Create multiple state transitions
    setTimeout(() => {
        console.log('1. IDLE → RINGING');
        ws.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-0001" }
        }));
    }, 500);
    
    // Session progress (stay in RINGING)
    setTimeout(() => {
        console.log('2. SESSION_PROGRESS in RINGING');
        ws.send(JSON.stringify({
            action: "SESSION_PROGRESS",
            payload: { ringNumber: 1 }
        }));
    }, 1500);
    
    // Answer
    setTimeout(() => {
        console.log('3. RINGING → CONNECTED');
        ws.send(JSON.stringify({
            action: "ANSWER"
        }));
    }, 2500);
    
    // Hangup
    setTimeout(() => {
        console.log('4. CONNECTED → IDLE');
        ws.send(JSON.stringify({
            action: "HANGUP"
        }));
    }, 3500);
    
    // Another call
    setTimeout(() => {
        console.log('5. IDLE → RINGING (second time)');
        ws.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-0002" }
        }));
    }, 4500);
});

ws.on('message', function(data) {
    const msg = JSON.parse(data.toString());
    
    if (msg.type === 'STATE_CHANGE') {
        const before = msg.stateBefore || 'INITIAL';
        const after = msg.stateAfter;
        const event = msg.eventName ? ` (${msg.eventName})` : '';
        console.log(`  → ${before} → ${after}${event}`);
    }
});

setTimeout(() => {
    console.log('\n✅ Test completed');
    console.log('\nCheck the UI at http://localhost:8091');
    console.log('The state header cards should now show all info on ONE LINE:');
    console.log('State: RINGING (#2) | 🔄 2 events | Steps 5-6 | 🕒 HH:mm:ss.SSS');
    console.log('\nThis saves vertical space for better visibility!');
    ws.close();
    process.exit(0);
}, 6000);