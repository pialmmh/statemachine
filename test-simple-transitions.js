#!/usr/bin/env node

const WebSocket = require('ws');

async function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function testSimpleTransitions() {
    console.log('\n' + '='.repeat(60));
    console.log('  TESTING SIMPLE TRANSITIONS RECORDING');
    console.log('='.repeat(60) + '\n');
    
    // Wait for server to be ready
    await sleep(5000);
    
    const ws = new WebSocket('ws://localhost:9999');
    
    await new Promise((resolve, reject) => {
        ws.on('open', resolve);
        ws.on('error', reject);
    });
    
    console.log('✅ Connected to WebSocket server\n');
    
    // Listen for tree view updates
    ws.on('message', (data) => {
        const msg = JSON.parse(data);
        if (msg.type === 'TREEVIEW_STORE_UPDATE') {
            console.log('\n📊 TreeView Update:');
            console.log('  Selected Machine:', msg.store?.selectedMachineId || 'null');
            console.log('  Transitions Count:', msg.store?.transitions?.length || 0);
            
            if (msg.store?.transitions && msg.store.transitions.length > 0) {
                console.log('  Transitions:');
                msg.store.transitions.forEach(t => {
                    console.log(`    Step ${t.stepNumber}: ${t.fromState} → ${t.toState} (${t.event})`);
                });
            }
        }
    });
    
    const machineId = 'call-002';
    
    // Step 1: Select machine for tree view
    console.log('Step 1: Selecting machine call-002...');
    ws.send(JSON.stringify({
        type: 'TREEVIEW_ACTION',
        action: 'SELECT_MACHINE',
        payload: { machineId: machineId },
        timestamp: new Date().toISOString()
    }));
    await sleep(1000);
    
    // Step 2: Send INCOMING_CALL
    console.log('\nStep 2: Sending INCOMING_CALL (should go IDLE → RINGING)...');
    ws.send(JSON.stringify({
        type: 'SEND_EVENT',
        machineId: machineId,
        eventType: 'INCOMING_CALL',
        payload: { phoneNumber: '+1-555-1234' }
    }));
    await sleep(2000);
    
    // Step 3: Send ANSWER
    console.log('\nStep 3: Sending ANSWER (should go RINGING → CONNECTED)...');
    ws.send(JSON.stringify({
        type: 'SEND_EVENT',
        machineId: machineId,
        eventType: 'ANSWER',
        payload: {}
    }));
    await sleep(2000);
    
    console.log('\n' + '='.repeat(60));
    console.log('  EXPECTED: Should see 3 transitions in tree view:');
    console.log('  1. Initial → IDLE');
    console.log('  2. IDLE → RINGING');
    console.log('  3. RINGING → CONNECTED');
    console.log('='.repeat(60) + '\n');
    
    ws.close();
    process.exit(0);
}

testSimpleTransitions().catch(console.error);