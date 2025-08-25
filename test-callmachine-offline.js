#!/usr/bin/env node

const WebSocket = require('ws');

async function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function testCallMachineOfflineTimeout() {
    console.log('\n' + '='.repeat(70));
    console.log('  TESTING CALLMACHINERUNNERPROPER WITH OFFLINE CONNECTED STATE');
    console.log('='.repeat(70) + '\n');
    
    const ws = new WebSocket('ws://localhost:9999');
    
    await new Promise((resolve, reject) => {
        ws.on('open', resolve);
        ws.on('error', reject);
    });
    
    console.log('✅ Connected to WebSocket server\n');
    
    // Listen for all messages
    ws.on('message', (data) => {
        const msg = JSON.parse(data);
        if (msg.type === 'EVENT_FIRED') {
            console.log(`  📨 [${new Date().toISOString().split('T')[1].split('.')[0]}] State Change: ${msg.machineId} [${msg.oldState} → ${msg.newState}]`);
        } else if (msg.type === 'STATE_CHANGE') {
            console.log(`  📊 [${new Date().toISOString().split('T')[1].split('.')[0]}] State Update: ${msg.machineId} is now in ${msg.newState}`);
        }
    });
    
    // Test all three machines
    const machines = ['call-001', 'call-002', 'call-003'];
    
    for (const machineId of machines) {
        console.log(`\n${'='.repeat(50)}`);
        console.log(`  TESTING MACHINE: ${machineId}`);
        console.log('='.repeat(50) + '\n');
        
        // Step 1: Send INCOMING_CALL to move to RINGING
        console.log(`Step 1: Sending INCOMING_CALL to ${machineId}...`);
        ws.send(JSON.stringify({
            type: 'SEND_EVENT',
            machineId: machineId,
            eventType: 'INCOMING_CALL',
            payload: { phoneNumber: `+1-555-${machineId.split('-')[1]}` }
        }));
        await sleep(1000);
        
        // Step 2: Send ANSWER to move to CONNECTED (offline)
        console.log(`Step 2: Sending ANSWER to ${machineId} (will go to CONNECTED/offline)...`);
        ws.send(JSON.stringify({
            type: 'SEND_EVENT',
            machineId: machineId,
            eventType: 'ANSWER',
            payload: {}
        }));
        await sleep(1000);
        
        console.log(`\n✅ ${machineId} is now in CONNECTED state (offline)`);
        console.log('   The machine should now be:');
        console.log('   - Removed from active registry');
        console.log('   - Stored in offline cache');
        console.log('   - Available in "Select Offline Machine" dropdown in UI\n');
    }
    
    console.log('\n' + '='.repeat(70));
    console.log('  ALL MACHINES ARE NOW OFFLINE IN CONNECTED STATE');
    console.log('='.repeat(70) + '\n');
    
    console.log('⏰ Waiting 35 seconds for timeout to expire (timeout is 30s)...\n');
    
    // Show countdown
    for (let i = 35; i > 0; i--) {
        process.stdout.write(`\r  Time remaining: ${i} seconds...  `);
        await sleep(1000);
    }
    
    console.log('\n\n✅ Timeout period has expired for all machines\n');
    
    // Step 3: Test rehydration with different events
    console.log('Step 3: Testing rehydration and timeout for each machine...\n');
    
    // Test call-001 with HANGUP
    console.log('Testing call-001 with HANGUP event:');
    ws.send(JSON.stringify({
        type: 'EVENT_TO_ARBITRARY',
        machineId: 'call-001',
        eventType: 'HANGUP',
        payload: {}
    }));
    await sleep(1000);
    
    // Test call-002 with SESSION_PROGRESS
    console.log('\nTesting call-002 with SESSION_PROGRESS event:');
    ws.send(JSON.stringify({
        type: 'EVENT_TO_ARBITRARY',
        machineId: 'call-002',
        eventType: 'SESSION_PROGRESS',
        payload: { sessionData: 'test' }
    }));
    await sleep(1000);
    
    // Test call-003 with another HANGUP
    console.log('\nTesting call-003 with HANGUP event:');
    ws.send(JSON.stringify({
        type: 'EVENT_TO_ARBITRARY',
        machineId: 'call-003',
        eventType: 'HANGUP',
        payload: {}
    }));
    await sleep(2000);
    
    console.log('\n' + '='.repeat(70));
    console.log('  EXPECTED RESULTS:');
    console.log('='.repeat(70));
    console.log('  ✅ All machines should have timed out during rehydration');
    console.log('  ✅ Final state for all machines should be IDLE');
    console.log('  ✅ All machines should be back online (in active registry)');
    console.log('  ✅ UI should show all machines in "Select Machine" dropdown');
    console.log('  ✅ "Select Offline Machine" dropdown should be empty');
    console.log('='.repeat(70) + '\n');
    
    console.log('📋 Check the backend logs for these messages:');
    console.log('  - "🔄 REHYDRATION SUCCESSFUL"');
    console.log('  - "Timeout check for state CONNECTED: elapsed=35000ms, timeout=30000ms"');
    console.log('  - "⏰ State CONNECTED has timed out after 35000ms. Transitioning to: IDLE"');
    console.log('  - "StateMachine [id] coming back online from CONNECTED to IDLE"');
    console.log('  - "[Registry] Machine [id] brought back online"');
    console.log('\n');
    
    // Optional: Query current state of machines
    console.log('Querying final state of all machines...\n');
    for (const machineId of machines) {
        ws.send(JSON.stringify({
            action: 'GET_MACHINE_STATE',
            machineId: machineId
        }));
        await sleep(100);
    }
    
    await sleep(2000);
    
    ws.close();
    console.log('Test completed!\n');
    process.exit(0);
}

testCallMachineOfflineTimeout().catch(console.error);