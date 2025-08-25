const WebSocket = require('ws');

const ws = new WebSocket('ws://localhost:9998');

ws.on('open', function open() {
    console.log('Connected - Initial state should display first');
    
    // Wait 3 seconds before first event to see initial state clearly
    setTimeout(() => {
        console.log('\nSending INCOMING_CALL...');
        const msg = {
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-TEST" }
        };
        ws.send(JSON.stringify(msg));
    }, 3000);
    
    // SESSION_PROGRESS after 5 seconds
    setTimeout(() => {
        console.log('Sending SESSION_PROGRESS...');
        const msg = {
            action: "SESSION_PROGRESS",
            payload: { sdp: "v=0", ringNumber: 1 }
        };
        ws.send(JSON.stringify(msg));
    }, 5000);
    
    // ANSWER after 7 seconds  
    setTimeout(() => {
        console.log('Sending ANSWER...');
        const msg = { action: "ANSWER" };
        ws.send(JSON.stringify(msg));
    }, 7000);
    
    // HANGUP after 9 seconds
    setTimeout(() => {
        console.log('Sending HANGUP...');
        const msg = { action: "HANGUP" };
        ws.send(JSON.stringify(msg));
    }, 9000);
    
    // Keep connection open for monitoring
    setTimeout(() => {
        console.log('\nTest completed - check the Live Mode viewer');
        console.log('The viewer should show:');
        console.log('1. Initial state (IDLE) with green header');
        console.log('2. Transition to RINGING');
        console.log('3. SESSION_PROGRESS in RINGING');
        console.log('4. Transition to CONNECTED');
        console.log('5. Transition back to IDLE');
        ws.close();
        process.exit(0);
    }, 12000);
});

ws.on('message', function message(data) {
    const msg = JSON.parse(data.toString());
    if (msg.type === 'CURRENT_STATE') {
        console.log('✓ Initial state displayed: ' + msg.currentState);
    } else if (msg.type === 'STATE_CHANGE') {
        console.log(`✓ Transition: ${msg.stateBefore || msg.oldState} → ${msg.stateAfter || msg.newState}`);
    }
});

ws.on('error', function error(err) {
    console.error('WebSocket error:', err);
});