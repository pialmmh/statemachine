const WebSocket = require('ws');

console.log('=== Test 1: Initial connection and state change ===');
let ws = new WebSocket('ws://localhost:9998');

ws.on('open', function open() {
    console.log('✓ Connected');
    
    // Wait for initial state
    setTimeout(() => {
        console.log('Changing state to RINGING...');
        ws.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-STATE-TEST" }
        }));
    }, 500);
    
    // Close after state change
    setTimeout(() => {
        console.log('Disconnecting...\n');
        ws.close();
    }, 1500);
});

ws.on('message', function(data) {
    const msg = JSON.parse(data.toString());
    if (msg.type === 'CURRENT_STATE') {
        console.log('Initial state:', msg.currentState);
    } else if (msg.type === 'STATE_CHANGE') {
        console.log(`State changed: ${msg.stateBefore} → ${msg.stateAfter}`);
    }
});

ws.on('close', function() {
    // Simulate clicking Live Viewer button (reconnect)
    setTimeout(() => {
        console.log('=== Test 2: Reconnecting (Live Viewer button clicked) ===');
        let ws2 = new WebSocket('ws://localhost:9998');
        
        ws2.on('open', function() {
            console.log('✓ Reconnected');
        });
        
        ws2.on('message', function(data) {
            const msg = JSON.parse(data.toString());
            if (msg.type === 'CURRENT_STATE') {
                console.log('Current state after reconnect:', msg.currentState);
                if (msg.currentState === 'RINGING') {
                    console.log('✅ SUCCESS: State preserved correctly as RINGING');
                } else {
                    console.log('❌ FAIL: State is ' + msg.currentState + ' but should be RINGING');
                }
                
                // Send another event to verify it continues working
                setTimeout(() => {
                    console.log('\nSending ANSWER to move to CONNECTED...');
                    ws2.send(JSON.stringify({
                        action: "ANSWER"
                    }));
                }, 1000);
                
                // Close after final test
                setTimeout(() => {
                    console.log('\n✓ Test completed');
                    ws2.close();
                    process.exit(0);
                }, 2000);
            } else if (msg.type === 'STATE_CHANGE') {
                console.log(`State changed: ${msg.stateBefore} → ${msg.stateAfter}`);
            }
        });
        
        ws2.on('error', function(err) {
            console.error('Reconnection failed:', err);
            process.exit(1);
        });
    }, 1000);
});

ws.on('error', function(err) {
    console.error('Connection failed:', err);
    process.exit(1);
});