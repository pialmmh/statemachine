const WebSocket = require('ws');

const ws = new WebSocket('ws://localhost:9999');

ws.on('open', function open() {
  console.log('Connected to WebSocket server');
  
  // Send INCOMING_CALL event to call-003
  const event = {
    type: 'EVENT',
    machineId: 'call-003',
    eventType: 'INCOMING_CALL',
    payload: { phoneNumber: '+1-555-TEST' }
  };
  
  console.log('Sending:', JSON.stringify(event));
  ws.send(JSON.stringify(event));
  
  // Request machine state after a delay
  setTimeout(() => {
    const stateRequest = {
      action: 'GET_MACHINE_STATE',
      machineId: 'call-003'
    };
    console.log('Requesting state:', JSON.stringify(stateRequest));
    ws.send(JSON.stringify(stateRequest));
    
    // Close after another delay
    setTimeout(() => {
      ws.close();
    }, 1000);
  }, 1000);
});

ws.on('message', function message(data) {
  const msg = JSON.parse(data.toString());
  if (msg.type === 'STATE_CHANGE') {
    console.log(`State changed: ${msg.fromState} -> ${msg.toState}`);
  } else if (msg.type === 'EVENT_FIRED') {
    console.log(`Event fired: ${msg.eventType} (${msg.oldState} -> ${msg.newState})`);
  } else if (msg.type === 'MACHINE_STATE') {
    console.log(`Machine ${msg.machineId} is in state: ${msg.currentState}`);
  } else {
    console.log('Received:', msg.type);
  }
});

ws.on('error', function error(err) {
  console.error('WebSocket error:', err);
});

ws.on('close', function close() {
  console.log('Disconnected from server');
  process.exit(0);
});