const WebSocket = require('ws');

console.log('=== Testing actual machine state ===\n');

// First, change the state
console.log('1. Connecting and changing state to RINGING...');
let ws1 = new WebSocket('ws://localhost:9999');

ws1.on('open', function() {
    setTimeout(() => {
        ws1.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-TEST" }
        }));
    }, 500);
});

ws1.on('message', function(data) {
    const msg = JSON.parse(data.toString());
    if (msg.type === 'STATE_CHANGE' && msg.stateAfter === 'RINGING') {
        console.log('   State changed to RINGING');
        
        // Now close and reconnect to simulate refresh
        setTimeout(() => {
            console.log('\n2. Closing connection (simulating page leave)...');
            ws1.close();
            
            setTimeout(() => {
                console.log('\n3. Reconnecting (simulating page refresh)...');
                let ws2 = new WebSocket('ws://localhost:9999');
                
                ws2.on('open', function() {
                    console.log('   Connected');
                });
                
                ws2.on('message', function(data2) {
                    const msg2 = JSON.parse(data2.toString());
                    if (msg2.type === 'CURRENT_STATE') {
                        console.log('   Server reports current state as:', msg2.currentState);
                        
                        if (msg2.currentState === 'IDLE') {
                            console.log('\n❗ The machine has returned to IDLE state');
                            console.log('   This could be due to:');
                            console.log('   - Timeout on RINGING state (30 seconds)');
                            console.log('   - State machine logic auto-transitioning');
                        } else if (msg2.currentState === 'RINGING') {
                            console.log('\n✅ Machine correctly preserved RINGING state');
                        }
                        
                        ws2.close();
                        process.exit(0);
                    }
                });
            }, 1000);
        }, 1000);
    }
});