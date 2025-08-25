#!/usr/bin/env node

const WebSocket = require('ws');

async function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function testRegistryCounts() {
    console.log('\n📊 Testing Registry Machine Counts\n');
    
    const ws = new WebSocket('ws://localhost:9999');
    
    await new Promise((resolve, reject) => {
        ws.on('open', resolve);
        ws.on('error', reject);
    });
    
    console.log('✅ Connected to WebSocket\n');
    
    // Listen for responses
    ws.on('message', (data) => {
        const msg = JSON.parse(data);
        
        if (msg.type === 'MACHINES_LIST') {
            console.log('📋 MACHINES_LIST Response:');
            console.log('   Total machines in array:', msg.machines?.length || 0);
            if (msg.machines) {
                msg.machines.forEach(m => {
                    console.log(`   - ${m.id} (${m.type})`);
                });
            }
        }
        
        if (msg.type === 'OFFLINE_MACHINES_LIST') {
            console.log('\n📋 OFFLINE_MACHINES_LIST Response:');
            console.log('   Total offline machines:', msg.machines?.length || 0);
            if (msg.machines && msg.machines.length > 0) {
                msg.machines.forEach(m => {
                    console.log(`   - ${m.id} (${m.type}) - State: ${m.state}`);
                });
            }
        }
        
        if (msg.type === 'REGISTRY_STATE') {
            console.log('\n📋 REGISTRY_STATE Response:');
            console.log('   Debug Mode:', msg.debugMode);
            console.log('   Machine Count (from registry.getActiveMachineCount()):', msg.machineCount);
            console.log('   Active Machines:', msg.activeMachines?.length || 0);
            console.log('   Offline Machines:', msg.offlineMachines?.length || 0);
            if (msg.activeMachines) {
                console.log('   Active machine details:');
                msg.activeMachines.forEach(m => {
                    console.log(`     - ${m.id}: ${m.currentState} (${m.online ? 'online' : 'offline'})`);
                });
            }
        }
    });
    
    // Request machine lists
    console.log('1️⃣ Requesting GET_MACHINES...');
    ws.send(JSON.stringify({
        action: 'GET_MACHINES',
        timestamp: new Date().toISOString()
    }));
    await sleep(500);
    
    console.log('\n2️⃣ Requesting GET_OFFLINE_MACHINES...');
    ws.send(JSON.stringify({
        action: 'GET_OFFLINE_MACHINES',
        timestamp: new Date().toISOString()
    }));
    await sleep(500);
    
    console.log('\n3️⃣ Requesting GET_REGISTRY_STATE...');
    ws.send(JSON.stringify({
        action: 'GET_REGISTRY_STATE',
        timestamp: new Date().toISOString()
    }));
    await sleep(1000);
    
    console.log('\n' + '='.repeat(60));
    console.log('Registry Count Verification Complete');
    console.log('The UI should display the counts from these responses.');
    console.log('='.repeat(60) + '\n');
    
    ws.close();
    process.exit(0);
}

testRegistryCounts().catch(console.error);