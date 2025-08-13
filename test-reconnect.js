const WebSocket = require('ws');

console.log('Test 1: Initial connection');
let ws = new WebSocket('ws://localhost:9998');

ws.on('open', function open() {
    console.log('✓ Connected successfully');
    
    // Send an event
    setTimeout(() => {
        console.log('Sending INCOMING_CALL...');
        ws.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-FIRST" }
        }));
    }, 500);
    
    // Close connection after 1 second
    setTimeout(() => {
        console.log('Closing first connection...');
        ws.close();
    }, 1000);
});

ws.on('close', function() {
    console.log('First connection closed');
    
    // Simulate clicking Live Viewer button again
    setTimeout(() => {
        console.log('\nTest 2: Simulating Live Viewer button click (reconnect)');
        let ws2 = new WebSocket('ws://localhost:9998');
        
        ws2.on('open', function() {
            console.log('✓ Reconnected successfully');
            
            // Should receive initial state again
            ws2.on('message', function(data) {
                const msg = JSON.parse(data.toString());
                if (msg.type === 'CURRENT_STATE') {
                    console.log('✓ Received initial state on reconnect:', msg.currentState);
                }
            });
            
            // Send another event
            setTimeout(() => {
                console.log('Sending INCOMING_CALL on new connection...');
                ws2.send(JSON.stringify({
                    action: "INCOMING_CALL",
                    payload: { callerNumber: "+1-555-SECOND" }
                }));
            }, 500);
            
            // Close after 1 second
            setTimeout(() => {
                console.log('✓ Test completed successfully');
                ws2.close();
                process.exit(0);
            }, 1500);
        });
        
        ws2.on('error', function(err) {
            console.error('✗ Reconnection failed:', err.message);
            process.exit(1);
        });
    }, 1000);
});

ws.on('error', function(err) {
    console.error('✗ Initial connection failed:', err);
    process.exit(1);
});