const WebSocket = require('ws');

console.log('=== Test: UI State Display on Live Viewer Button Click ===\n');

// First connection - set up some state
console.log('1. Initial connection - creating state history...');
let ws = new WebSocket('ws://localhost:9998');
let transitions = [];

ws.on('open', function open() {
    console.log('   Connected, initial state should be IDLE');
    
    // Create a history of transitions
    setTimeout(() => {
        console.log('2. Sending INCOMING_CALL to change to RINGING...');
        ws.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-HISTORY" }
        }));
    }, 1000);
    
    setTimeout(() => {
        console.log('3. Sending ANSWER to change to CONNECTED...');
        ws.send(JSON.stringify({
            action: "ANSWER"
        }));
    }, 2000);
    
    setTimeout(() => {
        console.log('4. Sending HANGUP to return to IDLE...');
        ws.send(JSON.stringify({
            action: "HANGUP"
        }));
    }, 3000);
    
    // Now simulate Live Viewer button click
    setTimeout(() => {
        console.log('\n5. Simulating Live Viewer button click (reconnecting)...');
        console.log('   History should be preserved, current state should be IDLE');
        console.log('   Transitions recorded: ' + transitions.length);
        
        // Close and reconnect
        ws.close();
        
        setTimeout(() => {
            let ws2 = new WebSocket('ws://localhost:9998');
            
            ws2.on('open', function() {
                console.log('   ✓ Reconnected');
            });
            
            ws2.on('message', function(data) {
                const msg = JSON.parse(data.toString());
                if (msg.type === 'CURRENT_STATE') {
                    console.log('   Current state shown in UI: ' + msg.currentState);
                    console.log('\n✅ The UI should show:');
                    console.log('   - Current state: ' + msg.currentState);
                    console.log('   - History of ' + transitions.length + ' transitions preserved');
                    console.log('   - No duplicate initial state entry');
                    
                    setTimeout(() => {
                        ws2.close();
                        process.exit(0);
                    }, 1000);
                }
            });
        }, 500);
    }, 4000);
});

ws.on('message', function(data) {
    const msg = JSON.parse(data.toString());
    if (msg.type === 'CURRENT_STATE') {
        console.log('   Initial state: ' + msg.currentState);
    } else if (msg.type === 'STATE_CHANGE') {
        const transition = `${msg.stateBefore} → ${msg.stateAfter}`;
        transitions.push(transition);
        console.log('   State changed: ' + transition);
    }
});

ws.on('error', function(err) {
    console.error('Error:', err);
    process.exit(1);
});