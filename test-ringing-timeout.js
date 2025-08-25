const WebSocket = require('ws');

console.log('Connecting to WebSocket server...');
const ws = new WebSocket('ws://localhost:9999');

ws.on('open', () => {
    console.log('✓ Connected to WebSocket server');
    
    // Send INCOMING_CALL event to call-001
    const incomingCallMessage = {
        type: 'EVENT',
        machineId: 'call-001',
        eventType: 'INCOMING_CALL',
        payload: {
            callId: 'test-call-123'
        }
    };
    
    console.log('\nSending INCOMING_CALL event to call-001...');
    ws.send(JSON.stringify(incomingCallMessage));
    console.log('✓ Event sent');
    
    console.log('\n⏳ Waiting for timeout (30 seconds for RINGING -> IDLE transition)...');
    console.log('   Listening for state changes...\n');
});

ws.on('message', (data) => {
    try {
        const msg = JSON.parse(data.toString());
        
        // Filter for relevant messages - handle both field names
        if (msg.type === 'STATE_CHANGE' && msg.machineId === 'call-001') {
            const fromState = msg.fromState || msg.stateBefore;
            const toState = msg.toState || msg.stateAfter;
            
            console.log(`[STATE CHANGE] Machine: ${msg.machineId}`);
            console.log(`   From: ${fromState} → To: ${toState}`);
            console.log(`   Timestamp: ${new Date().toLocaleTimeString()}`);
            
            if (toState === 'IDLE' && fromState === 'RINGING') {
                console.log('\n✅ SUCCESS! Timeout worked - machine transitioned from RINGING to IDLE');
                process.exit(0);
            }
        } else if (msg.type === 'MACHINE_STATE' && msg.machineId === 'call-001') {
            console.log(`[CURRENT STATE] Machine ${msg.machineId} is in state: ${msg.state}`);
            if (msg.transitions) {
                console.log(`   Available transitions:`, msg.transitions);
            }
            if (msg.timeoutInfo) {
                console.log(`   Timeout info:`, msg.timeoutInfo);
            }
        }
    } catch (e) {
        // Ignore parse errors for non-JSON messages
    }
});

ws.on('error', (err) => {
    console.error('WebSocket error:', err.message);
});

ws.on('close', () => {
    console.log('\nWebSocket connection closed');
});

// Set a timeout to fail the test if transition doesn't happen
setTimeout(() => {
    console.log('\n❌ FAILURE: Timeout did not trigger after 35 seconds');
    console.log('The machine should have transitioned from RINGING to IDLE but did not.');
    process.exit(1);
}, 35000);