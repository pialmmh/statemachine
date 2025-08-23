const WebSocket = require('ws');

const ws = new WebSocket('ws://localhost:9999');

ws.on('open', function open() {
  console.log('Connected to WebSocket');
  
  // Fire INCOMING_CALL to call-001 to transition from IDLE to RINGING
  const event = {
    action: 'FIRE_EVENT',
    payload: {
      machineId: 'call-001',
      event: 'INCOMING_CALL'
    }
  };
  
  console.log('Sending INCOMING_CALL event to call-001...');
  ws.send(JSON.stringify(event));
  
  // Wait a second then send ANSWER to transition from RINGING to CONNECTED  
  setTimeout(() => {
    const answerEvent = {
      action: 'FIRE_EVENT',
      payload: {
        machineId: 'call-001',
        event: 'ANSWER'
      }
    };
    console.log('Sending ANSWER event to call-001...');
    ws.send(JSON.stringify(answerEvent));
    
    // Wait another second then send HANGUP to transition from CONNECTED to IDLE
    setTimeout(() => {
      const hangupEvent = {
        action: 'FIRE_EVENT',
        payload: {
          machineId: 'call-001',
          event: 'HANGUP'
        }
      };
      console.log('Sending HANGUP event to call-001...');
      ws.send(JSON.stringify(hangupEvent));
      
      setTimeout(() => {
        console.log('Test complete!');
        process.exit(0);
      }, 1000);
    }, 1000);
  }, 1000);
});

ws.on('message', function message(data) {
  const msg = JSON.parse(data.toString());
  if (msg.type === 'STATE_UPDATE') {
    console.log(`State update: Machine ${msg.machineId} is now in ${msg.currentState}`);
  }
});

ws.on('error', function error(err) {
  console.error('WebSocket error:', err);
});