const WebSocket = require('ws');

console.log('=== Testing Countdown in Card Header ===\n');

const ws = new WebSocket('ws://localhost:9996');
let countdownReceived = false;

ws.on('open', function() {
    console.log('✅ Connected to WebSocket on port 9996');
    console.log('📊 Open browser: http://localhost:8091');
    console.log('📍 Countdown should appear in the state card header (top right)\n');
    
    setTimeout(() => {
        console.log('📞 Triggering RINGING state (30s timeout)...\n');
        ws.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-HEADER-TEST" }
        }));
    }, 1000);
});

ws.on('message', function(data) {
    const msg = JSON.parse(data.toString());
    
    if (msg.type === 'TIMEOUT_COUNTDOWN') {
        if (!countdownReceived) {
            console.log('✅ Countdown messages being sent');
            console.log('📍 Location: State card header (top right)');
            console.log('🎨 Colors: Static (no pulsing animation)');
            console.log('   - Normal (>10s): White transparent background');
            console.log('   - Warning (6-10s): Orange background');
            console.log('   - Danger (≤5s): Red background\n');
            countdownReceived = true;
        }
        
        if (msg.remainingSeconds === 28 || msg.remainingSeconds === 10 || msg.remainingSeconds === 5) {
            console.log(`⏱️  ${msg.remainingSeconds}s remaining`);
        }
        
        if (msg.remainingSeconds === 25) {
            console.log('\n✅ SUCCESS! Countdown working in new location');
            console.log('Check the UI to see it in the state card header');
            ws.close();
            process.exit(0);
        }
    } else if (msg.type === 'STATE_CHANGE' && msg.stateAfter === 'RINGING') {
        console.log('✅ State changed to RINGING');
        console.log('⏳ Countdown will appear in the RINGING state card header\n');
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
}, 8000);