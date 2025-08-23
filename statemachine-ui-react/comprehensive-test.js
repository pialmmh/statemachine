const WebSocket = require('ws');

console.log('=== Comprehensive Test of State Grouping ===\n');

// Step 1: Connect and get initial state
const ws = new WebSocket('ws://localhost:9999');
let updateCount = 0;

ws.on('open', function open() {
    console.log('✅ Connected to WebSocket');
});

ws.on('message', function message(data) {
    const msg = JSON.parse(data.toString());
    if (msg.type === 'TREEVIEW_STORE_UPDATE') {
        updateCount++;
        console.log(`\n=== Update #${updateCount} (version ${msg.store?.version}) ===`);
        
        const instances = msg.store?.stateInstances || [];
        console.log(`State Instances: ${instances.length}`);
        
        instances.forEach((instance, idx) => {
            console.log(`\nInstance ${idx + 1}:`);
            console.log(`  State: ${instance.state}`);
            console.log(`  Instance Number: ${instance.instanceNumber}`);
            console.log(`  Transitions: ${instance.transitions?.length || 0}`);
            
            // List all machines in this instance
            const machines = new Set();
            instance.transitions?.forEach(t => {
                machines.add(t.machineId);
            });
            console.log(`  Machines: ${Array.from(machines).join(', ')}`);
            
            // Show transition details
            if (instance.transitions) {
                console.log('  Transition details:');
                instance.transitions.forEach(t => {
                    console.log(`    - ${t.event}: ${t.fromState} → ${t.toState} (${t.machineId})`);
                });
            }
        });
        
        // Analysis
        console.log('\n=== Analysis ===');
        const idleInstances = instances.filter(i => i.state === 'IDLE');
        if (idleInstances.length === 0) {
            console.log('⚠️  No IDLE states found');
        } else if (idleInstances.length === 1) {
            const idle = idleInstances[0];
            const machineCount = new Set(idle.transitions?.map(t => t.machineId) || []).size;
            console.log(`✅ Correct: 1 IDLE state instance with ${machineCount} machines`);
        } else {
            console.log(`❌ Problem: ${idleInstances.length} separate IDLE state instances`);
            console.log('This is what the user is complaining about!');
        }
        
        // Only exit after first update to see initial state
        if (updateCount === 1) {
            setTimeout(() => {
                console.log('\n=== Test Complete ===');
                ws.close();
                process.exit(0);
            }, 1000);
        }
    }
});

ws.on('error', function error(err) {
    console.error('WebSocket error:', err);
    process.exit(1);
});

setTimeout(() => {
    console.log('\nTimeout: No updates received');
    ws.close();
    process.exit(1);
}, 5000);