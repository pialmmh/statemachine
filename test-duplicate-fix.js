const WebSocket = require('ws');

console.log('Testing duplicate state transition fix...\n');

const ws = new WebSocket('ws://localhost:9999');
let stateChangeCount = 0;
let eventFiredCount = 0;

ws.on('open', () => {
    console.log('✓ Connected to WebSocket server');
    
    // Wait a bit then send INCOMING_CALL event
    setTimeout(() => {
        const message = {
            type: 'EVENT',
            machineId: 'call-001',
            eventType: 'INCOMING_CALL',
            payload: {
                callId: 'test-duplicate-fix'
            }
        };
        
        console.log('\n📤 Sending INCOMING_CALL event to call-001...');
        ws.send(JSON.stringify(message));
    }, 100);
});

ws.on('message', (data) => {
    try {
        const msg = JSON.parse(data.toString());
        
        // Count STATE_CHANGE messages for call-001
        if (msg.type === 'STATE_CHANGE' && msg.machineId === 'call-001') {
            stateChangeCount++;
            const fromState = msg.stateBefore || msg.fromState || 'UNKNOWN';
            const toState = msg.stateAfter || msg.toState || 'UNKNOWN';
            
            console.log(`\n📨 STATE_CHANGE #${stateChangeCount}:`);
            console.log(`   Machine: ${msg.machineId}`);
            console.log(`   Transition: ${fromState} → ${toState}`);
            console.log(`   Timestamp: ${msg.timestamp}`);
            console.log(`   Entry Action: ${msg.entryActionStatus || 'not specified'}`);
            
            if (fromState === 'IDLE' && toState === 'RINGING') {
                console.log('   ✓ This is the expected IDLE → RINGING transition');
            }
        }
        
        // Count EVENT_FIRED messages
        if (msg.type === 'EVENT_FIRED' && msg.machineId === 'call-001') {
            eventFiredCount++;
            console.log(`\n📨 EVENT_FIRED #${eventFiredCount}:`);
            console.log(`   Machine: ${msg.machineId}`);
            console.log(`   Event: ${msg.eventType}`);
            console.log(`   Success: ${msg.success}`);
        }
        
    } catch (e) {
        // Ignore non-JSON messages
    }
});

ws.on('error', (err) => {
    console.error('WebSocket error:', err.message);
});

ws.on('close', () => {
    console.log('\nWebSocket connection closed');
});

// After 3 seconds, check results
setTimeout(() => {
    console.log('\n' + '='.repeat(50));
    console.log('TEST RESULTS:');
    console.log('='.repeat(50));
    
    if (stateChangeCount === 1) {
        console.log(`✅ PASS: Received exactly 1 STATE_CHANGE message (expected)`);
    } else {
        console.log(`❌ FAIL: Received ${stateChangeCount} STATE_CHANGE messages (expected 1)`);
    }
    
    console.log(`📊 EVENT_FIRED messages: ${eventFiredCount}`);
    
    if (stateChangeCount === 1) {
        console.log('\n🎉 Duplicate state transition issue is FIXED!');
    } else {
        console.log('\n⚠️ Duplicate state transitions still occurring');
    }
    
    process.exit(stateChangeCount === 1 ? 0 : 1);
}, 3000);