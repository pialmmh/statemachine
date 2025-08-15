import React, { useState, useEffect, useRef } from 'react';
import './MonitoringApp.css';

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
  
  const wsRef = useRef(null);
  const wsUrl = 'ws://localhost:9999';
  const hasReceivedInitialState = useRef(false);
  const countdownIntervalRef = useRef(null);

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

  const connectWebSocket = () => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return;

    try {
      wsRef.current = new WebSocket(wsUrl);

      wsRef.current.onopen = () => {
        console.log('WebSocket connected for live mode');
        setIsConnected(true);
        // Request initial state
        const request = {
          action: 'GET_STATE'
        };
        wsRef.current.send(JSON.stringify(request));
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
      
      // Handle initial CURRENT_STATE message with context
      if (data.type === 'CURRENT_STATE') {
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
      } else if (data.type === 'STATE_CHANGE') {
        // Update live state
        const newState = data.newState || data.state;
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
          
          // Get the previous context (from last transition's contextAfter)
          const previousContext = updated.length > 0 
            ? updated[updated.length - 1].contextAfter.persistentContext 
            : {};
          
          const transition = {
            stepNumber: updated.length + 1,
            fromState: data.oldState || 'UNKNOWN',
            toState: data.newState || 'UNKNOWN',
            event: data.eventName || 'Unknown',
            timestamp: data.timestamp || new Date().toISOString(),
            duration: data.duration || 0,
            eventData: data.payload || {},
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
    if (!isConnected || !wsRef.current) {
      console.error('Not connected to WebSocket');
      return;
    }

    try {
      const payload = eventPayload ? JSON.parse(eventPayload) : {};
      const message = {
        type: 'EVENT',
        eventType: selectedEvent,
        payload: payload
      };
      
      console.log('Sending event:', message);
      wsRef.current.send(JSON.stringify(message));
    } catch (error) {
      console.error('Error sending event:', error);
      alert('Invalid JSON payload');
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
        setEventPayload('{\n  "ringNumber": 1\n}');
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
            <span>📋 {currentMode === 'live' ? 'Live Sessions' : 'Recent Runs'}</span>
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
                      onClick={sendEvent}
                      disabled={!isConnected}
                    >
                      Send Event →
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
              // Live mode display - group by state instances (preserve chronological order)
              liveHistory.length > 0 ? (
                (() => {
                  // Group transitions by state instances (each entry to a state is a new instance)
                  const stateInstances = [];
                  let currentInstance = null;
                  
                  liveHistory.forEach(transition => {
                    // If this transition enters a new state, create a new instance
                    if (!currentInstance || currentInstance.state !== transition.toState) {
                      currentInstance = {
                        state: transition.toState,
                        instanceNumber: stateInstances.filter(i => i.state === transition.toState).length + 1,
                        transitions: []
                      };
                      stateInstances.push(currentInstance);
                    }
                    
                    // Add transition to current instance
                    currentInstance.transitions.push(transition);
                  });

                  return stateInstances.map((instance, groupIndex) => (
                    <div key={`state-${instance.state}-${instance.instanceNumber}`} style={{ 
                      background: '#fff', 
                      borderRadius: '8px', 
                      marginBottom: '20px', 
                      overflow: 'hidden', 
                      boxShadow: '0 2px 4px rgba(0,0,0,0.1)' 
                    }}>
                      {/* State Header */}
                      <div style={{ 
                        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)', 
                        color: 'white', 
                        padding: '12px 20px', 
                        position: 'relative' 
                      }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
                            <h3 style={{ margin: 0, fontSize: '16px' }}>
                              State: {instance.state}
                              {instance.instanceNumber > 1 && (
                                <span style={{ opacity: 0.8, fontSize: '14px' }}> (#{instance.instanceNumber})</span>
                              )}
                            </h3>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', fontSize: '13px', opacity: 0.9 }}>
                              <span>🔄 {instance.transitions.length} event{instance.transitions.length > 1 ? 's' : ''}</span>
                              <span style={{ opacity: 0.7 }}>|</span>
                              <span>Steps {instance.transitions[0].stepNumber}-{instance.transitions[instance.transitions.length - 1].stepNumber}</span>
                              <span style={{ opacity: 0.7 }}>|</span>
                              <span>🕒 {instance.transitions[0].timestamp ? instance.transitions[0].timestamp.split('T')[1] || instance.transitions[0].timestamp : new Date().toLocaleTimeString()}</span>
                            </div>
                          </div>
                          {/* Countdown Timer */}
                          {countdownState === instance.state && countdownRemaining > 0 && (
                            <div style={{ 
                              background: 'rgba(255,255,255,0.2)', 
                              padding: '5px 12px', 
                              borderRadius: '20px', 
                              fontSize: '14px',
                              fontWeight: '600',
                              display: 'flex',
                              alignItems: 'center',
                              gap: '8px'
                            }}>
                              <span>⏱️</span>
                              <span>{countdownRemaining}s</span>
                            </div>
                          )}
                        </div>
                      </div>

                      {/* Transitions for this state instance */}
                      {instance.transitions.map(transition => {
                        const eventBgColor = transition.event === 'Initial State' ? '#e8f5e9' : '#d1ecf1';
                        const eventColor = transition.event === 'Initial State' ? '#4caf50' : '#17a2b8';
                        
                        return (
                          <div key={transition.stepNumber} style={{ 
                            background: 'white', 
                            border: '1px solid #dee2e6', 
                            borderRadius: '10px', 
                            margin: '15px', 
                            overflow: 'hidden', 
                            boxShadow: '0 2px 6px rgba(0,0,0,0.05)' 
                          }}>
                            {/* Step Header */}
                            <div style={{ 
                              background: eventBgColor, 
                              padding: '10px 15px', 
                              borderBottom: '1px solid #dee2e6', 
                              display: 'flex', 
                              justifyContent: 'space-between', 
                              alignItems: 'center' 
                            }}>
                              <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
                                <span style={{ fontWeight: 600, color: '#495057' }}>Step {transition.stepNumber}</span>
                                <span style={{ 
                                  background: eventColor, 
                                  color: 'white', 
                                  padding: '3px 10px', 
                                  borderRadius: '4px', 
                                  fontSize: '12px', 
                                  fontWeight: 600 
                                }}>
                                  {transition.event}
                                </span>
                                <span style={{ fontSize: '12px', color: '#6c757d' }}>
                                  {transition.fromState === 'Initial' 
                                    ? `Initial State: ${transition.toState}` 
                                    : `${transition.fromState} → ${transition.toState}`}
                                </span>
                              </div>
                            </div>

                            {/* Three-column grid for context */}
                            <div style={{ 
                              padding: '15px', 
                              display: 'grid', 
                              gridTemplateColumns: '1fr 1fr 1fr', 
                              gap: '15px' 
                            }}>
                              {/* Event Payload */}
                              <div style={{ 
                                background: '#f8f9fa', 
                                borderRadius: '6px', 
                                padding: '12px', 
                                borderLeft: '3px solid #28a745' 
                              }}>
                                <div style={{ 
                                  fontWeight: 600, 
                                  color: '#28a745', 
                                  marginBottom: '8px', 
                                  fontSize: '13px' 
                                }}>
                                  📌 Event Payload
                                </div>
                                <pre style={{ 
                                  margin: 0, 
                                  background: 'white', 
                                  padding: '8px', 
                                  borderRadius: '4px', 
                                  overflowX: 'auto', 
                                  fontSize: '10px', 
                                  lineHeight: 1.3 
                                }}>
                                  {JSON.stringify(transition.eventData || {}, null, 2)}
                                </pre>
                              </div>

                              {/* Registry Status Before */}
                              <div style={{ 
                                background: '#fff5f5', 
                                borderRadius: '6px', 
                                padding: '12px', 
                                borderLeft: '3px solid #dc3545', 
                                fontSize: '11px' 
                              }}>
                                <div style={{ fontWeight: 600, color: '#dc3545', marginBottom: '8px' }}>
                                  Registry Status Before:
                                </div>
                                <div style={{ fontFamily: 'monospace', fontSize: '11px', lineHeight: 1.4, marginBottom: '12px' }}>
                                  Status: {transition.contextBefore?.registryStatus?.status || 'ACTIVE'}<br />
                                  Hydrated: {transition.contextBefore?.registryStatus?.hydrated ? '✅ Yes' : '🔄 No'}<br />
                                  Online: {transition.contextBefore?.registryStatus?.online !== false ? '🟢 Yes' : '🔴 No'}
                                </div>
                                <div style={{ marginBottom: '8px' }}>
                                  <div style={{ fontWeight: 600, color: '#495057', marginBottom: '4px', fontSize: '11px' }}>
                                    Persistent Context:
                                  </div>
                                  <pre style={{ 
                                    margin: 0, 
                                    fontSize: '10px', 
                                    fontFamily: 'monospace', 
                                    background: '#f8f9fa', 
                                    padding: '8px', 
                                    borderRadius: '4px', 
                                    lineHeight: 1.3 
                                  }}>
                                    {JSON.stringify(transition.contextBefore?.persistentContext || {}, null, 2)}
                                  </pre>
                                </div>
                                <div>
                                  <div style={{ fontWeight: 600, color: '#495057', marginBottom: '4px', fontSize: '11px' }}>
                                    Volatile Context:
                                  </div>
                                  <pre style={{ 
                                    margin: 0, 
                                    fontSize: '10px', 
                                    fontFamily: 'monospace', 
                                    background: '#f8f9fa', 
                                    padding: '8px', 
                                    borderRadius: '4px', 
                                    lineHeight: 1.3 
                                  }}>
                                    {JSON.stringify(transition.contextBefore?.volatileContext || {}, null, 2)}
                                  </pre>
                                </div>
                              </div>

                              {/* Registry Status After */}
                              <div style={{ 
                                background: '#f0f8ff', 
                                borderRadius: '6px', 
                                padding: '12px', 
                                borderLeft: '3px solid #17a2b8', 
                                fontSize: '11px' 
                              }}>
                                <div style={{ fontWeight: 600, color: '#17a2b8', marginBottom: '8px' }}>
                                  Registry Status After:
                                </div>
                                <div style={{ fontFamily: 'monospace', fontSize: '11px', lineHeight: 1.4, marginBottom: '12px' }}>
                                  Status: {transition.contextAfter?.registryStatus?.status || 'ACTIVE'}<br />
                                  Hydrated: {transition.contextAfter?.registryStatus?.hydrated ? '✅ Yes' : '🔄 No'}<br />
                                  Online: {transition.contextAfter?.registryStatus?.online !== false ? '🟢 Yes' : '🔴 No'}
                                </div>
                                <div style={{ marginBottom: '8px' }}>
                                  <div style={{ fontWeight: 600, color: '#495057', marginBottom: '4px', fontSize: '11px' }}>
                                    Persistent Context:
                                  </div>
                                  <pre style={{ 
                                    margin: 0, 
                                    fontSize: '10px', 
                                    fontFamily: 'monospace', 
                                    background: '#f8f9fa', 
                                    padding: '8px', 
                                    borderRadius: '4px', 
                                    lineHeight: 1.3 
                                  }}>
                                    {JSON.stringify(transition.contextAfter?.persistentContext || {}, null, 2)}
                                  </pre>
                                </div>
                                <div>
                                  <div style={{ fontWeight: 600, color: '#495057', marginBottom: '4px', fontSize: '11px' }}>
                                    Volatile Context:
                                  </div>
                                  <pre style={{ 
                                    margin: 0, 
                                    fontSize: '10px', 
                                    fontFamily: 'monospace', 
                                    background: '#f8f9fa', 
                                    padding: '8px', 
                                    borderRadius: '4px', 
                                    lineHeight: 1.3 
                                  }}>
                                    {JSON.stringify(transition.contextAfter?.volatileContext || {}, null, 2)}
                                  </pre>
                                </div>
                              </div>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  ));
                })()
              ) : (
                <div className="empty-state">
                  <h3>🔴 Live Monitoring Active</h3>
                  <p>{isConnected ? 'Connected and waiting for state transitions...' : 'Connecting to WebSocket server...'}</p>
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