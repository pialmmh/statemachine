const WebSocket = require('ws');

const ws = new WebSocket('ws://localhost:9998');

ws.on('open', function open() {
    console.log('Connected to WebSocket server');
    
    // Send INCOMING_CALL
    setTimeout(() => {
        const msg = {
            action: "INCOMING_CALL",
            payload: { callerNumber: "+1-555-1234" }
        };
        ws.send(JSON.stringify(msg));
        console.log('Sent INCOMING_CALL');
    }, 500);
    
    // Send ANSWER after 2 seconds
    setTimeout(() => {
        const msg = { action: "ANSWER" };
        ws.send(JSON.stringify(msg));
        console.log('Sent ANSWER');
    }, 2000);
    
    // Send HANGUP after 4 seconds
    setTimeout(() => {
        const msg = { action: "HANGUP" };
        ws.send(JSON.stringify(msg));
        console.log('Sent HANGUP');
    }, 4000);
    
    // Close after 5 seconds
    setTimeout(() => {
        ws.close();
        console.log('Connection closed');
        process.exit(0);
    }, 5000);
});

ws.on('message', function message(data) {
    console.log('Received:', data.toString().substring(0, 100) + '...');
});

ws.on('error', function error(err) {
    console.error('WebSocket error:', err);
});