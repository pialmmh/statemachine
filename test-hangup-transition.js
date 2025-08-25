#!/usr/bin/env node

const WebSocket = require('ws');

async function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function testHangupTransition() {
    console.log('\n' + '='.repeat(60));
    console.log('  TESTING HANGUP TRANSITION FROM CONNECTED TO IDLE');
    console.log('='.repeat(60) + '\n');
    
    const ws = new WebSocket('ws://localhost:9999');
    
    await new Promise((resolve, reject) => {
        ws.on('open', resolve);
        ws.on('error', reject);
    });
    
    console.log('✅ Connected to WebSocket server\n');
    
    // Listen for messages
    ws.on('message', (data) => {
        const msg = JSON.parse(data);
        if (msg.type === 'EVENT_FIRED') {
            console.log(`📨 State Change: ${msg.machineId} [${msg.oldState} → ${msg.newState}]`);
        } else if (msg.type === 'TREEVIEW_STORE_UPDATE' && msg.store?.transitions) {
            console.log('\n📊 Tree View Transitions:');
            msg.store.transitions.forEach(t => {
                console.log(`  Step ${t.stepNumber}: ${t.fromState} → ${t.toState} (${t.event})`);
            });
        }
    });
    
    const machineId = 'call-002';
    
    // Step 1: Select machine for tree view
    console.log('Step 1: Selecting machine call-002 for tree view...');
    ws.send(JSON.stringify({
        type: 'TREEVIEW_ACTION',
        action: 'SELECT_MACHINE',
        payload: { machineId: machineId },
        timestamp: new Date().toISOString()
    }));
    await sleep(1000);
    
    // Step 2: Send INCOMING_CALL -> RINGING
    console.log('\nStep 2: Sending INCOMING_CALL (IDLE → RINGING)...');
    ws.send(JSON.stringify({
        type: 'EVENT_TO_ARBITRARY',
        machineId: machineId,
        eventType: 'INCOMING_CALL',
        payload: { phoneNumber: '+1-555-1234' }
    }));
    await sleep(2000);
    
    // Step 3: Send ANSWER -> CONNECTED (offline)
    console.log('\nStep 3: Sending ANSWER (RINGING → CONNECTED)...');
    ws.send(JSON.stringify({
        type: 'EVENT_TO_ARBITRARY',
        machineId: machineId,
        eventType: 'ANSWER',
        payload: {}
    }));
    await sleep(2000);
    
    console.log('\n✅ Machine is now in CONNECTED state (offline)');
    
    // Step 4: Send HANGUP -> should go to IDLE
    console.log('\nStep 4: Sending HANGUP (CONNECTED → IDLE)...');
    ws.send(JSON.stringify({
        type: 'EVENT_TO_ARBITRARY',
        machineId: machineId,
        eventType: 'HANGUP',
        payload: {}
    }));
    await sleep(3000);
    
    console.log('\n' + '='.repeat(60));
    console.log('  EXPECTED RESULT:');
    console.log('  - Machine should transition from CONNECTED to IDLE');
    console.log('  - Tree view should show all transitions including HANGUP');
    console.log('='.repeat(60) + '\n');
    
    ws.close();
    process.exit(0);
}

testHangupTransition().catch(console.error);
