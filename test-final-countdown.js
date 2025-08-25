const WebSocket = require('ws');

console.log('=== Final Test: Countdown Display on Multiple RINGING States ===');

const ws = new WebSocket('ws://localhost:9999');
let testPhase = 1;

ws.on('open', function() {
    console.log('Connected to WebSocket on port 9999');
    
    setTimeout(() => {
        console.log('Phase 1: First RINGING state');
        ws.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-PHASE1" }
        }));
    }, 500);
    
    setTimeout(() => {
        console.log('Phase 2: Second RINGING state (after timeout)');
        testPhase = 2;
        ws.send(JSON.stringify({
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-PHASE2" }
        }));
    }, 32000);
});

ws.on('message', function(data) {
    const msg = JSON.parse(data.toString());
    
    if (msg.type === 'STATE_CHANGE') {
        const before = msg.stateBefore || 'INITIAL';
        console.log('State: ' + before + ' to ' + msg.stateAfter);
    } else if (msg.type === 'TIMEOUT_COUNTDOWN' && (msg.remainingSeconds === 30 || msg.remainingSeconds === 1)) {
        console.log('Countdown Phase ' + testPhase + ': ' + msg.remainingSeconds + 's');
    }
});

setTimeout(() => {
    console.log('Test completed');
    ws.close();
    process.exit(0);
}, 37000);
