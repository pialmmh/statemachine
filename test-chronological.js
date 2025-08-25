const WebSocket = require('ws');

const ws = new WebSocket('ws://localhost:9998');

ws.on('open', function open() {
    console.log('Connected - Events will be sent in order:');
    console.log('1. Initial State (IDLE)');
    console.log('2. INCOMING_CALL (IDLE → RINGING)');
    console.log('3. SESSION_PROGRESS (Stay in RINGING)');
    console.log('4. ANSWER (RINGING → CONNECTED)');
    console.log('5. HANGUP (CONNECTED → IDLE)');
    console.log('\nEvents should appear in this order from top to bottom\n');
    
    let step = 1;
    
    // Send events with delays
    setTimeout(() => {
        console.log(`Step ${++step}: Sending INCOMING_CALL...`);
        ws.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-ORDER" }
        }));
    }, 1500);
    
    setTimeout(() => {
        console.log(`Step ${++step}: Sending SESSION_PROGRESS...`);
        ws.send(JSON.stringify({
            action: "SESSION_PROGRESS",
            payload: { sdp: "v=0", ringNumber: 1 }
        }));
    }, 2500);
    
    setTimeout(() => {
        console.log(`Step ${++step}: Sending ANSWER...`);
        ws.send(JSON.stringify({
            action: "ANSWER"
        }));
    }, 3500);
    
    setTimeout(() => {
        console.log(`Step ${++step}: Sending HANGUP...`);
        ws.send(JSON.stringify({
            action: "HANGUP"
        }));
    }, 4500);
    
    setTimeout(() => {
        console.log('\n✓ Test completed');
        console.log('Check Live Mode viewer - events should be in chronological order');
        ws.close();
        process.exit(0);
    }, 5500);
});

ws.on('message', function message(data) {
    const msg = JSON.parse(data.toString());
    if (msg.type === 'CURRENT_STATE') {
        console.log('Step 1: Initial state received - ' + msg.currentState);
    } else if (msg.type === 'STATE_CHANGE') {
        const transition = `${msg.stateBefore || msg.oldState} → ${msg.stateAfter || msg.newState}`;
        console.log(`        Transition confirmed: ${transition}`);
    }
});

ws.on('error', function error(err) {
    console.error('WebSocket error:', err);
});