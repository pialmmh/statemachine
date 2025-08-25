const WebSocket = require('ws');

console.log('=== Testing RINGING state timeout behavior ===\n');

const ws = new WebSocket('ws://localhost:9999');

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
                        console.log(`\n❗ IMMEDIATE TRANSITION! Happened after only ${elapsed} seconds`);
                        console.log('   Expected: 30 seconds timeout');
                        console.log('   Actual: Immediate transition');
                    } else {
                        console.log(`\n✅ Timeout occurred after ${elapsed} seconds (expected ~30s)`);
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
    console.log('\nTest timed out after 35 seconds - no transition detected');
    ws.close();
    process.exit(1);
}, 35000);