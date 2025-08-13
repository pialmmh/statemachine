const WebSocket = require('ws');

console.log('=== Testing RINGING state timeout behavior (port 9998) ===\n');

const ws = new WebSocket('ws://localhost:9998');

ws.on('open', function() {
    console.log('Connected to WebSocket server');
    
    // Send INCOMING_CALL to trigger RINGING state
    setTimeout(() => {
        console.log('Sending INCOMING_CALL...');
        ws.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-TIMEOUT-TEST" }
        }));
        
        // Track time and monitor state changes
        const startTime = Date.now();
        
        ws.on('message', function(data) {
            const msg = JSON.parse(data.toString());
            const elapsed = Math.round((Date.now() - startTime) / 1000);
            
            if (msg.type === 'STATE_CHANGE') {
                console.log(`[${elapsed}s] State change: ${msg.stateBefore} → ${msg.stateAfter}`);
                
                if (msg.stateAfter === 'IDLE' && msg.stateBefore === 'RINGING') {
                    if (elapsed < 25) {
                        console.log(`\n❗ BUG FIXED? Timeout after ${elapsed} seconds`);
                        console.log('   If this is ~30s, the bug is fixed!');
                    } else {
                        console.log(`\n✅ TIMEOUT FIXED! Occurred after ${elapsed} seconds (expected ~30s)`);
                    }
                    ws.close();
                    process.exit(0);
                }
            }
        });
    }, 500);
});

ws.on('error', function(err) {
    console.error('Connection error:', err.message);
    process.exit(1);
});

// Safety timeout after 35 seconds
setTimeout(() => {
    console.log('\n✅ Good news: No immediate transition detected!');
    console.log('Waiting for 30-second timeout...');
}, 5000);

// Final timeout after 40 seconds
setTimeout(() => {
    console.log('\nTest completed after 40 seconds');
    ws.close();
    process.exit(0);
}, 40000);