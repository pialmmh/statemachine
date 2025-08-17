import React, { useState, useEffect, useRef } from 'react';
import './MonitoringApp.css';
import LiveHistoryDisplay from './components/LiveHistoryDisplay';

function MonitoringApp() {
  const [currentMode, setCurrentMode] = useState('snapshot');
  const [runs, setRuns] = useState([]);
  const [selectedRun, setSelectedRun] = useState(null);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(false);
  const [isConnected, setIsConnected] = useState(false);
  const [liveState, setLiveState] = useState('IDLE');
  const [liveHistory, setLiveHistory] = useState([]);
  const [selectedEvent, setSelectedEvent] = useState('INCOMING_CALL');
  const [eventPayload, setEventPayload] = useState('{\n  "phoneNumber": "+1-555-9999"\n}');
  const [countdownState, setCountdownState] = useState(null);
  const [countdownRemaining, setCountdownRemaining] = useState(0);
  const [liveMachines, setLiveMachines] = useState([]);
  const [selectedMachine, setSelectedMachine] = useState(null);
  
  const wsRef = useRef(null);
  const wsUrl = 'ws://localhost:9999';
  const hasReceivedInitialState = useRef(false);
  const countdownIntervalRef = useRef(null);
  const selectedMachineRef = useRef(null);

  useEffect(() => {
    loadRuns();
    if (currentMode === 'live') {
      // Clear history and reset flag when entering live mode
      setLiveHistory([]);
      hasReceivedInitialState.current = false;
      // Connect after a small delay to ensure state is cleared
      setTimeout(() => connectWebSocket(), 100);
    }
    return () => {
      if (wsRef.current) {
        wsRef.current.close();
        wsRef.current = null;
      }
      if (countdownIntervalRef.current) {
        clearInterval(countdownIntervalRef.current);
        countdownIntervalRef.current = null;
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentMode]);

  const loadRuns = async () => {
    setLoading(true);
    try {
      // Try to fetch from server
      const response = await fetch('http://localhost:8091/api/runs');
      if (response.ok) {
        const data = await response.json();
        setRuns(data);
      } else {
        // Use sample data
        setRuns(getSampleRuns());
      }
    } catch (error) {
      console.log('Using sample data:', error);
      setRuns(getSampleRuns());
    } finally {
      setLoading(false);
    }
  };

  const getSampleRuns = () => {
    const timestamp = Date.now();
    return [
      {
        runId: `call-demo-${timestamp}`,
        machineId: 'call-001',
        machineType: 'CallMachine',
        latestTimestamp: new Date().toISOString(),
        transitionCount: 11,
        triggeringClassName: 'MonitoredCallMachineDemo',
        triggeringClassFullPath: 'com.telcobright.statemachine.test.MonitoredCallMachineDemo'
      },
      {
        runId: `sms-demo-${timestamp - 10000}`,
        machineId: 'sms-001',
        machineType: 'SmsMachine',
        latestTimestamp: new Date(Date.now() - 900000).toISOString(),
        transitionCount: 3,
        triggeringClassName: 'SmsProcessingDemo',
        triggeringClassFullPath: 'com.telcobright.statemachine.test.SmsProcessingDemo'
      }
    ];
  };

  const loadHistory = async (runId) => {
    setLoading(true);
    try {
      const response = await fetch(`http://localhost:8091/api/history?runId=${runId}`);
      if (response.ok) {
        const data = await response.json();
        setHistory(data);
      } else {
        // Use sample history
        setHistory(getSampleHistory(runId));
      }
    } catch (error) {
      console.log('Using sample history:', error);
      setHistory(getSampleHistory(runId));
    } finally {
      setLoading(false);
    }
  };

  const getSampleHistory = (runId) => {
    if (runId.includes('call-demo')) {
      return [
        {
          stepNumber: 1,
          fromState: 'IDLE',
          toState: 'RINGING',
          event: 'INCOMING_CALL',
          duration: 120,
          eventData: { caller: '+1-555-DEMO', callType: 'VOICE' },
          contextBefore: { callId: 'call-001', currentState: 'IDLE' },
          contextAfter: { callId: 'call-001', currentState: 'RINGING', ringStartTime: new Date().toISOString() }
        },
        {
          stepNumber: 2,
          fromState: 'RINGING',
          toState: 'RINGING',
          event: 'SESSION_PROGRESS',
          duration: 50,
          eventData: { status: 'TRYING', code: 100 },
          contextBefore: { currentState: 'RINGING', sessionEvents: [] },
          contextAfter: { currentState: 'RINGING', sessionEvents: ['Trying'] }
        },
        {
          stepNumber: 3,
          fromState: 'RINGING',
          toState: 'CONNECTED',
          event: 'ANSWER',
          duration: 200,
          eventData: { answeredBy: 'user' },
          contextBefore: { currentState: 'RINGING' },
          contextAfter: { currentState: 'CONNECTED', connectedTime: new Date().toISOString() }
        },
        {
          stepNumber: 4,
          fromState: 'CONNECTED',
          toState: 'IDLE',
          event: 'HANGUP',
          duration: 100,
          eventData: { reason: 'normal_clearing', duration: 45 },
          contextBefore: { currentState: 'CONNECTED' },
          contextAfter: { currentState: 'IDLE', complete: true }
        }
      ];
    }
    return [];
  };

  // Helper function to safely send WebSocket messages
  const sendWebSocketMessage = (message) => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      try {
        const messageStr = typeof message === 'string' ? message : JSON.stringify(message);
        wsRef.current.send(messageStr);
        return true;
      } catch (error) {
        console.error('Error sending WebSocket message:', error);
        return false;
      }
    } else {
      console.warn('WebSocket not ready, message not sent:', message);
      // Queue the message to send when connection is ready
      if (wsRef.current && wsRef.current.readyState === WebSocket.CONNECTING) {
        setTimeout(() => sendWebSocketMessage(message), 100);
      }
      return false;
    }
  };

  const connectWebSocket = () => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return;

    try {
      wsRef.current = new WebSocket(wsUrl);

      wsRef.current.onopen = () => {
        console.log('WebSocket connected for live mode');
        setIsConnected(true);
        
        // Use safe send function with a small delay to ensure connection is fully established
        setTimeout(() => {
          // Request list of machines
          sendWebSocketMessage({
            action: 'GET_MACHINES'
          });
          
          // Request initial state
          sendWebSocketMessage({
            action: 'GET_STATE'
          });
        }, 50);
      };

      wsRef.current.onmessage = (event) => {
        console.log('Raw WebSocket message:', event.data);
        handleWebSocketMessage(event.data);
      };

      wsRef.current.onclose = () => {
        console.log('WebSocket disconnected');
        setIsConnected(false);
        wsRef.current = null;
        // Reconnect if still in live mode
        if (currentMode === 'live') {
          setTimeout(connectWebSocket, 3000);
        }
      };

      wsRef.current.onerror = (error) => {
        console.error('WebSocket error:', error);
        setIsConnected(false);
      };
    } catch (error) {
      console.error('Failed to connect:', error);
    }
  };

  const handleCountdown = (state, remaining) => {
    // Clear existing interval
    if (countdownIntervalRef.current) {
      clearInterval(countdownIntervalRef.current);
      countdownIntervalRef.current = null;
    }

    if (state && remaining > 0) {
      setCountdownState(state);
      setCountdownRemaining(remaining);
      
      // Start local countdown
      countdownIntervalRef.current = setInterval(() => {
        setCountdownRemaining(prev => {
          if (prev <= 1) {
            clearInterval(countdownIntervalRef.current);
            countdownIntervalRef.current = null;
            setCountdownState(null);
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    } else {
      setCountdownState(null);
      setCountdownRemaining(0);
    }
  };

  const handleWebSocketMessage = (message) => {
    try {
      const data = JSON.parse(message);
      console.log('Parsed WebSocket message:', data);
      console.log('Message type:', data.type, 'Selected machine:', selectedMachine);
      
      // Handle machine list
      if (data.type === 'MACHINES_LIST') {
        const machines = data.machines || [];
        setLiveMachines(machines);
        // Auto-select first machine if none selected
        if (machines.length > 0 && !selectedMachine) {
          setSelectedMachine(machines[0].id);
          selectedMachineRef.current = machines[0].id;  // Update ref
        }
      } else if (data.type === 'MACHINE_REGISTERED') {
        // Add new machine to list
        setLiveMachines(prev => [...prev, { id: data.machineId, type: data.machineType || 'StateMachine' }]);
      } else if (data.type === 'MACHINE_UNREGISTERED') {
        // Remove machine from list
        setLiveMachines(prev => prev.filter(m => m.id !== data.machineId));
        if (selectedMachine === data.machineId) {
          setSelectedMachine(null);
        }
      } else if (data.type === 'CURRENT_STATE') {
        console.log('CURRENT_STATE message received. Has context?', !!data.context, 'Has received initial?', hasReceivedInitialState.current);
        setLiveState(data.currentState || 'IDLE');
        
        // If we have context and haven't received initial state yet
        if (data.context && !hasReceivedInitialState.current) {
          console.log('Adding initial state with context:', data.context);
          hasReceivedInitialState.current = true;
          
          // Add initial state to history
          const initialTransition = {
            stepNumber: 1,
            fromState: 'Initial',
            toState: data.currentState || 'IDLE',
            event: 'Initial State',
            timestamp: data.timestamp || new Date().toISOString(),
            duration: 0,
            eventData: {},
            contextBefore: {
              registryStatus: { status: 'ACTIVE', hydrated: false, online: true },
              persistentContext: data.context || {},
              volatileContext: {}
            },
            contextAfter: {
              registryStatus: { status: 'ACTIVE', hydrated: false, online: true },
              persistentContext: data.context || {},
              volatileContext: {}
            }
          };
          
          setLiveHistory([initialTransition]);
        }
      } else if (data.type === 'MACHINE_STATE') {
        // Handle single machine state response
        console.log('MACHINE_STATE received:', data);
        
        if (data.machineId === selectedMachine) {
          setLiveState(data.currentState || 'IDLE');
          
          // Always set up initial history entry when we get machine state
          const initialTransition = {
            stepNumber: 1,
            fromState: 'Initial',
            toState: data.currentState || 'IDLE',
            event: 'Machine State',
            timestamp: data.timestamp || new Date().toISOString(),
            duration: 0,
            eventData: {},
            contextBefore: {
              registryStatus: { status: 'ACTIVE', hydrated: false, online: true },
              persistentContext: {},
              volatileContext: {}
            },
            contextAfter: {
              registryStatus: { status: 'ACTIVE', hydrated: false, online: true },
              persistentContext: data.context || {},
              volatileContext: {}
            }
          };
          
          setLiveHistory([initialTransition]);
          hasReceivedInitialState.current = true;
        }
      } else if (data.type === 'COMPLETE_STATUS') {
        // Handle complete status update from server
        console.log('COMPLETE_STATUS received:', data);
        
        // Process machines from complete status
        if (data.machines && Array.isArray(data.machines)) {
          // Find the selected machine's data
          const selectedMachineData = data.machines.find(m => m.machineId === selectedMachine);
          
          if (selectedMachineData) {
            // Update live state for selected machine
            setLiveState(selectedMachineData.currentState || 'IDLE');
            
            // If we haven't received initial state yet, set it up
            if (!hasReceivedInitialState.current && selectedMachineData.context) {
              hasReceivedInitialState.current = true;
              
              const initialTransition = {
                stepNumber: 1,
                fromState: 'Initial',
                toState: selectedMachineData.currentState || 'IDLE',
                event: 'Initial State',
                timestamp: data.timestamp || new Date().toISOString(),
                duration: 0,
                eventData: {},
                contextBefore: {
                  registryStatus: { status: 'ACTIVE', hydrated: false, online: true },
                  persistentContext: {},
                  volatileContext: {}
                },
                contextAfter: {
                  registryStatus: { status: 'ACTIVE', hydrated: false, online: true },
                  persistentContext: selectedMachineData.context || {},
                  volatileContext: {}
                }
              };
              
              setLiveHistory([initialTransition]);
            }
          }
        }
      } else if (data.type === 'STATE_CHANGE') {
        console.log('STATE_CHANGE received for machine:', data.machineId);
        console.log('Currently selected machine (ref):', selectedMachineRef.current);
        console.log('Currently selected machine (state):', selectedMachine);
        
        // Only process state changes for the selected machine (use ref to avoid closure issues)
        if (data.machineId !== selectedMachineRef.current) {
          console.log('Ignoring state change for non-selected machine');
          return;
        }
        
        console.log('Processing STATE_CHANGE for selected machine');
        
        // Update live state
        const newState = data.newState || data.stateAfter || data.state;
        console.log('New state:', newState);
        setLiveState(newState);
        
        // Handle countdown if present in message
        if (data.countdown) {
          handleCountdown(data.countdown.state, data.countdown.remaining);
        } else {
          // If no countdown in message, check if this is a state with timeout
          // RINGING state has a 30-second timeout
          if (newState === 'RINGING') {
            handleCountdown('RINGING', 30);
          } else {
            handleCountdown(null, 0);
          }
        }
        
        // Add to live history with full context
        setLiveHistory(prev => {
          const updated = [...(Array.isArray(prev) ? prev : [])];
          
          // If this is the first transition and we're going from IDLE, ensure we have the initial state
          if (updated.length === 0 && data.stateBefore === 'IDLE') {
            // Add the initial IDLE state first
            updated.push({
              stepNumber: 1,
              fromState: 'Initial',
              toState: 'IDLE',
              event: 'Initial State',
              timestamp: new Date().toISOString(),
              duration: 0,
              eventData: {},
              contextBefore: {
                registryStatus: { status: 'ACTIVE', hydrated: false, online: true },
                persistentContext: {},
                volatileContext: {}
              },
              contextAfter: {
                registryStatus: { status: 'ACTIVE', hydrated: false, online: true },
                persistentContext: {},
                volatileContext: {}
              }
            });
          }
          
          // Get the previous context (from last transition's contextAfter)
          const previousContext = updated.length > 0 
            ? updated[updated.length - 1].contextAfter.persistentContext 
            : {};
          
          const transition = {
            stepNumber: updated.length + 1,
            fromState: data.stateBefore || data.oldState || 'UNKNOWN',
            toState: data.stateAfter || data.newState || 'UNKNOWN',
            event: data.type === 'STATE_CHANGE' ? 'State Transition' : (data.eventName || 'Unknown'),
            timestamp: data.timestamp || new Date().toISOString(),
            duration: data.duration || 0,
            eventData: data.payload || {},
            entryActionStatus: data.entryActionStatus || 'none', // Capture entry action status
            contextBefore: {
              registryStatus: { status: 'ACTIVE', hydrated: false, online: true },
              persistentContext: previousContext,
              volatileContext: {}
            },
            contextAfter: {
              registryStatus: { status: 'ACTIVE', hydrated: false, online: true },
              persistentContext: data.context || previousContext,
              volatileContext: {}
            }
          };
          updated.push(transition);
          return updated;
        });
      } else if (data.type === 'CONNECTION_TEST') {
        // Ignore connection test messages
        console.log('Connection test message received');
      } else if (data.type === 'STATE') {
        // Simple state update without context
        setLiveState(data.state);
      } else if (data.type === 'PERIODIC_UPDATE') {
        // Handle periodic updates
        setLiveState(data.state);
        // Handle countdown if present
        if (data.countdown) {
          handleCountdown(data.countdown.state, data.countdown.remaining);
        }
      } else if (data.type === 'COUNTDOWN') {
        // Handle countdown updates
        handleCountdown(data.state, data.remaining);
      } else if (data.state) {
        // Generic state update
        setLiveState(data.state);
      }
    } catch (error) {
      console.error('Error parsing message:', error, message);
    }
  };


  const selectRun = (run) => {
    setSelectedRun(run);
    loadHistory(run.runId);
  };

  const refreshData = () => {
    loadRuns();
    if (selectedRun) {
      loadHistory(selectedRun.runId);
    }
  };

  const switchToLiveMode = () => {
    // Handle live mode specific setup
    if (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) {
      connectWebSocket();
    }
  };

  const formatTimestamp = (timestamp) => {
    try {
      return new Date(timestamp).toLocaleString();
    } catch {
      return timestamp;
    }
  };

  const sendEvent = () => {
    console.log('sendEvent called');
    console.log('isConnected:', isConnected, 'wsRef.current:', wsRef.current, 'selectedMachine:', selectedMachine);
    
    if (!selectedMachine) {
      alert('Please select a machine first');
      return;
    }

    if (!isConnected || !wsRef.current) {
      alert('WebSocket is not connected. Please wait for connection or refresh the page.');
      return;
    }

    try {
      const payload = eventPayload ? JSON.parse(eventPayload) : {};
      const message = {
        type: 'EVENT',
        machineId: selectedMachine,
        eventType: selectedEvent,
        payload: payload
      };
      
      console.log('Sending WebSocket event:', message);
      
      if (sendWebSocketMessage(message)) {
        console.log('Event sent successfully');
      } else {
        alert('Failed to send event. WebSocket connection might be unstable.');
      }
    } catch (error) {
      console.error('Error preparing event:', error);
      alert('Invalid JSON payload: ' + error.message);
    }
  };

  const handleEventSelection = (event) => {
    const eventType = event.target.value;
    setSelectedEvent(eventType);
    
    // Set default payloads for each event
    switch (eventType) {
      case 'INCOMING_CALL':
        setEventPayload('{\n  "phoneNumber": "+1-555-9999"\n}');
        break;
      case 'SESSION_PROGRESS':
        setEventPayload('{\n  "sessionData": "v=0",\n  "ringNumber": 1\n}');
        break;
      case 'ANSWER':
      case 'HANGUP':
      case 'REJECT':
      case 'BUSY':
      case 'TIMEOUT':
        setEventPayload('{}');
        break;
      default:
        setEventPayload('{}');
    }
  };

  return (
    <div className="monitoring-app">
      <div className="header">
        <div className="header-left">
          <h1>📊 TelcoBright State Machine Monitoring</h1>
          <p>Real-time monitoring of CallMachine and SmsMachine state transitions</p>
        </div>
        <div className="header-right">
          <div className="mode-buttons">
            <button 
              className={`mode-btn ${currentMode === 'snapshot' ? 'active' : ''}`}
              onClick={() => setCurrentMode('snapshot')}
            >
              📸 Snapshot Viewer
            </button>
            <button 
              className={`mode-btn ${currentMode === 'live' ? 'active' : ''}`}
              onClick={() => {
                setCurrentMode('live');
                switchToLiveMode();
              }}
            >
              🔴 Live Viewer
            </button>
          </div>
        </div>
      </div>

      <div className="container">
        <div className="left-panel">
          <div className="panel-header">
            <span>📋 {currentMode === 'live' ? `Live Machines: ${liveMachines.length}` : 'Recent Runs'}</span>
            <button className="refresh-btn" onClick={refreshData}>Refresh</button>
          </div>
          <div className="run-list">
            {loading ? (
              <div className="loading">Loading state machine runs...</div>
            ) : currentMode === 'snapshot' ? (
              runs.map(run => (
                <div 
                  key={run.runId}
                  className={`run-item ${selectedRun?.runId === run.runId ? 'selected' : ''}`}
                  onClick={() => selectRun(run)}
                >
                  <div className="triggering-class">{run.triggeringClassName}</div>
                  <div style={{ fontSize: '14px', fontWeight: '600', marginBottom: '4px' }}>
                    {run.machineType} - {run.machineId}
                  </div>
                  <div style={{ fontSize: '12px', color: '#6c757d' }}>
                    {formatTimestamp(run.latestTimestamp)}
                  </div>
                  <div style={{ fontSize: '11px', color: '#868e96', marginTop: '4px' }}>
                    {run.transitionCount} transitions
                  </div>
                </div>
              ))
            ) : (
              <div className="live-controls">
                <div className="run-item" style={{ background: isConnected ? '#d4edda' : '#f8d7da' }}>
                  <div style={{ fontSize: '14px', fontWeight: '600', marginBottom: '8px' }}>
                    {isConnected ? '🟢 Connected' : '🔴 Disconnected'}
                  </div>
                  <div style={{ fontSize: '12px' }}>
                    Current State: <strong>{liveState}</strong>
                  </div>
                  {isConnected && (
                    <div style={{ fontSize: '11px', marginTop: '8px', color: '#495057' }}>
                      WebSocket: {wsUrl}
                    </div>
                  )}
                </div>
                
                {isConnected && (
                  <div className="event-trigger-panel">
                    <div className="panel-subtitle">🖥️ Select Machine</div>
                    <select 
                      className="event-select"
                      value={selectedMachine || ''}
                      onChange={(e) => {
                        const machineId = e.target.value;
                        console.log('Machine selected:', machineId);
                        setSelectedMachine(machineId);
                        selectedMachineRef.current = machineId;  // Update ref to avoid closure issues
                        
                        // When a machine is selected, reset history and request its state
                        if (machineId) {
                          console.log('Requesting state for machine:', machineId);
                          hasReceivedInitialState.current = false;
                          // Don't clear history completely - keep initial state display
                          setLiveHistory([]);
                          
                          // Request the machine's current state using safe send
                          const request = {
                            action: 'GET_MACHINE_STATE',
                            machineId: machineId
                          };
                          console.log('Sending WebSocket request:', request);
                          sendWebSocketMessage(request);
                        } else {
                          console.log('Cannot request state - WebSocket not ready or no machine selected');
                          console.log('machineId:', machineId, 'wsRef.current:', wsRef.current, 'readyState:', wsRef.current?.readyState);
                        }
                      }}
                      style={{ marginBottom: '12px' }}
                    >
                      <option value="">-- Select a Machine --</option>
                      {liveMachines.map(machine => (
                        <option key={machine.id} value={machine.id}>
                          {machine.type} - {machine.id}
                        </option>
                      ))}
                    </select>
                    
                    <div className="panel-subtitle">📮 Send Event</div>
                    <select 
                      className="event-select"
                      value={selectedEvent}
                      onChange={handleEventSelection}
                    >
                      <option value="INCOMING_CALL">INCOMING_CALL</option>
                      <option value="ANSWER">ANSWER</option>
                      <option value="HANGUP">HANGUP</option>
                      <option value="SESSION_PROGRESS">SESSION_PROGRESS</option>
                      <option value="REJECT">REJECT</option>
                      <option value="BUSY">BUSY</option>
                      <option value="TIMEOUT">TIMEOUT</option>
                    </select>
                    
                    <div className="payload-section">
                      <label>Event Payload (JSON):</label>
                      <textarea 
                        className="payload-input"
                        value={eventPayload}
                        onChange={(e) => setEventPayload(e.target.value)}
                        placeholder='{"phoneNumber": "+1-555-9999"}'
                        rows="4"
                      />
                    </div>
                    
                    <button 
                      className="send-event-btn"
                      onClick={() => {
                        console.log('Button clicked!');
                        console.log('Current state - isConnected:', isConnected, 'selectedMachine:', selectedMachine);
                        sendEvent();
                      }}
                      disabled={!isConnected || !selectedMachine}
                    >
                      Send Event → {!isConnected ? '(Not Connected)' : !selectedMachine ? '(No Machine Selected)' : ''}
                    </button>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>

        <div className="right-panel">
          <div className="panel-header">
            📈 {currentMode === 'live' ? 'Live State Transitions' : 'State Machine History'}
            {currentMode === 'live' && liveHistory.length > 0 && (
              <button 
                className="refresh-btn" 
                onClick={() => setLiveHistory([])}
                style={{ marginLeft: 'auto' }}
              >
                Clear History
              </button>
            )}
          </div>
          <div className="history-content">
            {currentMode === 'snapshot' ? (
              selectedRun && history.length > 0 ? (
                history.map(transition => (
                  <div key={transition.stepNumber} className="transition-item">
                    <div className="transition-header">
                      Step {transition.stepNumber}: {transition.event}
                      <span style={{ marginLeft: 'auto', fontSize: '12px' }}>
                        {transition.duration}ms
                      </span>
                    </div>
                    <div className="transition-body">
                      <div className="state-flow">
                        <div className="state">{transition.fromState}</div>
                        <div className="arrow">→</div>
                        <div className="state">{transition.toState}</div>
                      </div>
                      
                      {transition.eventData && (
                        <div className="event-info">
                          <strong>Event Data:</strong>
                          <pre>{JSON.stringify(transition.eventData, null, 2)}</pre>
                        </div>
                      )}

                      <div className="context-section">
                        <div className="context-title">Context Before:</div>
                        <div className="context-content">
                          {JSON.stringify(transition.contextBefore, null, 2)}
                        </div>
                      </div>

                      <div className="context-section">
                        <div className="context-title">Context After:</div>
                        <div className="context-content">
                          {JSON.stringify(transition.contextAfter, null, 2)}
                        </div>
                      </div>
                    </div>
                  </div>
                ))
              ) : (
                <div className="empty-state">
                  <h3>🎯 Welcome to State Machine Monitoring!</h3>
                  <p>Select a run from the left to see detailed state transitions including:</p>
                  <ul style={{ textAlign: 'left', display: 'inline-block', marginTop: '15px' }}>
                    <li>📍 <strong>State Transitions:</strong> IDLE → RINGING → CONNECTED</li>
                    <li>⚡ <strong>Event Processing:</strong> IncomingCall, Answer, Hangup</li>
                    <li>📊 <strong>Performance Metrics:</strong> Transition durations</li>
                    <li>🧠 <strong>Context Data:</strong> Before/after state context</li>
                    <li>🏛️ <strong>Registry Status:</strong> Machine lifecycle tracking</li>
                  </ul>
                </div>
              )
            ) : (
              // Live mode display
              selectedMachine ? (
                <LiveHistoryDisplay 
                  liveHistory={liveHistory.length > 0 ? liveHistory : [{
                    stepNumber: 1,
                    fromState: 'Initial',
                    toState: liveState || 'IDLE',
                    event: 'Current State',
                    timestamp: new Date().toISOString(),
                    duration: 0,
                    eventData: {},
                    contextBefore: {
                      registryStatus: { status: 'ACTIVE', hydrated: false, online: true },
                      persistentContext: {},
                      volatileContext: {}
                    },
                    contextAfter: {
                      registryStatus: { status: 'ACTIVE', hydrated: false, online: true },
                      persistentContext: {},
                      volatileContext: {}
                    }
                  }]}
                  countdownState={countdownState}
                  countdownRemaining={countdownRemaining}
                />
              ) : (
                <div className="empty-state">
                  <h3>🔴 Live Monitoring Active</h3>
                  <p>{isConnected ? 'Select a machine from the dropdown to view its state' : 'Connecting to WebSocket server...'}</p>
                </div>
              )
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

export default MonitoringApp;