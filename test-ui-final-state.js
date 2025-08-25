#!/usr/bin/env node

const WebSocket = require('ws');

async function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function testUIFinalState() {
    console.log('\n' + '='.repeat(60));
    console.log('  TESTING UI SHOWS FINAL IDLE STATE AFTER HANGUP');
    console.log('='.repeat(60) + '\n');
    
    const ws = new WebSocket('ws://localhost:9999');
    
    await new Promise((resolve, reject) => {
        ws.on('open', resolve);
        ws.on('error', reject);
    });
    
    console.log('✅ Connected to WebSocket server\n');
    
    // Track the tree view structure
    let lastTreeStructure = null;
    
    ws.on('message', (data) => {
        const msg = JSON.parse(data);
        if (msg.type === 'TREEVIEW_STORE_UPDATE' && msg.store?.transitions) {
            // Build a tree structure representation
            const transitions = msg.store.transitions || [];
            const states = {};
            let currentState = null;
            
            transitions.forEach(t => {
                if (t.event === 'Start' || t.event === 'Entry') {
                    // Entry into a state
                    if (!states[t.toState]) {
                        states[t.toState] = { entries: 0, transitions: [] };
                    }
                    states[t.toState].entries++;
                    currentState = t.toState;
                } else if (t.fromState !== t.toState) {
                    // Transition from one state to another
                    if (!states[t.fromState]) {
                        states[t.fromState] = { entries: 0, transitions: [] };
                    }
                    states[t.fromState].transitions.push({
                        event: t.event,
                        to: t.toState,
                        step: t.stepNumber
                    });
                    
                    // Check if this creates a new state entry
                    if (t.toState !== currentState) {
                        if (!states[t.toState]) {
                            states[t.toState] = { entries: 0, transitions: [] };
                        }
                        states[t.toState].entries++;
                        currentState = t.toState;
                    }
                }
            });
            
            // Print tree structure
            console.log('\n📊 Tree Structure:');
            Object.keys(states).forEach(state => {
                const info = states[state];
                console.log(`  ${state} (entered ${info.entries} time${info.entries !== 1 ? 's' : ''}):`);
                info.transitions.forEach(t => {
                    console.log(`    └─ ${t.event} → ${t.to} (Step #${t.step})`);
                });
            });
            
            lastTreeStructure = states;
        }
    });
    
    const machineId = 'call-001'; // Use a different machine for clean test
    
    // Step 1: Select machine
    console.log('\nStep 1: Selecting machine call-001...');
    ws.send(JSON.stringify({
        type: 'TREEVIEW_ACTION',
        action: 'SELECT_MACHINE',
        payload: { machineId: machineId },
        timestamp: new Date().toISOString()
    }));
    await sleep(1000);
    
    // Step 2: IDLE → RINGING
    console.log('\nStep 2: IDLE → RINGING...');
    ws.send(JSON.stringify({
        type: 'EVENT_TO_ARBITRARY',
        machineId: machineId,
        eventType: 'INCOMING_CALL',
        payload: { phoneNumber: '+1-555-9999' }
    }));
    await sleep(1500);
    
    // Step 3: RINGING → CONNECTED
    console.log('\nStep 3: RINGING → CONNECTED...');
    ws.send(JSON.stringify({
        type: 'EVENT_TO_ARBITRARY',
        machineId: machineId,
        eventType: 'ANSWER',
        payload: {}
    }));
    await sleep(1500);
    
    // Step 4: CONNECTED → IDLE via HANGUP
    console.log('\nStep 4: CONNECTED → IDLE (HANGUP)...');
    ws.send(JSON.stringify({
        type: 'EVENT_TO_ARBITRARY',
        machineId: machineId,
        eventType: 'HANGUP',
        payload: {}
    }));
    await sleep(2000);
    
    // Verify final state
    console.log('\n' + '='.repeat(60));
    console.log('  VERIFICATION:');
    console.log('='.repeat(60));
    
    if (lastTreeStructure) {
        const idleEntries = lastTreeStructure['IDLE']?.entries || 0;
        if (idleEntries >= 2) {
            console.log('✅ SUCCESS: IDLE state appears twice in tree');
            console.log('   - First entry: Initial state');
            console.log('   - Second entry: After HANGUP from CONNECTED');
            console.log('\n✅ UI correctly shows the final IDLE state!');
        } else {
            console.log('❌ ISSUE: IDLE state only appears once');
            console.log('   The UI may not be showing the final IDLE state after HANGUP');
        }
    }
    
    console.log('='.repeat(60) + '\n');
    
    ws.close();
    process.exit(0);
}

testUIFinalState().catch(console.error);