// Debug script to check treeViewStore state
const WebSocket = require('ws');

const ws = new WebSocket('ws://localhost:9999');

ws.on('open', function open() {
    console.log('Connected to WebSocket');
});

ws.on('message', function message(data) {
    const msg = JSON.parse(data.toString());
    if (msg.type === 'TREEVIEW_STORE_UPDATE') {
        console.log('\n=== TREEVIEW_STORE_UPDATE ===');
        console.log('Store version:', msg.store.version);
        console.log('stateInstances array:', JSON.stringify(msg.store.stateInstances, null, 2));
        
        // Simulate what treeViewStore.replaceStore does
        const state = {
            stateInstances: msg.store.stateInstances || [],
            selectedTransition: msg.store.selectedTransition || null,
            expandedStates: msg.store.expandedStates || [],
            selectedMachineId: msg.store.selectedMachineId || null,
            lastUpdate: msg.store.lastUpdate || null,
            version: msg.store.version || 0,
            timestamp: msg.store.timestamp || null
        };
        
        console.log('\n=== After replaceStore ===');
        console.log('state.stateInstances:', JSON.stringify(state.stateInstances, null, 2));
        
        ws.close();
        process.exit(0);
    }
});

ws.on('error', function error(err) {
    console.error('WebSocket error:', err);
    process.exit(1);
});
