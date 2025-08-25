const WebSocket = require('ws');

// Helper function to sleep
const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));

console.log('='.repeat(60));
console.log('OFFLINE DEBUG CACHE TEST');
console.log('='.repeat(60));
console.log('This test verifies that offline machines are kept in debug');
console.log('cache while WebSocket clients are connected.');
console.log('='.repeat(60));
console.log('');

async function runTest() {
  // Wait for backend to start
  console.log('Waiting for backend to start...');
  await sleep(3000);
  
  // Connect first WebSocket client
  console.log('Connecting WebSocket client 1...');
  const ws1 = new WebSocket('ws://localhost:9999');
  
  await new Promise((resolve) => {
    ws1.on('open', () => {
      console.log('✓ Client 1 connected');
      resolve();
    });
  });
  
  // Send events to create a machine
  const machineId = 'test-offline-001';
  console.log(`\nCreating machine ${machineId}...`);
  
  ws1.send(JSON.stringify({
    action: 'FIRE_EVENT',
    payload: {
      machineId: machineId,
      event: 'INCOMING_CALL'
    }
  }));
  
  await sleep(1000);
  
  // The machine should now be in RINGING state
  console.log('Machine created and in RINGING state');
  
  // Wait for timeout (30 seconds) to trigger offline transition
  // For testing, we'll manually send it to IDLE which should be offline
  console.log('\nSending HANGUP to trigger transition to IDLE (if configured as offline)...');
  
  ws1.send(JSON.stringify({
    action: 'FIRE_EVENT',
    payload: {
      machineId: machineId,
      event: 'HANGUP'
    }
  }));
  
  await sleep(2000);
  
  console.log('\nMachine should now be offline but kept in debug cache.');
  console.log('(Check backend logs for: "moved to offline debug cache")');
  
  // Connect second WebSocket client
  console.log('\nConnecting WebSocket client 2...');
  const ws2 = new WebSocket('ws://localhost:9999');
  
  await new Promise((resolve) => {
    ws2.on('open', () => {
      console.log('✓ Client 2 connected');
      resolve();
    });
  });
  
  console.log('Both clients connected - offline machines should remain in cache');
  
  // Disconnect first client
  console.log('\nDisconnecting client 1...');
  ws1.close();
  await sleep(1000);
  
  console.log('Client 1 disconnected - client 2 still connected');
  console.log('Offline machines should still be in cache');
  
  // Try to send event to offline machine
  console.log('\nTrying to send event to offline machine...');
  ws2.send(JSON.stringify({
    action: 'FIRE_EVENT',
    payload: {
      machineId: machineId,
      event: 'INCOMING_CALL'
    }
  }));
  
  await sleep(1000);
  
  // Disconnect last client
  console.log('\nDisconnecting client 2 (last client)...');
  ws2.close();
  await sleep(1000);
  
  console.log('All clients disconnected!');
  console.log('(Check backend logs for: "clearing offline machines for debug")');
  
  // Connect new client to verify cache was cleared
  console.log('\nConnecting new client to verify cache was cleared...');
  const ws3 = new WebSocket('ws://localhost:9999');
  
  await new Promise((resolve) => {
    ws3.on('open', () => {
      console.log('✓ Client 3 connected');
      resolve();
    });
  });
  
  console.log('\nTrying to access previously offline machine...');
  ws3.send(JSON.stringify({
    action: 'FIRE_EVENT',
    payload: {
      machineId: machineId,
      event: 'INCOMING_CALL'
    }
  }));
  
  await sleep(1000);
  
  console.log('If cache was cleared, machine should be rehydrated from persistence');
  console.log('(or created new if no persistence)');
  
  ws3.close();
  
  console.log('\n' + '='.repeat(60));
  console.log('TEST COMPLETE');
  console.log('Check backend logs for offline cache behavior');
  console.log('='.repeat(60));
}

runTest().catch(console.error).finally(() => process.exit(0));