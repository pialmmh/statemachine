const WebSocket = require('ws');

const ws = new WebSocket('ws://localhost:9998');

ws.on('open', function open() {
    console.log('Connected to WebSocket server');
    console.log('Sending events every 5 seconds...');
    console.log('Press Ctrl+C to stop');
    
    let callCount = 0;
    
    // Send events in a cycle
    const sendCycle = () => {
        callCount++;
        const phoneNumber = `+1-555-${String(1000 + callCount).padStart(4, '0')}`;
        
        // INCOMING_CALL
        setTimeout(() => {
            const msg = {
                action: "INCOMING_CALL",
                payload: { callerNumber: phoneNumber }
            };
            ws.send(JSON.stringify(msg));
            console.log(`[${new Date().toLocaleTimeString()}] Sent INCOMING_CALL from ${phoneNumber}`);
        }, 0);
        
        // SESSION_PROGRESS (ring)
        setTimeout(() => {
            const msg = {
                action: "SESSION_PROGRESS",
                payload: { sdp: "v=0", ringNumber: 1 }
            };
            ws.send(JSON.stringify(msg));
            console.log(`[${new Date().toLocaleTimeString()}] Sent SESSION_PROGRESS (ring 1)`);
        }, 1000);
        
        // ANSWER
        setTimeout(() => {
            const msg = { action: "ANSWER" };
            ws.send(JSON.stringify(msg));
            console.log(`[${new Date().toLocaleTimeString()}] Sent ANSWER`);
        }, 2000);
        
        // HANGUP
        setTimeout(() => {
            const msg = { action: "HANGUP" };
            ws.send(JSON.stringify(msg));
            console.log(`[${new Date().toLocaleTimeString()}] Sent HANGUP`);
        }, 3000);
    };
    
    // Start first cycle immediately
    sendCycle();
    
    // Repeat cycle every 5 seconds
    setInterval(sendCycle, 5000);
});

ws.on('message', function message(data) {
    const msg = data.toString();
    const parsed = JSON.parse(msg);
    if (parsed.type === 'STATE_CHANGE') {
        console.log(`   -> State change: ${parsed.stateBefore || parsed.oldState} → ${parsed.stateAfter || parsed.newState}`);
    }
});

ws.on('error', function error(err) {
    console.error('WebSocket error:', err);
    process.exit(1);
});