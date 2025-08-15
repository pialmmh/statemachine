const WebSocket = require('ws');

const ws = new WebSocket('ws://localhost:9999');

ws.on('open', () => {
    console.log('Connected to WebSocket server');
    
    // Request initial state
    const request = { action: 'GET_STATE' };
    console.log('Sending:', JSON.stringify(request));
    ws.send(JSON.stringify(request));
});

ws.on('message', (data) => {
    console.log('Raw message received:', data.toString());
    try {
        const parsed = JSON.parse(data.toString());
        console.log('Parsed message:', JSON.stringify(parsed, null, 2));
        
        // Check what fields are present
        console.log('Message type:', parsed.type);
        console.log('Has context?', !!parsed.context);
        console.log('Context keys:', parsed.context ? Object.keys(parsed.context) : 'N/A');
    } catch (e) {
        console.error('Parse error:', e);
    }
});

ws.on('error', (error) => {
    console.error('WebSocket error:', error);
});

ws.on('close', () => {
    console.log('Disconnected from WebSocket server');
    process.exit(0);
});

// Keep the process alive for 10 seconds
setTimeout(() => {
    ws.close();
}, 10000);