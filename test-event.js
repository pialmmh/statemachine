const WebSocket = require('ws');

const ws = new WebSocket('ws://localhost:9999');

ws.on('open', function open() {
    console.log('Connected to WebSocket');
    
    // Send INCOMING_CALL event
    const event = {
        type: 'EVENT',
        machineId: 'call-002',
        eventType: 'INCOMING_CALL',
        payload: { phoneNumber: '+1-555-9999' }
    };
    
    console.log('Sending event:', JSON.stringify(event));
    ws.send(JSON.stringify(event));
    
    // Wait for response
    setTimeout(() => {
        ws.close();
        process.exit(0);
    }, 2000);
});

ws.on('message', function message(data) {
    console.log('Received:', data.toString());
});

ws.on('error', function error(err) {
    console.error('WebSocket error:', err);
    process.exit(1);
});