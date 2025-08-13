const WebSocket = require('ws');

const ws = new WebSocket('ws://localhost:9998');

ws.on('open', function open() {
    console.log('Connected - sending multiple call cycles');
    console.log('Each cycle should appear below the previous one\n');
    
    let callNumber = 1;
    
    function sendCallCycle(num) {
        const phone = `+1-555-${String(1000 + num).padStart(4, '0')}`;
        
        setTimeout(() => {
            console.log(`Call ${num}: INCOMING_CALL from ${phone}`);
            ws.send(JSON.stringify({
                action: "INCOMING_CALL",
                payload: { callerNumber: phone }
            }));
        }, (num - 1) * 3000 + 500);
        
        setTimeout(() => {
            console.log(`Call ${num}: ANSWER`);
            ws.send(JSON.stringify({
                action: "ANSWER"
            }));
        }, (num - 1) * 3000 + 1000);
        
        setTimeout(() => {
            console.log(`Call ${num}: HANGUP`);
            ws.send(JSON.stringify({
                action: "HANGUP"
            }));
        }, (num - 1) * 3000 + 1500);
    }
    
    // Send 3 call cycles
    sendCallCycle(1);
    sendCallCycle(2);
    sendCallCycle(3);
    
    setTimeout(() => {
        console.log('\n✓ All cycles sent');
        console.log('Live Mode should show:');
        console.log('- Initial IDLE at top');
        console.log('- Call 1 transitions');
        console.log('- Call 2 transitions');
        console.log('- Call 3 transitions at bottom');
        ws.close();
        process.exit(0);
    }, 10000);
});

ws.on('message', function message(data) {
    const msg = JSON.parse(data.toString());
    if (msg.type === 'CURRENT_STATE') {
        console.log('Initial: ' + msg.currentState);
    }
});

ws.on('error', function error(err) {
    console.error('WebSocket error:', err);
});