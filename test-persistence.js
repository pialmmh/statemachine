#!/usr/bin/env node

const WebSocket = require('ws');

async function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function testPersistence() {
    console.log('\n💾 Testing Persistent Context Saving\n');
    
    const ws = new WebSocket('ws://localhost:9999');
    
    await new Promise((resolve, reject) => {
        ws.on('open', resolve);
        ws.on('error', reject);
    });
    
    console.log('✅ Connected to WebSocket\n');
    
    const machineId = 'call-003';
    
    // Select machine
    console.log(`1️⃣ Selecting machine ${machineId}...`);
    ws.send(JSON.stringify({
        type: 'TREEVIEW_ACTION',
        action: 'SELECT_MACHINE',
        payload: { machineId: machineId },
        timestamp: new Date().toISOString()
    }));
    await sleep(500);
    
    // Send INCOMING_CALL to go to RINGING
    console.log('2️⃣ Sending INCOMING_CALL (IDLE → RINGING)...');
    ws.send(JSON.stringify({
        type: 'EVENT_TO_ARBITRARY',
        machineId: machineId,
        eventType: 'INCOMING_CALL',
        payload: {}
    }));
    await sleep(1000);
    
    // Send ANSWER to go to CONNECTED (offline state)
    console.log('3️⃣ Sending ANSWER (RINGING → CONNECTED[offline])...');
    console.log('   This should trigger persistence of the context!');
    ws.send(JSON.stringify({
        type: 'EVENT_TO_ARBITRARY',
        machineId: machineId,
        eventType: 'ANSWER',
        payload: {}
    }));
    await sleep(2000);
    
    console.log('\n📊 Check the server logs for:');
    console.log('   - "[Registry] Machine entering offline state"');
    console.log('   - "[MySQLPersistenceProvider] Saved context"');
    console.log('   - "[Registry] Persisted offline machine"');
    
    console.log('\n✅ Test complete!\n');
    
    ws.close();
    process.exit(0);
}

testPersistence().catch(console.error);