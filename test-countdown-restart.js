const WebSocket = require('ws');

console.log('=== Testing Countdown Timer Restart After Timeout ===\n');

const ws = new WebSocket('ws://localhost:9998');
let countdownMessages = {};

ws.on('open', function() {
    console.log('✅ Connected to WebSocket on port 9998');
    console.log('📊 Open browser: http://localhost:8091\n');
    
    console.log('Test sequence:');
    console.log('1. IDLE → RINGING (first time, should show countdown)');
    console.log('2. Wait for timeout: RINGING → IDLE');
    console.log('3. IDLE → RINGING (second time, should show countdown again)\n');
    
    setTimeout(() => {
        console.log('📞 First IncomingCall: IDLE → RINGING');
        ws.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-FIRST" }
        }));
    }, 1000);
    
    // Wait for timeout (simulated by waiting 3 seconds)
    setTimeout(() => {
        console.log('\n⏰ Waiting for timeout to occur...');
    }, 3000);
    
    // Send second incoming call after timeout
    setTimeout(() => {
        console.log('\n📞 Second IncomingCall: IDLE → RINGING (after timeout)');
        ws.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-SECOND" }
        }));
    }, 35000); // After 35 seconds (timeout is 30s)
});

ws.on('message', function(data) {
    const msg = JSON.parse(data.toString());
    
    if (msg.type === 'STATE_CHANGE') {
        console.log(`\n🔄 State change: ${msg.stateBefore || 'INITIAL'} → ${msg.stateAfter}`);
        if (msg.eventName) {
            console.log(`   Event: ${msg.eventName}`);
        }
        
        // Reset countdown tracking for new RINGING state
        if (msg.stateAfter === 'RINGING') {
            countdownMessages[msg.stateAfter] = 0;
        }
    } else if (msg.type === 'TIMEOUT_COUNTDOWN') {
        if (!countdownMessages[msg.state]) {
            countdownMessages[msg.state] = 0;
        }
        countdownMessages[msg.state]++;
        
        // Log first few countdown messages for each RINGING state
        if (countdownMessages[msg.state] <= 3 || msg.remainingSeconds === 1) {
            console.log(`   ⏱️ Countdown: ${msg.remainingSeconds}s remaining`);
        }
        
        if (countdownMessages[msg.state] === 1) {
            console.log(`   ✅ Countdown timer started for ${msg.state}`);
        }
    }
});

ws.on('error', function(err) {
    console.error('❌ Error:', err.message);
    process.exit(1);
});

// Extended timeout to allow for full test sequence
setTimeout(() => {
    console.log('\n✅ Test completed');
    console.log('\nSummary:');
    for (const state in countdownMessages) {
        console.log(`- ${state}: Received ${countdownMessages[state]} countdown messages`);
    }
    
    if (countdownMessages['RINGING'] > 30) {
        console.log('\n✅ SUCCESS: Countdown timer worked for both RINGING states');
    } else {
        console.log('\n⚠️  ISSUE: Countdown may not have restarted properly');
    }
    
    ws.close();
    process.exit(0);
}, 40000);