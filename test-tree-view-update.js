#!/usr/bin/env node

const WebSocket = require('ws');

async function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function testTreeViewUpdate() {
    console.log('\n' + '='.repeat(60));
    console.log('  TESTING TREE VIEW UPDATE WITH MACHINE SELECTION');
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
        if (msg.type === 'TREEVIEW_STORE_UPDATE') {
            console.log('\n📊 TreeView Store Update Received:');
            console.log('  Selected Machine:', msg.store?.selectedMachineId || 'null');
            console.log('  Transitions Count:', msg.store?.transitions?.length || 0);
            console.log('  Version:', msg.store?.version || 0);
            
            if (msg.store?.transitions && msg.store.transitions.length > 0) {
                console.log('\n  Transitions:');
                msg.store.transitions.forEach(t => {
                    console.log(`    Step ${t.stepNumber}: ${t.fromState} → ${t.toState} (${t.event})`);
                });
            }
        } else if (msg.type === 'EVENT_FIRED') {
            console.log(`  📨 State Change: ${msg.machineId} [${msg.oldState} → ${msg.newState}]`);
        }
    });
    
    const machineId = 'call-002';
    
    // Step 1: Select the machine for tree view
    console.log('Step 1: Selecting machine call-002 for tree view...');
    ws.send(JSON.stringify({
        type: 'TREEVIEW_ACTION',
        action: 'SELECT_MACHINE',
        payload: { machineId: machineId },
        timestamp: new Date().toISOString()
    }));
    await sleep(1000);
    
    // Step 2: Move to CONNECTED (offline) state
    console.log('\nStep 2: Moving call-002 to CONNECTED (offline) state...');
    
    ws.send(JSON.stringify({
        type: 'SEND_EVENT',
        machineId: machineId,
        eventType: 'INCOMING_CALL',
        payload: { phoneNumber: '+1-555-0000' }
    }));
    await sleep(500);
    
    ws.send(JSON.stringify({
        type: 'SEND_EVENT',
        machineId: machineId,
        eventType: 'ANSWER',
        payload: {}
    }));
    await sleep(1000);
    
    console.log('\n✅ Machine is now in CONNECTED state (offline)');
    console.log('⏰ Timeout is set to 30 seconds\n');
    
    // Step 3: Wait for timeout
    console.log('Step 3: Waiting 35 seconds for timeout to expire...\n');
    
    for (let i = 35; i > 0; i--) {
        process.stdout.write(`\r  Time remaining: ${i} seconds...  `);
        await sleep(1000);
    }
    
    console.log('\n\n✅ Timeout period has expired\n');
    
    // Step 4: Trigger rehydration
    console.log('Step 4: Sending HANGUP to trigger rehydration...');
    
    ws.send(JSON.stringify({
        type: 'EVENT_TO_ARBITRARY',
        machineId: machineId,
        eventType: 'HANGUP',
        payload: {}
    }));
    
    await sleep(3000);
    
    console.log('\n' + '='.repeat(60));
    console.log('  EXPECTED RESULT:');
    console.log('  - Machine should timeout during rehydration');
    console.log('  - Tree view should show CONNECTED → IDLE transition');
    console.log('  - Final state should be IDLE');
    console.log('  - Machine should be back online');
    console.log('='.repeat(60) + '\n');
    
    ws.close();
    process.exit(0);
}

testTreeViewUpdate().catch(console.error);