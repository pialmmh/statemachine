#!/usr/bin/env node

const WebSocket = require('ws');

async function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function testRehydrationTimeout() {
    console.log('Connecting to WebSocket server...');
    const ws = new WebSocket('ws://localhost:9999');
    
    await new Promise((resolve, reject) => {
        ws.on('open', resolve);
        ws.on('error', reject);
    });
    
    console.log('Connected!');
    
    // Send INCOMING_CALL to move to RINGING
    console.log('\n1. Sending INCOMING_CALL to call-002...');
    ws.send(JSON.stringify({
        type: 'SEND_EVENT',
        machineId: 'call-002',
        eventType: 'INCOMING_CALL',
        payload: { phoneNumber: '+1-555-9999' }
    }));
    
    await sleep(1000);
    
    // Send ANSWER to move to CONNECTED (offline state)
    console.log('2. Sending ANSWER to call-002 (will go to CONNECTED/offline)...');
    ws.send(JSON.stringify({
        type: 'SEND_EVENT',
        machineId: 'call-002',
        eventType: 'ANSWER',
        payload: {}
    }));
    
    await sleep(1000);
    
    console.log('3. Machine should now be in CONNECTED state (offline)');
    console.log('   Waiting 35 seconds for timeout to expire (timeout is 30s)...');
    
    // Wait for timeout to expire
    await sleep(35000);
    
    console.log('\n4. Sending arbitrary event to trigger rehydration...');
    console.log('   The machine should automatically timeout to IDLE upon rehydration');
    
    ws.send(JSON.stringify({
        type: 'EVENT_TO_ARBITRARY',
        machineId: 'call-002',
        eventType: 'HANGUP',
        payload: {}
    }));
    
    // Listen for messages
    ws.on('message', (data) => {
        const msg = JSON.parse(data);
        if (msg.type === 'EVENT_FIRED' && msg.machineId === 'call-002') {
            console.log(`\n✓ Event result: ${msg.oldState} -> ${msg.newState}`);
            if (msg.oldState === 'CONNECTED' && msg.newState === 'IDLE') {
                console.log('✅ SUCCESS: Timeout worked! Machine transitioned from CONNECTED to IDLE');
            } else if (msg.newState === 'IDLE') {
                console.log('✅ Machine is in IDLE (likely timed out during rehydration)');
            }
        }
    });
    
    await sleep(2000);
    
    console.log('\nTest complete!');
    ws.close();
    process.exit(0);
}

testRehydrationTimeout().catch(console.error);