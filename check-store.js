const WebSocket = require('ws');

const ws = new WebSocket('ws://localhost:9999');

ws.on('open', function open() {
    console.log('Connected to WebSocket');
});

ws.on('message', function message(data) {
    const msg = JSON.parse(data.toString());
    if (msg.type === 'TREEVIEW_STORE_UPDATE') {
        console.log('\n=== TREEVIEW_STORE_UPDATE ===');
        console.log('Version:', msg.store?.version);
        console.log('State Instances:', msg.store?.stateInstances?.length);
        if (msg.store?.stateInstances) {
            msg.store.stateInstances.forEach((instance, idx) => {
                console.log(`\nInstance ${idx + 1}:`);
                console.log(`  State: ${instance.state}`);
                console.log(`  Instance Number: ${instance.instanceNumber}`);
                console.log(`  Transitions: ${instance.transitions?.length || 0}`);
                if (instance.transitions) {
                    instance.transitions.forEach(t => {
                        console.log(`    - ${t.event}: ${t.fromState} → ${t.toState} (${t.machineId})`);
                    });
                }
            });
        }
        ws.close();
        process.exit(0);
    }
});

ws.on('error', function error(err) {
    console.error('WebSocket error:', err);
    process.exit(1);
});