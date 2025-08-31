# System Status - State Machine with UI

## 🟢 Currently Running

### Backend (CallMachineRunnerEnhanced)
- **Status**: ✅ RUNNING
- **WebSocket Server**: `ws://localhost:9999`
- **State Machines**: 3 machines created
  - call-001 (ADMISSION state)
  - call-002 (ADMISSION state)  
  - call-003 (ADMISSION state)
- **Process**: Java process running via Maven exec

### Frontend (React UI)
- **Status**: ✅ RUNNING
- **URL**: `http://localhost:4001`
- **Features Available**:
  - Live Viewer (real-time state monitoring)
  - Tree View (hierarchical state visualization)
  - Event Viewer (transition history)
  - Event Sender (send events to machines)

## 📋 How to Use

### 1. Access the UI
Open your browser and navigate to: **http://localhost:4001**

### 2. Connect to WebSocket
- Click on **"Live Viewer"** button
- The UI will automatically connect to `ws://localhost:9999`
- Connection status will show as "Connected" in green

### 3. Monitor State Machines
- **Machine Selector**: Dropdown shows all 3 machines (call-001, call-002, call-003)
- **Current State**: Shows the current state of selected machine
- **State Diagram**: Visual representation of state transitions

### 4. Send Events
In the UI, you can send events to machines:

1. Select a machine from the dropdown
2. Choose an event type:
   - **INCOMING_CALL**: Moves from ADMISSION → RINGING
   - **ANSWER**: Moves from RINGING → CONNECTED
   - **HANGUP**: Moves to HUNGUP (final state)
   - **SESSION_PROGRESS**: Stay event in RINGING state
3. Click "Send Event"
4. Watch the state change in real-time!

### 5. View History
- Switch to **"Event Viewer"** tab to see complete transition history
- Shows timestamp, event type, and state changes
- Auto-refreshes every second

## 🎯 Test Scenarios

### Scenario 1: Complete Call Flow
```
1. Select "call-001"
2. Send INCOMING_CALL → State changes to RINGING
3. Send ANSWER → State changes to CONNECTED
4. Send HANGUP → State changes to HUNGUP
```

### Scenario 2: Rejected Call
```
1. Select "call-002"
2. Send INCOMING_CALL → State changes to RINGING
3. Send HANGUP → State changes directly to HUNGUP
```

### Scenario 3: Session Progress
```
1. Select "call-003"
2. Send INCOMING_CALL → State changes to RINGING
3. Send SESSION_PROGRESS → Stays in RINGING (with progress update)
4. Send ANSWER → State changes to CONNECTED
```

## 🔧 Troubleshooting

### If UI doesn't connect:
1. Check WebSocket is running: Look for "WebSocket server: ws://localhost:9999" in backend console
2. Refresh the browser page
3. Check browser console for errors (F12)

### If state doesn't update:
1. Ensure you've selected a machine from dropdown
2. Check that event is valid for current state
3. Look at backend console for event processing logs

## 📊 WebSocket Messages

The system broadcasts these real-time updates:
- `REGISTRY_CREATE`: New machine created
- `STATE_CHANGE`: State transition occurred
- `EVENT_METADATA_UPDATE`: Available events updated
- `COMPLETE_STATUS`: Full system status
- `TREEVIEW_STORE_UPDATE`: Tree structure changed

## 🛑 How to Stop

### Stop Backend:
Press `Ctrl+C` in the terminal running CallMachineRunnerEnhanced

### Stop Frontend:
Press `Ctrl+C` in the terminal running React UI (or close the browser tab)

## 📝 Notes

- The backend creates 3 sample machines on startup
- Each machine has independent state
- WebSocket broadcasts all changes to connected clients
- UI auto-reconnects if connection is lost
- History is stored in MySQL tables (one per machine)

---

**System is ready for testing!** Open http://localhost:4001 and start interacting with the state machines.