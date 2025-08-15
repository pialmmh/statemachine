const WebSocket = require('ws');

const ws = new WebSocket('ws://localhost:9999');

ws.on('open', () => {
    console.log('Connected to WebSocket server');
    
    // Send INCOMING_CALL event to trigger RINGING state (which has 30s timeout)
    setTimeout(() => {
        const request = {
            type: 'EVENT',
            eventType: 'INCOMING_CALL',
            payload: { phoneNumber: '+1-555-9999' }
        };
        console.log('\nSending INCOMING_CALL event:', JSON.stringify(request));
        ws.send(JSON.stringify(request));
    }, 1000);
});

ws.on('message', (data) => {
    const message = JSON.parse(data.toString());
    
    // Only log STATE_CHANGE, COUNTDOWN, and PERIODIC_UPDATE messages
    if (message.type === 'STATE_CHANGE' || 
        message.type === 'COUNTDOWN' || 
        message.type === 'PERIODIC_UPDATE') {
        
        console.log('\n=== Message Received ===');
        console.log('Type:', message.type);
        console.log('State:', message.newState || message.state || 'N/A');
        
        if (message.countdown) {
            console.log('Countdown:', message.countdown);
        }
        
        if (message.type === 'COUNTDOWN') {
            console.log('Countdown State:', message.state);
            console.log('Remaining:', message.remaining);
        }
    }
});

ws.on('error', (error) => {
    console.error('WebSocket error:', error);
});

ws.on('close', () => {
    console.log('\nDisconnected from WebSocket server');
    process.exit(0);
});

// Keep the process alive for 40 seconds to see the full countdown
setTimeout(() => {
    console.log('\nClosing connection...');
    ws.close();
}, 40000);