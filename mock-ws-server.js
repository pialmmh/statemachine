const WebSocket = require('ws');

// Create WebSocket server on port 9999
const wss = new WebSocket.Server({ port: 9999 });

console.log('🚀 Mock WebSocket Server started on port 9999');
console.log('📊 Ready to accept connections from React UI');

// Sample state machine data
const machines = {
    'call-001': {
        id: 'call-001',
        currentState: 'RINGING',
        previousState: 'ADMISSION',
        lastEvent: 'INCOMING_CALL',
        persistentContext: {
            callId: 'call-001',
            callerNumber: '+1-555-1234',
            calleeNumber: '+1-555-5678',
            startTime: new Date().toISOString(),
            duration: 0,
            status: 'active'
        },
        volatileContext: {
            sessionId: 'sess-' + Math.random().toString(36).substr(2, 9),
            mediaServer: 'media-01',
            isRecording: true
        },
        transitions: [
            { from: 'ADMISSION', to: 'RINGING', event: 'INCOMING_CALL', timestamp: new Date().toISOString() }
        ]
    },
    'call-002': {
        id: 'call-002',
        currentState: 'CONNECTED',
        previousState: 'RINGING',
        lastEvent: 'ANSWER',
        persistentContext: {
            callId: 'call-002',
            callerNumber: '+1-555-2222',
            calleeNumber: '+1-555-3333',
            startTime: new Date(Date.now() - 60000).toISOString(),
            duration: 60,
            status: 'active'
        },
        volatileContext: {
            sessionId: 'sess-' + Math.random().toString(36).substr(2, 9),
            mediaServer: 'media-02',
            isRecording: false
        },
        transitions: [
            { from: 'ADMISSION', to: 'RINGING', event: 'INCOMING_CALL', timestamp: new Date(Date.now() - 120000).toISOString() },
            { from: 'RINGING', to: 'CONNECTED', event: 'ANSWER', timestamp: new Date(Date.now() - 60000).toISOString() }
        ]
    },
    'call-003': {
        id: 'call-003',
        currentState: 'ADMISSION',
        previousState: null,
        lastEvent: null,
        persistentContext: {
            callId: 'call-003',
            status: 'idle'
        },
        volatileContext: {},
        transitions: []
    }
};

// Handle WebSocket connections
wss.on('connection', (ws) => {
    console.log('✅ New client connected');

    // Send initial connection confirmation
    ws.send(JSON.stringify({
        type: 'CONNECTION_ESTABLISHED',
        timestamp: new Date().toISOString(),
        availableMachines: Object.keys(machines)
    }));

    // Handle messages from client
    ws.on('message', (data) => {
        try {
            const message = JSON.parse(data.toString());
            console.log('📥 Received:', message);

            // Handle different message types
            switch (message.action) {
                case 'GET_STATE':
                    handleGetState(ws, message);
                    break;
                case 'SEND_EVENT':
                    handleSendEvent(ws, message);
                    break;
                case 'GET_MACHINES':
                    handleGetMachines(ws);
                    break;
                case 'SUBSCRIBE':
                    handleSubscribe(ws, message);
                    break;
                default:
                    console.log('Unknown action:', message.action);
            }
        } catch (error) {
            console.error('Error processing message:', error);
            ws.send(JSON.stringify({
                type: 'ERROR',
                message: error.message
            }));
        }
    });

    ws.on('close', () => {
        console.log('👋 Client disconnected');
    });

    // Start sending periodic updates
    const interval = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
            // Send random updates for active machines
            const machineIds = Object.keys(machines);
            const randomMachine = machines[machineIds[Math.floor(Math.random() * machineIds.length)]];

            // Update duration for connected calls
            if (randomMachine.currentState === 'CONNECTED') {
                randomMachine.persistentContext.duration += 1;
            }

            ws.send(JSON.stringify({
                type: 'STATE_UPDATE',
                machineId: randomMachine.id,
                data: randomMachine,
                timestamp: new Date().toISOString()
            }));
        } else {
            clearInterval(interval);
        }
    }, 5000);
});

function handleGetState(ws, message) {
    const machineId = message.machineId || 'call-001';
    const machine = machines[machineId];

    if (machine) {
        ws.send(JSON.stringify({
            type: 'STATE_RESPONSE',
            machineId: machineId,
            data: machine,
            timestamp: new Date().toISOString()
        }));
    } else {
        ws.send(JSON.stringify({
            type: 'ERROR',
            message: `Machine ${machineId} not found`
        }));
    }
}

function handleSendEvent(ws, message) {
    const { machineId, eventType, payload } = message;
    const machine = machines[machineId];

    if (!machine) {
        ws.send(JSON.stringify({
            type: 'ERROR',
            message: `Machine ${machineId} not found`
        }));
        return;
    }

    // Simulate state transitions
    const transition = simulateTransition(machine, eventType);

    if (transition) {
        machine.transitions.push(transition);
        machine.previousState = machine.currentState;
        machine.currentState = transition.to;
        machine.lastEvent = eventType;

        ws.send(JSON.stringify({
            type: 'EVENT_PROCESSED',
            machineId: machineId,
            transition: transition,
            data: machine,
            timestamp: new Date().toISOString()
        }));

        // Broadcast to all connected clients
        wss.clients.forEach(client => {
            if (client.readyState === WebSocket.OPEN) {
                client.send(JSON.stringify({
                    type: 'STATE_CHANGED',
                    machineId: machineId,
                    transition: transition,
                    data: machine,
                    timestamp: new Date().toISOString()
                }));
            }
        });
    } else {
        ws.send(JSON.stringify({
            type: 'EVENT_REJECTED',
            machineId: machineId,
            reason: `Cannot process ${eventType} in state ${machine.currentState}`,
            timestamp: new Date().toISOString()
        }));
    }
}

function handleGetMachines(ws) {
    const machineList = Object.keys(machines).map(id => ({
        id: id,
        currentState: machines[id].currentState,
        lastEvent: machines[id].lastEvent
    }));

    ws.send(JSON.stringify({
        type: 'MACHINES_LIST',
        machines: machineList,
        timestamp: new Date().toISOString()
    }));
}

function handleSubscribe(ws, message) {
    const { machineId } = message;
    console.log(`Client subscribed to ${machineId}`);

    // Send initial state
    if (machines[machineId]) {
        ws.send(JSON.stringify({
            type: 'SUBSCRIPTION_CONFIRMED',
            machineId: machineId,
            data: machines[machineId],
            timestamp: new Date().toISOString()
        }));
    }
}

function simulateTransition(machine, eventType) {
    const transitions = {
        'ADMISSION': {
            'INCOMING_CALL': 'RINGING'
        },
        'RINGING': {
            'ANSWER': 'CONNECTED',
            'REJECT': 'REJECTED',
            'HANGUP': 'HUNGUP'
        },
        'CONNECTED': {
            'HANGUP': 'HUNGUP'
        }
    };

    const stateTransitions = transitions[machine.currentState];
    if (stateTransitions && stateTransitions[eventType]) {
        return {
            from: machine.currentState,
            to: stateTransitions[eventType],
            event: eventType,
            timestamp: new Date().toISOString()
        };
    }

    return null;
}

console.log('\n📊 Available test machines:');
console.log('  - call-001 (RINGING)');
console.log('  - call-002 (CONNECTED)');
console.log('  - call-003 (ADMISSION)');
console.log('\n💡 React UI can connect to ws://localhost:9999');
console.log('🔄 Sending updates every 5 seconds');
console.log('\nPress Ctrl+C to stop the server\n');