# State Machine Monitor - React UI

A modern, Material-UI based React application for monitoring and controlling state machines in real-time via WebSocket connections.

## Features

- 🎨 **Modern Material-UI Design** - Dark theme with responsive layout
- 🔌 **Real-time WebSocket Connection** - Live state updates and event streaming
- 📊 **State Visualization** - Visual representation of current state and transitions
- 🎮 **Interactive Event Triggers** - Send events to control state machine
- 📈 **Transition Timeline** - Historical view of all state transitions
- ⏱️ **Countdown Timer** - Visual timeout countdown for states with timeouts
- 📱 **Responsive Design** - Works on desktop and mobile devices
- 🧩 **Reusable Components** - Modular architecture for easy extension

## Quick Start

### Installation

```bash
# Navigate to the React UI directory
cd statemachine-ui-react

# Install dependencies
npm install
```

### Development

```bash
# Start development server on port 4001
npm start
```

The application will open at `http://localhost:4001`

### Build for Production

```bash
# Create production build
npm run build
```

## Project Structure

```
statemachine-ui-react/
├── public/
│   └── index.html
├── src/
│   ├── components/
│   │   ├── WebSocketConnection.js    # WebSocket connection manager
│   │   ├── StateVisualization.js     # State machine visualization
│   │   ├── EventTriggerPanel.js      # Event trigger interface
│   │   ├── StateTransitionTimeline.js # Transition history table
│   │   ├── MachineDetailsCard.js     # Machine context details
│   │   └── CountdownTimer.js         # Timeout countdown display
│   ├── hooks/
│   │   └── useWebSocket.js           # WebSocket custom hook
│   ├── App.js                        # Main application component
│   ├── index.js                      # Application entry point
│   └── index.css                     # Global styles
└── package.json
```

## Components

### WebSocketConnection
- Manages WebSocket connection status
- Configurable server URL
- Auto-reconnection support

### StateVisualization
- Displays current state with visual indicators
- Shows recent state transitions
- Animated state changes

### EventTriggerPanel
- Quick action buttons for simple events
- Form inputs for events with payloads
- State-aware event filtering

### StateTransitionTimeline
- Paginated table of all transitions
- Expandable rows for details
- Color-coded states and events

### MachineDetailsCard
- Displays machine ID and context
- Shows call-specific information
- Formatted timestamps

### CountdownTimer
- Circular progress indicator
- Critical state warning
- Animated countdown display

## Configuration

### WebSocket URL
Default: `ws://localhost:9999`

To change the WebSocket server URL:
1. Click the settings icon in the top toolbar
2. Enter the new URL
3. Save and reconnect

### Port Configuration
Development server runs on port 4001 by default. To change:

```bash
# Linux/Mac
PORT=3000 npm start

# Windows
set PORT=3000 && npm start
```

## WebSocket Protocol

The UI expects the following WebSocket message types:

### Incoming Messages
- `STATE` - Current state update
- `STATE_CHANGE` - State transition notification
- `EVENT_METADATA` - Available events and schemas
- `COUNTDOWN` - Timeout countdown updates
- `PERIODIC_UPDATE` - Periodic state sync

### Outgoing Messages
- `EVENT` - Trigger state machine events
- `GET_EVENT_METADATA` - Request event metadata
- `GET_STATE` - Request current state

## Customization

### Theme
Edit `src/index.js` to customize the Material-UI theme:

```javascript
const darkTheme = createTheme({
  palette: {
    mode: 'dark',
    primary: { main: '#90caf9' },
    secondary: { main: '#f48fb1' },
    // ... customize colors
  },
});
```

### Adding New States
Edit the state arrays in components:
- `StateVisualization.js` - Update `states` array
- `EventTriggerPanel.js` - Update `getValidEventsForState()`

### Adding New Events
Update `defaultEvents` in `EventTriggerPanel.js`:

```javascript
const defaultEvents = [
  { 
    type: 'NEW_EVENT', 
    label: 'New Event', 
    requiresPayload: true, 
    payloadFields: ['field1', 'field2'] 
  },
  // ...
];
```

## Future Enhancements

- 📊 State machine diagram visualization
- 📈 Performance metrics and statistics
- 🔍 Advanced filtering and search
- 💾 Session recording and playback
- 🎯 Multiple machine monitoring
- 📱 Mobile app version
- 🧪 Testing suite
- 📚 API documentation

## Troubleshooting

### WebSocket Connection Issues
- Verify the WebSocket server is running
- Check the URL format: `ws://` or `wss://`
- Ensure no firewall blocking the port
- Check browser console for errors

### Build Issues
```bash
# Clear cache and reinstall
rm -rf node_modules package-lock.json
npm install
```

### Development Server Issues
```bash
# Kill process on port 4001
lsof -ti:4001 | xargs kill -9
# Or use a different port
PORT=3000 npm start
```

## License

MIT

## Contributing

Pull requests are welcome. For major changes, please open an issue first.