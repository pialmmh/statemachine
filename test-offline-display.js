const WebSocket = require('ws');

// Helper function to sleep
const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));

console.log('='.repeat(60));
console.log('OFFLINE STATUS DISPLAY TEST');
console.log('='.repeat(60));
console.log('This test will trigger offline states and verify they');
console.log('are displayed in the UI tree view with "OFFLINE" badge');
console.log('='.repeat(60));
console.log('');

async function runTest() {
  // Wait for backend to start
  console.log('Waiting for backend to start...');
  await sleep(5000);
  
  // Connect WebSocket client
  console.log('Connecting WebSocket client...');
  const ws = new WebSocket('ws://localhost:9999');
  
  await new Promise((resolve) => {
    ws.on('open', () => {
      console.log('✓ Client connected');
      resolve();
    });
  });
  
  ws.on('message', (data) => {
    const msg = JSON.parse(data.toString());
    if (msg.type === 'TREEVIEW_STORE_UPDATE') {
      const store = msg.store;
      if (store.transitions) {
        const offlineTransitions = store.transitions.filter(t => t.isOffline);
        if (offlineTransitions.length > 0) {
          console.log('');
          console.log('🔴 OFFLINE STATE DETECTED IN TREE VIEW!');
          offlineTransitions.forEach(t => {
            console.log(`   State: ${t.toState} marked as OFFLINE`);
          });
        }
      }
    }
  });
  
  // Create a test machine
  const machineId = 'test-offline-display-001';
  
  console.log('');
  console.log('PHASE 1: Create machine and transition through states');
  console.log('-'.repeat(40));
  
  // Start with INCOMING_CALL
  console.log('Sending INCOMING_CALL to create machine...');
  ws.send(JSON.stringify({
    action: 'FIRE_EVENT',
    payload: {
      machineId: machineId,
      event: 'INCOMING_CALL'
    }
  }));
  
  await sleep(1000);
  
  // Answer the call
  console.log('Sending ANSWER to transition to CONNECTED...');
  ws.send(JSON.stringify({
    action: 'FIRE_EVENT',
    payload: {
      machineId: machineId,
      event: 'ANSWER'
    }
  }));
  
  await sleep(1000);
  
  // Hangup to go to IDLE
  console.log('Sending HANGUP to transition to IDLE...');
  ws.send(JSON.stringify({
    action: 'FIRE_EVENT',
    payload: {
      machineId: machineId,
      event: 'HANGUP'
    }
  }));
  
  await sleep(2000);
  
  console.log('');
  console.log('PHASE 2: Check UI for offline display');
  console.log('-'.repeat(40));
  console.log('If IDLE is configured as offline state:');
  console.log('1. Check the React UI at http://localhost:4001');
  console.log('2. Select machine: ' + machineId);
  console.log('3. Look for "IDLE" state with red "OFFLINE" badge');
  console.log('4. The state should show as: IDLE [OFFLINE]');
  
  await sleep(3000);
  
  // Try another call to test rehydration
  console.log('');
  console.log('PHASE 3: Test with another call (potential rehydration)');
  console.log('-'.repeat(40));
  console.log('Sending another INCOMING_CALL...');
  
  ws.send(JSON.stringify({
    action: 'FIRE_EVENT', 
    payload: {
      machineId: machineId,
      event: 'INCOMING_CALL'
    }
  }));
  
  await sleep(2000);
  
  console.log('');
  console.log('='.repeat(60));
  console.log('TEST COMPLETE');
  console.log('');
  console.log('Expected Results in UI:');
  console.log('1. States that lead to offline should show red "OFFLINE" badge');
  console.log('2. Backend logs should show "[History] Machine marked as offline"');
  console.log('3. Tree view should display offline states distinctly');
  console.log('='.repeat(60));
  
  ws.close();
}

runTest().catch(console.error).finally(() => process.exit(0));