#!/usr/bin/env node

const WebSocket = require('ws');

async function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function quickDemo() {
    console.log('\n' + '='.repeat(60));
    console.log('  QUICK REHYDRATION + TIMEOUT DEMONSTRATION');
    console.log('='.repeat(60) + '\n');
    
    const ws = new WebSocket('ws://localhost:9999');
    
    await new Promise((resolve, reject) => {
        ws.on('open', resolve);
        ws.on('error', reject);
    });
    
    console.log('✅ Connected to WebSocket server\n');
    
    // Listen for responses
    ws.on('message', (data) => {
        const msg = JSON.parse(data);
        if (msg.type === 'EVENT_FIRED') {
            console.log(`  📨 State Change: ${msg.machineId} [${msg.oldState} → ${msg.newState}]`);
        }
    });
    
    const machineId = 'call-002';
    
    // Step 1: Move to CONNECTED (offline)
    console.log('Step 1: Moving call-002 to CONNECTED (offline) state...');
    
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
    
    // Step 2: Wait for timeout
    console.log('Step 2: Waiting 35 seconds for timeout to expire...');
    console.log('  (Feel free to check the UI - machine should be in offline dropdown)\n');
    
    for (let i = 35; i > 0; i--) {
        process.stdout.write(`\r  Time remaining: ${i} seconds...  `);
        await sleep(1000);
    }
    
    console.log('\n\n✅ Timeout period has expired\n');
    
    // Step 3: Trigger rehydration
    console.log('Step 3: Sending event to trigger rehydration...');
    
    ws.send(JSON.stringify({
        type: 'EVENT_TO_ARBITRARY',
        machineId: machineId,
        eventType: 'HANGUP',
        payload: {}
    }));
    
    await sleep(2000);
    
    console.log('\n' + '='.repeat(60));
    console.log('  EXPECTED RESULT:');
    console.log('  - Machine should have timed out during rehydration');
    console.log('  - Final state should be IDLE');
    console.log('  - Machine should be back online');
    console.log('='.repeat(60) + '\n');
    
    console.log('Check the backend logs for:');
    console.log('  "Timeout check for state CONNECTED: elapsed=35000ms, timeout=30000ms"');
    console.log('  "⏰ State CONNECTED has timed out after 35000ms. Transitioning to: IDLE"');
    console.log('  "Machine call-002 coming back online"\n');
    
    ws.close();
    process.exit(0);
}

quickDemo().catch(console.error);