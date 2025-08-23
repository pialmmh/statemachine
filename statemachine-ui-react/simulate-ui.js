const WebSocket = require('ws');

// Simulate treeViewStore
class TreeViewStore {
  constructor() {
    this.state = {
      stateInstances: [],
      version: 0
    };
  }
  
  replaceStore(backendStore) {
    console.log('[TreeViewStore] Replacing store with backend update, version:', backendStore.version);
    console.log('[TreeViewStore] Received stateInstances count:', backendStore.stateInstances?.length || 0);
    
    if (backendStore.stateInstances && backendStore.stateInstances.length > 0) {
      console.log('[TreeViewStore] First state instance:');
      const first = backendStore.stateInstances[0];
      console.log('  State:', first.state);
      console.log('  Instance Number:', first.instanceNumber);
      console.log('  Transitions:', first.transitions?.length || 0);
    }
    
    this.state = {
      stateInstances: backendStore.stateInstances || [],
      version: backendStore.version || 0
    };
    
    console.log('[TreeViewStore] State after update, count:', this.state.stateInstances.length);
    
    // Simulate what would be passed to StateTreeView
    this.simulateRender();
  }
  
  simulateRender() {
    console.log('\n=== Simulating StateTreeView render ===');
    console.log('[StateTreeView] Rendering with', this.state.stateInstances.length, 'state instances');
    
    // This is what StateTreeView does - it maps over stateInstances
    this.state.stateInstances.forEach((instance, idx) => {
      console.log('\nRendering state ' + (idx + 1) + ':');
      console.log('  State:', instance.state);
      console.log('  Instance Number:', instance.instanceNumber);
      console.log('  Transitions:', (instance.transitions?.length || 0));
      console.log('  Key:', instance.state + '-' + instance.instanceNumber + '-' + idx);
    });
  }
}

const treeViewStore = new TreeViewStore();
const ws = new WebSocket('ws://localhost:9999');

ws.on('open', function open() {
    console.log('Connected to WebSocket (simulating UI)');
});

ws.on('message', function message(data) {
    const msg = JSON.parse(data.toString());
    if (msg.type === 'TREEVIEW_STORE_UPDATE') {
        console.log('\n=== TREEVIEW_STORE_UPDATE received ===');
        treeViewStore.replaceStore(msg.store);
        
        // Check if this matches what we expect
        console.log('\n=== Analysis ===');
        if (treeViewStore.state.stateInstances.length === 1) {
          console.log('✅ Correct: Only 1 state instance in store');
        } else {
          console.log('❌ Problem: ' + treeViewStore.state.stateInstances.length + ' state instances in store');
        }
        
        ws.close();
        process.exit(0);
    }
});

ws.on('error', function error(err) {
    console.error('WebSocket error:', err);
    process.exit(1);
});

setTimeout(() => {
    console.log('Timeout waiting for TREEVIEW_STORE_UPDATE');
    ws.close();
    process.exit(1);
}, 5000);