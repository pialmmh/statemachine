# State Machine Debugger with UI

A real-time visual debugger for monitoring and interacting with state machines through a WebSocket-based interface and React UI.

## Overview

The State Machine Debugger provides:
- Real-time state machine monitoring via WebSocket
- Interactive React UI for visualizing state transitions
- Event injection capabilities for testing
- Tree view visualization of state histories
- Support for multiple concurrent state machines

## Architecture

```
┌─────────────────────────┐     WebSocket      ┌──────────────────────┐
│   React UI (Port 4001)  │◄───────9999────────►│  Backend Debugger    │
│  - Tree View Display    │                     │  - State Machines    │
│  - Event Sender         │                     │  - WebSocket Server  │
│  - Transition History   │                     │  - MySQL Persistence │
└─────────────────────────┘                     └──────────────────────┘
```

## Prerequisites

- Java 17+ 
- Node.js 14+ and npm
- MySQL database running on localhost:3306
- Database `statedb` with appropriate tables

## Quick Start

### 1. Start the Backend (State Machine Debugger)

```bash
# From the project root directory
mvn compile
mvn exec:java -Dexec.mainClass="com.telcobright.statemachine.statemachinedebugger.CallMachineRunnerEnhanced"
```

The backend will:
- Start WebSocket server on port 9999
- Initialize 3 sample call machines (call-001, call-002, call-003)
- Connect to MySQL for persistence
- Begin logging to `websocket-logs/` directory

You should see:
```
============================================================
   Enhanced CallMachine with Proper Context Separation
============================================================
🔌 WebSocket API: ws://localhost:9999
============================================================

Features:
✅ Persistent context (CallPersistentContext) - saved to DB
✅ Volatile context (CallVolatileContext) - runtime only
✅ Automatic volatile context recreation on rehydration
✅ SIP session tracking and media server assignment
✅ Call recording and billing calculation
```

### 2. Start the React UI

```bash
# From the statemachine-ui-react directory
cd statemachine-ui-react
npm install  # Only needed first time
npm start
```

The UI will:
- Start development server on port 4001
- Automatically open http://localhost:4001 in your browser
- Connect to the WebSocket server at ws://localhost:9999

## Using the Debugger

### Monitoring State Machines

1. **Open the UI**: Navigate to http://localhost:4001
2. **Click "Live Viewer"**: Connects to the WebSocket server
3. **Select a Machine**: Choose from the dropdown (e.g., call-001, call-002, call-003)
4. **View State**: Current state and transition history appear in the tree view

### Sending Events

1. **Select a Machine**: Choose target machine from dropdown
2. **Select Event Type**: Available events:
   - `INCOMING_CALL`: Transitions from ADMISSION → RINGING
   - `ANSWER`: Transitions from RINGING → CONNECTED
   - `HANGUP`: Transitions to HUNGUP (final state)
   - `SESSION_PROGRESS`: Stays in current state with side effects
3. **Configure Payload** (optional): 
   ```json
   {
     "phoneNumber": "+1-555-9999"
   }
   ```
4. **Click "Send Event"**: Event is sent via WebSocket

### State Transitions

The sample CallMachine implements this state flow:

```
ADMISSION --[INCOMING_CALL]--> RINGING --[ANSWER]--> CONNECTED --[HANGUP]--> HUNGUP
     |                            |                        |
     +---[HANGUP]---------------->+------[HANGUP]----------+
```

## Features

### Tree View Display
- Hierarchical visualization of state transitions
- Color-coded states and events
- Expandable/collapsible nodes
- Real-time updates via WebSocket

### Event Details Panel
- Shows transition metadata
- Displays event payloads
- Timing information
- Context data (persistent and volatile)

### WebSocket Communication
- Bidirectional real-time messaging
- Automatic reconnection
- Event broadcasting to all connected clients
- Structured JSON message protocol

## Troubleshooting

### Port Already in Use

If you see "Address already in use" error:

```bash
# Find process using port 9999
lsof -i :9999
# Or
netstat -tlnp | grep 9999

# Kill the process
kill <PID>
```

### WebSocket Connection Failed

1. Ensure backend is running first
2. Check firewall settings for port 9999
3. Verify no proxy interference
4. Check browser console for errors

### State Transitions Not Working

1. Verify machine is in correct state for the event
2. Check backend console for error messages
3. Ensure event payload is valid JSON
4. Look at websocket-logs/ for detailed logging

### UI Not Updating

1. Check browser console for errors
2. Verify WebSocket connection status (green indicator)
3. Try refreshing the page
4. Ensure selected machine exists

## Development

### Project Structure

```
statemachine/
├── src/main/java/com/telcobright/statemachine/
│   └── statemachinedebugger/
│       ├── CallMachineRunnerEnhanced.java    # Main backend entry point
│       ├── StateMachineWebSocketServer.java  # WebSocket server implementation
│       └── WebSocketLogger.java              # Logging utilities
└── statemachine-ui-react/
    ├── src/
    │   ├── MonitoringApp.js                  # Main React component
    │   ├── components/
    │   │   ├── StateTreeView.js              # Tree visualization
    │   │   ├── TransitionDetailPanel.js      # Event details
    │   │   └── LiveHistoryDisplay.js         # History view
    │   └── store/
    │       └── treeViewStore.js              # State management
    └── package.json
```

### WebSocket Message Protocol

#### From UI to Backend:
```json
{
  "type": "EVENT",
  "machineId": "call-003",
  "eventType": "INCOMING_CALL",
  "payload": {
    "phoneNumber": "+1-555-TEST"
  }
}
```

#### From Backend to UI:
```json
{
  "type": "STATE_CHANGE",
  "machineId": "call-003",
  "fromState": "ADMISSION",
  "toState": "RINGING",
  "event": "INCOMING_CALL",
  "timestamp": "2025-08-30T15:39:23.023"
}
```

### Adding New State Machines

1. Extend the generic state machine framework
2. Define states and transitions
3. Register with the debugger
4. Events will automatically appear in UI

## Configuration

### Backend Configuration

Edit `CallMachineRunnerEnhanced.java`:

```java
// WebSocket port
private static final int WS_PORT = 9999;

// Number of sample machines
// Modify the initializeStateMachines() method
```

### UI Configuration

Edit `statemachine-ui-react/src/config.js`:

```javascript
const config = {
  websocketUrl: 'ws://localhost:9999',
  // Other configuration options
};
```

## Logs and Debugging

### Backend Logs
- Console output: Real-time state changes and events
- `websocket-logs/`: Detailed WebSocket message logs
- MySQL tables: Persistent state and history

### Frontend Logs
- Browser console: Debug messages and errors
- Network tab: WebSocket frame inspection
- React Developer Tools: Component state inspection

## Testing

### Manual Testing

1. Send events through UI
2. Verify state transitions
3. Check transition history
4. Test edge cases (invalid events, timeouts)

### Test Scripts

```bash
# Test with Node.js script
node test-incoming-call.js

# Test with HTML page
open test-websocket.html
```

## Production Considerations

- Use proper authentication for WebSocket connections
- Implement rate limiting for event submission
- Add SSL/TLS for secure WebSocket (wss://)
- Configure proper CORS headers
- Set up monitoring and alerting
- Implement proper error recovery
- Add database connection pooling
- Consider using message queuing for scalability

## Support

For issues or questions:
- Check the console logs (both backend and frontend)
- Review websocket-logs/ directory
- Verify database connectivity
- Ensure all prerequisites are installed

## License

Part of the Telcobright State Machine Framework