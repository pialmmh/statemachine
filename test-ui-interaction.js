#!/usr/bin/env node

const WebSocket = require('ws');

async function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function testUIInteraction() {
    console.log('\n📡 Testing UI interaction with Enhanced CallMachine\n');
    
    const ws = new WebSocket('ws://localhost:9999');
    
    await new Promise((resolve, reject) => {
        ws.on('open', resolve);
        ws.on('error', reject);
    });
    
    console.log('✅ Connected to WebSocket\n');
    
    // Select machine call-001
    console.log('Selecting machine call-001...');
    ws.send(JSON.stringify({
        type: 'TREEVIEW_ACTION',
        action: 'SELECT_MACHINE',
        payload: { machineId: 'call-001' },
        timestamp: new Date().toISOString()
    }));
    
    await sleep(1000);
    
    // Send incoming call
    console.log('Sending INCOMING_CALL to call-001...');
    ws.send(JSON.stringify({
        type: 'EVENT_TO_ARBITRARY',
        machineId: 'call-001',
        eventType: 'INCOMING_CALL',
        payload: {}
    }));
    
    await sleep(2000);
    
    // Answer the call
    console.log('Sending ANSWER to call-001...');
    ws.send(JSON.stringify({
        type: 'EVENT_TO_ARBITRARY',
        machineId: 'call-001',
        eventType: 'ANSWER',
        payload: {}
    }));
    
    await sleep(2000);
    
    // Hangup
    console.log('Sending HANGUP to call-001...');
    ws.send(JSON.stringify({
        type: 'EVENT_TO_ARBITRARY',
        machineId: 'call-001',
        eventType: 'HANGUP',
        payload: {}
    }));
    
    await sleep(1000);
    
    console.log('\n✅ Test complete! Check the UI to see the state transitions.\n');
    
    ws.close();
    process.exit(0);
}

testUIInteraction().catch(console.error);