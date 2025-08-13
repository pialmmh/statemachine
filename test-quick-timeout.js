const WebSocket = require('ws');

console.log('=== Quick Test: Countdown Timer After Timeout ===\n');

const ws = new WebSocket('ws://localhost:9998');
let ringingCount = 0;

ws.on('open', function() {
    console.log('✅ Connected to WebSocket\n');
    
    // First RINGING
    setTimeout(() => {
        console.log('📞 Call 1: IDLE → RINGING');
        ws.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-CALL1" }
        }));
    }, 500);
    
    // Second RINGING after letting first one timeout
    setTimeout(() => {
        console.log('\n📞 Call 2: IDLE → RINGING (after timeout)');
        ws.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-CALL2" }
        }));
    }, 32000); // After timeout (30s + 2s buffer)
});

ws.on('message', function(data) {
    const msg = JSON.parse(data.toString());
    
    if (msg.type === 'STATE_CHANGE') {
        console.log(`State: ${msg.stateBefore || 'INITIAL'} → ${msg.stateAfter} ${msg.eventName ? '(' + msg.eventName + ')' : ''}`);
        
        if (msg.stateAfter === 'RINGING') {
            ringingCount++;
            console.log(`  [RINGING #${ringingCount}]`);
        }
    } else if (msg.type === 'TIMEOUT_COUNTDOWN' && msg.remainingSeconds === 30) {
        console.log(`  ✅ Countdown started: ${msg.remainingSeconds}s`);
    } else if (msg.type === 'TIMEOUT_COUNTDOWN' && msg.remainingSeconds === 1) {
        console.log(`  ⏰ Countdown ending: ${msg.remainingSeconds}s`);
    }
});

setTimeout(() => {
    console.log(`\n📊 Result: Got ${ringingCount} RINGING states`);
    if (ringingCount === 2) {
        console.log('✅ Both RINGING states occurred');
    } else {
        console.log('⚠️  Expected 2 RINGING states');
    }
    ws.close();
    process.exit(0);
}, 35000);