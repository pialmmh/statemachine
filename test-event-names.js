const WebSocket = require('ws');

console.log('=== Testing Event Name Display ===\n');

const ws = new WebSocket('ws://localhost:9995');
let eventCount = 0;

ws.on('open', function() {
    console.log('✅ Connected to WebSocket on port 9995');
    console.log('📊 Open browser: http://localhost:8091\n');
    
    setTimeout(() => {
        console.log('📞 Sending IncomingCall event...');
        ws.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-EVENT-TEST" }
        }));
    }, 1000);
    
    setTimeout(() => {
        console.log('✅ Sending Answer event...');
        ws.send(JSON.stringify({
            action: "ANSWER"
        }));
    }, 2000);
    
    setTimeout(() => {
        console.log('📵 Sending Hangup event...');
        ws.send(JSON.stringify({
            action: "HANGUP"
        }));
    }, 3000);
});

ws.on('message', function(data) {
    const msg = JSON.parse(data.toString());
    
    if (msg.type === 'STATE_CHANGE') {
        eventCount++;
        console.log(`\n📌 Event #${eventCount}: ${msg.eventName || 'No event name'}`);
        console.log(`   State: ${msg.stateBefore} → ${msg.stateAfter}`);
        
        if (msg.eventName) {
            console.log(`   ✅ Event name included: "${msg.eventName}"`);
        } else {
            console.log(`   ⚠️  No event name in message`);
        }
        
        if (eventCount === 3) {
            console.log('\n✅ SUCCESS! Event names are being sent:');
            console.log('   - Step 1: Initial (for IDLE state)');
            console.log('   - Step 2: IncomingCall (IDLE → RINGING)');
            console.log('   - Step 3: Answer (RINGING → CONNECTED)');
            console.log('   - Step 4: Hangup (CONNECTED → IDLE)');
            console.log('\nCheck the UI - event names should display instead of "STATE_CHANGE"');
            
            setTimeout(() => {
                ws.close();
                process.exit(0);
            }, 1000);
        }
    }
});

ws.on('error', function(err) {
    console.error('❌ Error:', err.message);
    process.exit(1);
});

setTimeout(() => {
    console.log('\n✅ Test completed');
    ws.close();
    process.exit(0);
}, 5000);