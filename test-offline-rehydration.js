const WebSocket = require('ws');

const ws = new WebSocket('ws://localhost:9999');

// Helper function to sleep
const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));

// Helper function to send event
const sendEvent = (machineId, eventType) => {
  const event = {
    action: 'FIRE_EVENT',
    payload: {
      machineId: machineId,
      event: eventType
    }
  };
  console.log(`[${new Date().toISOString()}] Sending ${eventType} to ${machineId}`);
  ws.send(JSON.stringify(event));
};

// Main test flow
ws.on('open', async function() {
  console.log('='.repeat(60));
  console.log('OFFLINE/REHYDRATION TEST SCENARIO');
  console.log('='.repeat(60));
  console.log('Connected to WebSocket server');
  console.log('');
  
  const machineId = 'call-test-001';
  
  console.log('PHASE 1: Normal Call Flow');
  console.log('-'.repeat(40));
  
  // Start a call
  sendEvent(machineId, 'INCOMING_CALL');
  await sleep(1000);
  
  // Answer the call
  sendEvent(machineId, 'ANSWER');
  await sleep(1000);
  
  console.log('');
  console.log('PHASE 2: Test Timeout-based Offline (2 minutes)');
  console.log('-'.repeat(40));
  console.log('Call is in CONNECTED state.');
  console.log('Waiting for 2-minute timeout to trigger...');
  console.log('(This would transition to IDLE and potentially offline)');
  console.log('');
  
  // Wait 10 seconds then hangup to demonstrate manual offline
  await sleep(10000);
  
  console.log('Hanging up call manually...');
  sendEvent(machineId, 'HANGUP');
  await sleep(2000);
  
  console.log('');
  console.log('PHASE 3: Test Rehydration');
  console.log('-'.repeat(40));
  console.log('Machine should now be in IDLE state.');
  console.log('Sending new INCOMING_CALL to test if machine can be reused...');
  
  // Try to send another call to the same machine
  sendEvent(machineId, 'INCOMING_CALL');
  await sleep(2000);
  
  console.log('');
  console.log('='.repeat(60));
  console.log('TEST COMPLETE');
  console.log('Check the UI to see state transitions and offline behavior');
  console.log('='.repeat(60));
  
  await sleep(2000);
  process.exit(0);
});

ws.on('message', function(data) {
  const msg = JSON.parse(data.toString());
  if (msg.type === 'STATE_UPDATE') {
    console.log(`[${new Date().toISOString()}] State update: Machine ${msg.machineId} -> ${msg.currentState}`);
  } else if (msg.type === 'MACHINE_REGISTERED') {
    console.log(`[${new Date().toISOString()}] Machine registered: ${msg.machineId}`);
  } else if (msg.type === 'MACHINE_UNREGISTERED') {
    console.log(`[${new Date().toISOString()}] Machine went offline: ${msg.machineId}`);
  }
});

ws.on('error', function(err) {
  console.error('WebSocket error:', err);
});