import React, { useState, useEffect, useRef } from 'react';
import './MonitoringApp.css';
import LiveHistoryDisplay from './components/LiveHistoryDisplay';
import wsLogger from './wsLogger';
import treeViewStore from './store/treeViewStore';

function MonitoringApp({ mode = 'snapshot' }) {
  const [currentMode, setCurrentMode] = useState(mode);
  const [runs, setRuns] = useState([]);
  const [selectedRun, setSelectedRun] = useState(null);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(false);
  const [isConnected, setIsConnected] = useState(false);
  const [liveState, setLiveState] = useState('IDLE');
  const [liveHistory, setLiveHistory] = useState([]);
  const [mysqlHistory, setMysqlHistory] = useState([]); // MySQL history for Event Viewer
  const [selectedEvent, setSelectedEvent] = useState('INCOMING_CALL');
  const [eventPayload, setEventPayload] = useState('{\n  "phoneNumber": "+1-555-9999"\n}');
  const [countdownState, setCountdownState] = useState(null);
  const [countdownRemaining, setCountdownRemaining] = useState(0);
  const [viewMode, setViewMode] = useState('tree'); // 'tree' or 'events'
  const [liveMachines, setLiveMachines] = useState([]);
  const [selectedMachine, setSelectedMachine] = useState(null);
  const [treeViewData, setTreeViewData] = useState({ stateInstances: [] }); // Subscribe to treeViewStore
  const [lastAddedMachine, setLastAddedMachine] = useState(null);
  const [lastRemovedMachine, setLastRemovedMachine] = useState(null);
  const [arbitraryMachineId, setArbitraryMachineId] = useState('');
  const [offlineMachines, setOfflineMachines] = useState([]);
  const [offlineSelectedEvent, setOfflineSelectedEvent] = useState('INCOMING_CALL');
  const [offlineEventPayload, setOfflineEventPayload] = useState('{\n  "phoneNumber": "+1-555-9999"\n}');
  
  const wsRef = useRef(null);
  const wsUrl = 'ws://localhost:9999';
  const hasReceivedInitialState = useRef(false);
  const countdownIntervalRef = useRef(null);
  const selectedMachineRef = useRef(null);
  const lastHistoryId = useRef(0);
  const historyPollInterval = useRef(null);
  const historySessionId = useRef(null); // Track current history session
  const lastEventCount = useRef(0); // Track last event count to prevent flickering

  // Update currentMode when prop changes
  useEffect(() => {
    setCurrentMode(mode);
  }, [mode]);
  
  // Keep selectedMachineRef in sync with selectedMachine
  useEffect(() => {
    selectedMachineRef.current = selectedMachine;
    wsLogger.debug('Updated selectedMachineRef to: ' + selectedMachine);
  }, [selectedMachine]);

  // Auto-refresh Event Viewer history every 1 second
  useEffect(() => {
    if (viewMode === 'events' && selectedMachine && isConnected) {
      // Request initial Event Viewer history
      requestEventViewerHistory(selectedMachine);
      lastEventCount.current = 0; // Reset event count when switching machines
      
      // Set up interval to refresh every 1 second
      const intervalId = setInterval(() => {
        if (selectedMachineRef.current) {
          requestEventViewerHistory(selectedMachineRef.current);
        }
      }, 1000);
      
      // Cleanup interval on unmount or when conditions change
      return () => {
        wsLogger.debug('Stopping Event Viewer auto-refresh');
        clearInterval(intervalId);
        lastEventCount.current = 0; // Reset on cleanup
      };
    }
  }, [viewMode, selectedMachine, isConnected]);

  // Subscribe to treeViewStore updates
  useEffect(() => {
    const unsubscribe = treeViewStore.subscribe((state) => {
      console.log('[MonitoringApp] TreeView store updated, version:', state.version);
      wsLogger.log('MonitoringApp', 'TreeView store subscription callback, stateInstances count:', state.stateInstances?.length || 0);
      wsLogger.log('MonitoringApp', 'TreeView store subscription callback, stateInstances:', JSON.stringify(state.stateInstances));
      setTreeViewData(state);
    });
    
    // Get initial state
    const initialState = treeViewStore.getSnapshot();
    wsLogger.log('MonitoringApp', 'Initial treeViewStore state, stateInstances count:', initialState.stateInstances?.length || 0);
    setTreeViewData(initialState);
    
    return unsubscribe;
  }, []);

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
      stopHistoryPolling();
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
      wsLogger.debug('Using sample data:', error);
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
      wsLogger.debug('Using sample history:', error);
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

  // Helper function to log WebSocket events back to server
  const logWebSocketEvent = (direction, eventData) => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      try {
        const logMessage = {
          type: 'LOG',
          source: 'react_app',
          direction: direction, // 'sent' or 'received'
          timestamp: new Date().toISOString(),
          selectedMachine: selectedMachineRef.current,
          eventData: eventData
        };
        wsRef.current.send(JSON.stringify(logMessage));
      } catch (error) {
        wsLogger.error('Error logging WebSocket event:', error);
      }
    }
  };

  // Helper function to safely send WebSocket messages
  const sendWebSocketMessage = (message) => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      try {
        const messageStr = typeof message === 'string' ? message : JSON.stringify(message);
        wsRef.current.send(messageStr);
        
        // Log the sent message (but not if it's already a LOG message)
        if (typeof message === 'object' && message.type !== 'LOG') {
          logWebSocketEvent('sent', message);
        }
        
        return true;
      } catch (error) {
        wsLogger.error('Error sending WebSocket message:', error);
        return false;
      }
    } else {
      wsLogger.debug('WebSocket not ready, message not sent:', message);
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
        wsLogger.debug('WebSocket connected for live mode');
        setIsConnected(true);
        
        // Set WebSocket connection for treeViewStore
        treeViewStore.setWebSocket(wsRef.current);
        
        // Set WebSocket reference in logger
        wsLogger.setWebSocket(wsRef.current);
        
        // Use safe send function with a small delay to ensure connection is fully established
        setTimeout(() => {
          // Request list of machines
          sendWebSocketMessage({
            action: 'GET_MACHINES'
          });
          
          // Request list of offline machines
          sendWebSocketMessage({
            action: 'GET_OFFLINE_MACHINES'
          });
          
          // Request initial state
          sendWebSocketMessage({
            action: 'GET_STATE'
          });
          
          // Request registry status to get last added/removed machines
          sendWebSocketMessage({
            action: 'GET_REGISTRY_STATE'
          });
        }, 50);
      };

      wsRef.current.onmessage = (event) => {
        // Disabled to reduce console noise
        // wsLogger.debug('Raw WebSocket message:', event.data);
        
        // Log received message (but not LOG messages to avoid loops)
        try {
          const data = JSON.parse(event.data);
          if (data.type !== 'LOG') {
            logWebSocketEvent('received', data);
          }
        } catch (e) {
          // If not JSON, still log it
          logWebSocketEvent('received', { rawData: event.data });
        }
        
        handleWebSocketMessage(event.data);
      };

      wsRef.current.onclose = () => {
        wsLogger.debug('WebSocket disconnected');
        setIsConnected(false);
        wsRef.current = null;
        // Reconnect if still in live mode
        if (currentMode === 'live') {
          setTimeout(connectWebSocket, 3000);
        }
      };

      wsRef.current.onerror = (error) => {
        wsLogger.error('WebSocket error:', error);
        setIsConnected(false);
      };
    } catch (error) {
      wsLogger.error('Failed to connect:', error);
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
      // Only log important message types
      if (data.type !== 'HISTORY_UPDATE' || (data.newEntries && data.newEntries.length > 0)) {
        wsLogger.debug('WebSocket message type:', data.type);
      }
      
      // Handle machine list
      if (data.type === 'MACHINES_LIST') {
        const machines = data.machines || [];
        wsLogger.debug('MACHINES_LIST received:', machines.map(m => m.id));
        setLiveMachines(machines);
        // Don't auto-select any machine - let user choose
        if (selectedMachine) {
          wsLogger.debug('Machine already selected:', selectedMachine);
        }
      } else if (data.type === 'MACHINE_REGISTERED') {
        // Add new machine to list
        setLiveMachines(prev => [...prev, { id: data.machineId, type: data.machineType || 'StateMachine' }]);
        setLastAddedMachine(data.machineId);
        // Request registry status to get latest info
        requestRegistryStatus();
      } else if (data.type === 'MACHINE_UNREGISTERED') {
        // Remove machine from list
        setLiveMachines(prev => prev.filter(m => m.id !== data.machineId));
        setLastRemovedMachine(data.machineId);
        if (selectedMachine === data.machineId) {
          setSelectedMachine(null);
        }
        // Request registry status to get latest info
        requestRegistryStatus();
      } else if (data.type === 'REGISTRY_STATE') {
        // Update registry state including last added/removed
        if (data.lastAddedMachine) {
          setLastAddedMachine(data.lastAddedMachine);
        }
        if (data.lastRemovedMachine) {
          setLastRemovedMachine(data.lastRemovedMachine);
        }
      } else if (data.type === 'OFFLINE_MACHINES_LIST') {
        // Update offline machines list
        if (data.machines) {
          setOfflineMachines(data.machines);
          wsLogger.debug('Received offline machines:', data.machines.length);
        }
      } else if (data.type === 'CURRENT_STATE') {
        wsLogger.debug('CURRENT_STATE message received. Has context?', !!data.context, 'Has received initial?', hasReceivedInitialState.current);
        setLiveState(data.currentState || 'IDLE');
        
        // If we haven't received initial state yet, add it
        if (!hasReceivedInitialState.current) {
          wsLogger.debug('Adding initial state');
          hasReceivedInitialState.current = true;
          
          // Add initial state to history
          const initialTransition = {
            id: `initial-${Date.now()}-${Math.random()}`,
            stepNumber: 1,
            fromState: data.currentState || 'IDLE',
            toState: data.currentState || 'IDLE',
            event: 'Entry',
            isEntryStep: true,
            stateChange: false,
            timestamp: data.timestamp || new Date().toISOString(),
            duration: 0,
            eventData: {},
            entryActionStatus: 'none',
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
        wsLogger.debug('MACHINE_STATE received:', data);
        
        if (data.machineId === selectedMachine) {
          setLiveState(data.currentState || 'IDLE');
          
          // Always set up initial history entry when we get machine state
          const initialTransition = {
            id: `machine-state-${Date.now()}-${Math.random()}`,
            stepNumber: 1,
            fromState: data.currentState || 'IDLE',
            toState: data.currentState || 'IDLE',
            event: 'Entry',
            isEntryStep: true,
            stateChange: false,
            timestamp: data.timestamp || new Date().toISOString(),
            duration: 0,
            eventData: {},
            entryActionStatus: 'none',
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
        wsLogger.debug('COMPLETE_STATUS received - machines count:', data.machines ? data.machines.length : 0);
        wsLogger.debug('Currently selected machine:', selectedMachine);
        wsLogger.debug('hasReceivedInitialState:', hasReceivedInitialState.current);
        
        // Don't process COMPLETE_STATUS for history - it causes duplicates
        // We only use it for updating the machine list and current state
        // History should only be updated by explicit STATE_CHANGE events
        
        // Process machines from complete status
        if (data.machines && Array.isArray(data.machines)) {
          // Find the selected machine's data
          const selectedMachineData = data.machines.find(m => m.machineId === selectedMachine);
          
          if (selectedMachineData) {
            // Update live state for selected machine
            setLiveState(selectedMachineData.currentState || 'IDLE');
            
            // Skip adding initial state from COMPLETE_STATUS
            // Let MACHINE_STATE handle initial state setup
          }
        }
      } else if (data.type === 'EVENT_SENT') {
        // Track the event that was sent to the state machine
        wsLogger.debug('EVENT_SENT received:', data);
        
        // Add event to history as a separate entry
        setLiveHistory(prev => {
          const updated = [...(Array.isArray(prev) ? prev : [])];
          const event = {
            stepNumber: updated.length + 1,
            fromState: data.currentState || liveState,
            toState: data.currentState || liveState, // Event doesn't change state yet
            event: data.eventType || 'Unknown Event',
            eventSent: true, // Mark this as an event that was sent
            timestamp: data.timestamp || new Date().toISOString(),
            duration: 0,
            eventData: data.payload || {},
            entryActionStatus: 'none',
            contextBefore: {
              registryStatus: { status: 'ACTIVE', hydrated: false, online: true },
              persistentContext: updated.length > 0 ? (updated[updated.length - 1].contextAfter?.persistentContext || updated[updated.length - 1].persistentContext || {}) : {},
              volatileContext: {}
            },
            contextAfter: {
              registryStatus: { status: 'ACTIVE', hydrated: false, online: true },
              persistentContext: updated.length > 0 ? (updated[updated.length - 1].contextAfter?.persistentContext || updated[updated.length - 1].persistentContext || {}) : {},
              volatileContext: {}
            }
          };
          
          wsLogger.debug('Adding event to history:', event.event);
          updated.push(event);
          return updated;
        });
      } else if (data.type === 'TREEVIEW_STORE_UPDATE') {
        // Handle treeview store update from backend
        wsLogger.debug('TREEVIEW_STORE_UPDATE received, version:', data.store?.version);
        wsLogger.log('MonitoringApp', 'TREEVIEW_STORE_UPDATE stateInstances count:', data.store?.stateInstances?.length || 0);
        wsLogger.log('MonitoringApp', 'TREEVIEW_STORE_UPDATE data.store.stateInstances:', JSON.stringify(data.store?.stateInstances));
        if (data.store) {
          treeViewStore.replaceStore(data.store);
        }
      } else if (data.type === 'STATE_CHANGE') {
        wsLogger.debug('STATE_CHANGE received for machine:', data.machineId);
        wsLogger.debug('Currently selected machine (ref):', selectedMachineRef.current);
        wsLogger.debug('Currently selected machine (state):', selectedMachine);
        wsLogger.debug('Full STATE_CHANGE data:', JSON.stringify(data));
        
        // Only process state changes for the selected machine (use ref to avoid closure issues)
        if (data.machineId !== selectedMachineRef.current) {
          wsLogger.debug(`Ignoring state change: machineId=${data.machineId}, selected=${selectedMachineRef.current}`);
          return;
        }
        
        wsLogger.debug('Processing STATE_CHANGE for selected machine');
        
        // Update live state
        const newState = data.newState || data.stateAfter || data.state;
        wsLogger.debug('New state:', newState);
        setLiveState(newState);
        
        // Handle countdown if present in message
        if (data.countdown) {
          handleCountdown(data.countdown.state, data.countdown.remaining);
        } else {
          // If no countdown in message, check if this is a state with timeout
          // RINGING and CONNECTED states have 30-second timeouts
          if (newState === 'RINGING') {
            handleCountdown('RINGING', 30);
          } else if (newState === 'CONNECTED') {
            handleCountdown('CONNECTED', 30);
          } else {
            handleCountdown(null, 0);
          }
        }
        
        // Add to live history with full context
        wsLogger.debug('Adding STATE_CHANGE to history');
        wsLogger.debug('Current liveHistory before update:', liveHistory.length, 'items');
        
        setLiveHistory(prev => {
          const updated = [...(Array.isArray(prev) ? prev : [])];
          wsLogger.debug('Inside setLiveHistory - current length:', updated.length);
          wsLogger.debug('Last item in history:', updated.length > 0 ? updated[updated.length - 1] : 'empty');
          
          // Don't add initial state here - let COMPLETE_STATUS handle it
          // Just add the actual state transition
          
          // Get the previous context (from last transition's contextAfter)
          const previousContext = updated.length > 0 
            ? (updated[updated.length - 1].contextAfter?.persistentContext || updated[updated.length - 1].persistentContext || {})
            : {};
          
          // Detect timeout transitions by pattern
          const fromState = data.stateBefore || data.oldState || 'UNKNOWN';
          const toState = data.stateAfter || data.newState || 'UNKNOWN';
          let eventName = data.eventName || 'State Change';
          
          // Check if we just added this exact transition (check for duplicates)
          const lastTransition = updated.length > 0 ? updated[updated.length - 1] : null;
          
          if (lastTransition && 
              lastTransition.fromState === fromState && 
              lastTransition.toState === toState &&
              !lastTransition.eventSent &&
              Math.abs(new Date() - new Date(lastTransition.timestamp)) < 100) { // Within 100ms
            wsLogger.debug('WARNING: Duplicate state transition detected, skipping:', fromState, '->', toState);
            return updated; // Skip duplicate
          }
          
          // Common timeout patterns
          if (!data.eventName) {
            if (fromState === 'RINGING' && toState === 'IDLE') {
              eventName = 'Timeout'; // RINGING timeout after 30s
            } else if (fromState === 'CONNECTED' && toState === 'IDLE') {
              eventName = 'Timeout'; // CONNECTED timeout after 120s
            }
          }
          
          // Add the event/transition in the source state
          const transition = {
            id: `${Date.now()}-${Math.random()}`, // Add unique ID for debugging
            stepNumber: updated.length + 1,
            fromState: fromState,
            toState: toState,
            event: eventName,
            stateChange: true, // Mark this as a state change
            timestamp: data.timestamp || new Date().toISOString(),
            duration: data.duration || 0,
            eventData: data.payload || {},
            entryActionStatus: 'none', // No entry action status for transitions
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
          
          wsLogger.debug('Adding transition:', transition.fromState, '->', transition.toState, 'ID:', transition.id);
          wsLogger.debug('History length before push:', updated.length);
          wsLogger.debug('All transitions in history:', updated.map(t => `${t.fromState}->${t.toState} (ID: ${t.id})`));
          updated.push(transition);
          
          // Add entry step for the target state (only if it's a real state change)
          if (fromState !== toState) {
            const entryStep = {
              id: `${Date.now()}-entry-${Math.random()}`,
              stepNumber: updated.length + 1,
              fromState: toState,
              toState: toState,
              event: 'Entry',
              stateChange: false,
              isEntryStep: true,
              timestamp: new Date().toISOString(),
              duration: 0,
              eventData: {},
              entryActionStatus: data.entryActionStatus || 'none',
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
            wsLogger.debug('Adding entry step for state:', toState);
            updated.push(entryStep);
          }
          
          wsLogger.debug('History length after push:', updated.length);
          return updated;
        });
      } else if (data.type === 'EVENT_VIEWER_HISTORY') {
        // Event Viewer history received (MySQL raw data)
        const eventCount = data.events ? data.events.length : 0;
        
        // Only update UI if there are new events to prevent flickering
        if (data.machineId === selectedMachineRef.current && data.events) {
          if (eventCount !== lastEventCount.current) {
            wsLogger.debug('=== EVENT_VIEWER_HISTORY received (new events) ===');
            wsLogger.debug('  Machine ID:', data.machineId);
            wsLogger.debug('  Events: ', lastEventCount.current, '->', eventCount);
            setMysqlHistory(data.events);
            lastEventCount.current = eventCount;
          }
          // Else silently ignore - no new events
        }
      } else if (data.type === 'HISTORY_DATA') {
        // Full history data received
        wsLogger.debug('=== HISTORY_DATA received ===');
        wsLogger.debug('  Machine ID:', data.machineId);
        wsLogger.debug('  History length:', data.history ? data.history.length : 0);
        wsLogger.debug('  Raw history length:', data.rawHistory ? data.rawHistory.length : 0);
        wsLogger.debug('  Selected machine ref:', selectedMachineRef.current);
        wsLogger.debug('  Match:', data.machineId === selectedMachineRef.current);
        
        if (data.machineId === selectedMachineRef.current && data.history) {
          wsLogger.debug('Processing history for', data.machineId);
          
          // Store raw MySQL history for Event Viewer (if available, otherwise fall back to grouped)
          setMysqlHistory(data.rawHistory || data.history);
          
          // Generate a new session ID for this history load
          const sessionId = `${data.machineId}-${Date.now()}`;
          historySessionId.current = sessionId;
          wsLogger.debug('New history session:', sessionId);
          
          // Check if this is grouped format or raw format
          let historyToSet;
          if (data.history.length > 0 && data.history[0].state && data.history[0].transitions) {
            // Grouped format - pass directly without transformation
            wsLogger.debug('Received grouped history format - passing directly');
            historyToSet = data.history;
            
            // Get last ID from the last transition in the last group
            const lastGroup = data.history[data.history.length - 1];
            const lastTransitions = lastGroup.transitions || [];
            if (lastTransitions.length > 0) {
              const lastTransition = lastTransitions[lastTransitions.length - 1];
              lastHistoryId.current = lastTransition.id || 0;
            } else {
              lastHistoryId.current = 0;
            }
          } else {
            // Raw format - transform it
            wsLogger.debug('Received raw history format - transforming');
            const transformedHistory = transformBackendHistory(data.history);
            wsLogger.debug('Transformed', data.history.length, 'entries into', transformedHistory.length, 'frontend entries');
            historyToSet = transformedHistory;
            
            // Update last ID for incremental updates
            if (data.history.length > 0) {
              const lastEntry = data.history[data.history.length - 1];
              lastHistoryId.current = lastEntry.id || 0;
            } else {
              lastHistoryId.current = 0;
            }
          }
          
          wsLogger.debug('About to setLiveHistory with:', historyToSet.length, 'entries');
          setLiveHistory(historyToSet);
          wsLogger.debug('Set lastHistoryId to:', lastHistoryId.current);
          
          // Start polling for updates only if we have history
          if (historyToSet.length > 0) {
            wsLogger.debug('Starting history polling for', data.machineId);
            startHistoryPolling(data.machineId);
          }
        } else {
          wsLogger.debug('Not processing - machine mismatch or no history');
        }
      } else if (data.type === 'HISTORY_UPDATE') {
        // Incremental history update - only log if there are new entries
        if (data.newEntries && data.newEntries.length > 0) {
          wsLogger.debug('HISTORY_UPDATE received:', data.newEntries.length, 'new entries');
        }
        
        // Only process if it's for the current machine AND we have a matching session
        const currentSessionId = historySessionId.current;
        if (data.machineId === selectedMachineRef.current && 
            currentSessionId && 
            data.newEntries && 
            data.newEntries.length > 0) {
          
          // Additional check: verify the lastId matches what we're expecting
          if (data.lastId !== lastHistoryId.current) {
            wsLogger.debug('Ignoring HISTORY_UPDATE - lastId mismatch', {
              expected: lastHistoryId.current,
              received: data.lastId
            });
            return;
          }
          
          const newTransformed = transformBackendHistory(data.newEntries);
          setLiveHistory(prev => [...prev, ...newTransformed]);
          
          // Update last ID
          const lastEntry = data.newEntries[data.newEntries.length - 1];
          lastHistoryId.current = lastEntry.id || lastHistoryId.current;
        }
      } else if (data.type === 'CONNECTION_TEST') {
        // Ignore connection test messages
        wsLogger.debug('Connection test message received');
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
      wsLogger.error('Error parsing message:', error, message);
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

  // Transform backend history format to frontend format
  const transformBackendHistory = (backendHistory) => {
    if (!backendHistory || !Array.isArray(backendHistory)) {
      return [];
    }
    
    wsLogger.debug('=== transformBackendHistory called with', backendHistory.length, 'entries ===');
    wsLogger.debug('First entry:', backendHistory[0]);
    
    const transformed = [];
    let stepNumber = 1;
    
    backendHistory.forEach((entry, index) => {
      const isEntry = entry.event === 'ENTRY';
      const isBeforeEntry = entry.event === 'BEFORE_ENTRY_ACTIONS';
      const isAfterEntry = entry.event === 'AFTER_ENTRY_ACTIONS';
      const isTransition = entry.transitionOrStay === true;
      
      wsLogger.debug(`Entry ${index}: state=${entry.state}, event=${entry.event}, isEntry=${isEntry}, isTransition=${isTransition}`);
      
      // Determine the display event name
      let displayEvent = entry.event || 'Unknown';
      if (isEntry) {
        displayEvent = 'Entry';
      } else if (isBeforeEntry) {
        displayEvent = 'Before Entry Actions';
      } else if (isAfterEntry) {
        displayEvent = 'After Entry Actions';
      }
      
      // Determine entry action status based on event type
      let entryActionStatus = 'none';
      if (isBeforeEntry || isAfterEntry) {
        entryActionStatus = 'executed';
      } else if (isEntry) {
        entryActionStatus = 'none';  // No entry actions
      }
      
      const historyEntry = {
        stepNumber: stepNumber++,
        fromState: entry.state,
        toState: isTransition && entry.transitionToState ? entry.transitionToState : entry.state,
        event: displayEvent,
        timestamp: entry.datetime || new Date().toISOString(),
        duration: 0,
        eventData: entry.eventPayload || {},
        eventIgnored: entry.eventIgnored || false,
        stateChange: isTransition,
        eventSent: !isTransition && !isEntry && !isBeforeEntry && !isAfterEntry,
        entryActionStatus: entryActionStatus,
        isEntryStep: isEntry || isBeforeEntry || isAfterEntry,
        contextBefore: {
          registryStatus: { status: 'ACTIVE', hydrated: false, online: true },
          persistentContext: index > 0 && backendHistory[index - 1].persistentContext 
            ? backendHistory[index - 1].persistentContext : {},
          volatileContext: index > 0 && backendHistory[index - 1].volatileContext 
            ? backendHistory[index - 1].volatileContext : {}
        },
        contextAfter: {
          registryStatus: { status: 'ACTIVE', hydrated: false, online: true },
          persistentContext: entry.persistentContext || {},
          volatileContext: entry.volatileContext || {}
        }
      };
      
      transformed.push(historyEntry);
      wsLogger.debug(`Added transformed entry: Step #${historyEntry.stepNumber}, ${historyEntry.event}, isEntryStep=${historyEntry.isEntryStep}`);
    });
    
    wsLogger.debug('=== Total transformed entries:', transformed.length, '===');
    wsLogger.debug('Transformed history:', transformed);
    return transformed;
  };

  // Request full history from backend
  const requestMachineHistory = (machineId) => {
    if (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) {
      wsLogger.error('WebSocket not connected');
      return;
    }
    
    const request = {
      action: 'GET_HISTORY',
      machineId: machineId
    };
    
    wsLogger.debug('Requesting full history for:', machineId);
    wsRef.current.send(JSON.stringify(request));
  };

  // Request Event Viewer history (MySQL raw data)
  const requestEventViewerHistory = (machineId) => {
    if (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) {
      wsLogger.error('WebSocket not connected for Event Viewer');
      return;
    }
    
    const request = {
      action: 'GET_EVENT_VIEWER_HISTORY',
      machineId: machineId
    };
    
    wsLogger.debug('Requesting Event Viewer history for:', machineId);
    wsRef.current.send(JSON.stringify(request));
  };
  
  // Request registry status to get last added/removed machines
  const requestRegistryStatus = () => {
    if (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) {
      return;
    }
    
    const request = {
      action: 'GET_REGISTRY_STATE'
    };
    
    wsRef.current.send(JSON.stringify(request));
  };
  
  // Request incremental history updates
  const requestHistoryUpdate = (machineId) => {
    if (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) {
      return;
    }
    
    const request = {
      action: 'GET_HISTORY_SINCE',
      machineId: machineId,
      lastId: lastHistoryId.current
    };
    
    // Only log if we're debugging polling issues
    // wsLogger.debug('Requesting history update for:', machineId, 'since ID:', lastHistoryId.current);
    wsRef.current.send(JSON.stringify(request));
  };
  
  // Start polling for history updates
  const startHistoryPolling = (machineId) => {
    // Clear any existing polling
    if (historyPollInterval.current) {
      clearInterval(historyPollInterval.current);
      historyPollInterval.current = null;
    }
    
    // Store the session ID for this polling session
    const sessionId = historySessionId.current;
    
    // Poll for updates every 2 seconds (reduced frequency)
    historyPollInterval.current = setInterval(() => {
      // Check both machine ID and session ID
      if (selectedMachineRef.current === machineId && historySessionId.current === sessionId) {
        requestHistoryUpdate(machineId);
      } else {
        // Stop polling if machine or session changed
        wsLogger.debug('Stopping polling - machine or session changed');
        clearInterval(historyPollInterval.current);
        historyPollInterval.current = null;
      }
    }, 2000);
  };
  
  // Stop history polling
  const stopHistoryPolling = () => {
    if (historyPollInterval.current) {
      clearInterval(historyPollInterval.current);
      historyPollInterval.current = null;
    }
  };

  const sendEvent = () => {
    wsLogger.debug('sendEvent called');
    wsLogger.debug('isConnected:', isConnected, 'wsRef.current:', wsRef.current, 'selectedMachine:', selectedMachine);
    
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
      
      wsLogger.debug('Sending WebSocket event:', message);
      
      if (sendWebSocketMessage(message)) {
        wsLogger.debug('Event sent successfully');
        
        // Don't add the event to history here - let the backend STATE_CHANGE handle it
        // This was causing duplicate entries
        // The backend will send us the proper state change event
      } else {
        alert('Failed to send event. WebSocket connection might be unstable.');
      }
    } catch (error) {
      wsLogger.error('Error preparing event:', error);
      alert('Invalid JSON payload: ' + error.message);
    }
  };

  const sendEventToArbitraryMachine = () => {
    wsLogger.debug('sendEventToArbitraryMachine called');
    wsLogger.debug('isConnected:', isConnected, 'arbitraryMachineId:', arbitraryMachineId);
    
    if (!arbitraryMachineId || !arbitraryMachineId.trim()) {
      alert('Please select an offline machine');
      return;
    }

    if (!isConnected || !wsRef.current) {
      alert('WebSocket is not connected. Please wait for connection or refresh the page.');
      return;
    }

    try {
      const payload = offlineEventPayload ? JSON.parse(offlineEventPayload) : {};
      const message = {
        type: 'EVENT_TO_ARBITRARY',
        machineId: arbitraryMachineId.trim(),
        eventType: offlineSelectedEvent,
        payload: payload
      };
      
      wsLogger.debug('Sending event to arbitrary machine:', message);
      
      if (sendWebSocketMessage(message)) {
        wsLogger.debug('Event sent successfully to arbitrary machine:', arbitraryMachineId);
        
        // Clear the input after successful send
        setArbitraryMachineId('');
        
        // If this machine gets rehydrated, it should appear in the list
        // Request updated machine list after a short delay
        setTimeout(() => {
          sendWebSocketMessage({
            action: 'GET_MACHINES'
          });
        }, 500);
      } else {
        alert('Failed to send event. WebSocket connection might be unstable.');
      }
    } catch (error) {
      wsLogger.error('Error preparing event:', error);
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
      <div className="container">
        <div className="left-panel">
          {currentMode === 'snapshot' && (
            <div className="panel-header">
              <span>Recent Runs</span>
              <button className="refresh-btn" onClick={refreshData}>Refresh</button>
            </div>
          )}
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
                <div className="run-item" style={{ background: isConnected ? '#d4edda' : '#f8d7da', marginBottom: '8px', padding: '10px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '13px', fontWeight: '700', marginBottom: '6px', borderBottom: '1px solid rgba(0,0,0,0.1)', paddingBottom: '6px' }}>
                    <span>Registry Status</span>
                    <button 
                      className="refresh-btn" 
                      onClick={refreshData}
                      style={{ padding: '2px 6px', fontSize: '10px' }}
                    >
                      Refresh
                    </button>
                  </div>
                  <div style={{ fontSize: '12px', marginBottom: '4px' }}>
                    {isConnected ? '🟢 Connected' : '🔴 Disconnected'} • 📋 Machines: {liveMachines.length}
                  </div>
                  <div style={{ fontSize: '11px', color: '#495057' }}>
                    <span style={{ color: lastAddedMachine ? '#28a745' : '#6c757d' }}>
                      Last Added: <strong>{lastAddedMachine || 'none'}</strong>
                    </span>
                    <span style={{ margin: '0 4px', color: '#6c757d' }}>|</span>
                    <span style={{ color: lastRemovedMachine ? '#dc3545' : '#6c757d' }}>
                      Last Offline: <strong>{lastRemovedMachine || 'none'}</strong>
                    </span>
                  </div>
                </div>
                
                {isConnected && (
                  <div className="event-trigger-panel" style={{ paddingTop: '8px' }}>
                    <div className="panel-subtitle" style={{ marginBottom: '6px', fontSize: '13px' }}>🖥️ Select Online Machine</div>
                    <select 
                      className="event-select"
                      value={selectedMachine || ''}
                      onChange={(e) => {
                        const machineId = e.target.value;
                        wsLogger.debug('Machine selected:', machineId);
                        setSelectedMachine(machineId);
                        selectedMachineRef.current = machineId;  // Update ref to avoid closure issues
                        
                        // Send selection to backend via treeViewStore
                        treeViewStore.selectMachine(machineId);
                        
                        // Request MySQL history for Event Viewer
                        if (machineId) {
                          requestMachineHistory(machineId);
                        }
                        
                        // When a machine is selected, request its history from backend
                        if (machineId) {
                          wsLogger.debug('Requesting history for machine:', machineId);
                          
                          // Stop polling for the previous machine
                          stopHistoryPolling();
                          
                          // Clear state for new machine
                          hasReceivedInitialState.current = false;
                          lastHistoryId.current = 0;
                          historySessionId.current = null; // Clear session ID
                          setLiveHistory([]);
                          
                          // Request the machine's history from backend
                          requestMachineHistory(machineId);
                          
                          // Request the machine's current state using safe send
                          const request = {
                            action: 'GET_MACHINE_STATE',
                            machineId: machineId
                          };
                          wsLogger.debug('Sending WebSocket request:', request);
                          sendWebSocketMessage(request);
                        } else {
                          // User deselected machine (selected empty option)
                          wsLogger.debug('Machine deselected');
                          stopHistoryPolling();
                          setLiveHistory([]);
                          hasReceivedInitialState.current = false;
                          lastHistoryId.current = 0;
                          historySessionId.current = null; // Clear session ID
                        }
                      }}
                      style={{ marginBottom: '12px' }}
                    >
                      <option value="">Select Machine</option>
                      {liveMachines.map(machine => (
                        <option key={machine.id} value={machine.id}>
                          {machine.type} - {machine.id}
                        </option>
                      ))}
                    </select>
                    
                    <div className="panel-subtitle" style={{ marginBottom: '6px', marginTop: '10px', fontSize: '13px' }}>📮 Send Event</div>
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
                        wsLogger.debug('Button clicked!');
                        wsLogger.debug('Current state - isConnected:', isConnected, 'selectedMachine:', selectedMachine);
                        sendEvent();
                      }}
                      disabled={!isConnected || !selectedMachine}
                    >
                      Send Event → {!isConnected ? '(Not Connected)' : !selectedMachine ? '(No Machine Selected)' : ''}
                    </button>
                    
                    {/* Horizontal Separator Line */}
                    <hr style={{ 
                      margin: '15px 0 12px 0',
                      border: 'none',
                      borderTop: '2px solid #e9ecef'
                    }} />
                    
                    {/* Send to Offline Debug Machines */}
                    <div>
                      <div className="panel-subtitle" style={{ marginBottom: '6px', fontSize: '13px' }}>🖥️ Select Offline Machine</div>
                      
                      {/* Offline Machine Selector */}
                      <select 
                        className="event-select"
                        value={arbitraryMachineId}
                        onChange={(e) => setArbitraryMachineId(e.target.value)}
                        style={{ marginBottom: '12px' }}
                      >
                        <option value="">Select Machine</option>
                        {offlineMachines.map(machine => (
                          <option key={machine.id} value={machine.id}>
                            {machine.type || 'StateMachine'} - {machine.id}
                          </option>
                        ))}
                      </select>
                      
                      <div className="panel-subtitle" style={{ marginBottom: '6px', marginTop: '10px', fontSize: '13px' }}>📮 Send Event</div>
                      
                      {/* Event Type Selector for Offline Machines */}
                      <select 
                        className="event-select"
                        value={offlineSelectedEvent}
                        onChange={(e) => setOfflineSelectedEvent(e.target.value)}
                      >
                        <option value="INCOMING_CALL">INCOMING_CALL</option>
                        <option value="ANSWER">ANSWER</option>
                        <option value="HANGUP">HANGUP</option>
                        <option value="SESSION_PROGRESS">SESSION_PROGRESS</option>
                        <option value="REJECT">REJECT</option>
                        <option value="BUSY">BUSY</option>
                        <option value="TIMEOUT">TIMEOUT</option>
                      </select>
                      
                      {/* Event Payload for Offline Machines */}
                      <div className="payload-section">
                        <label>Event Payload (JSON):</label>
                        <textarea 
                          className="payload-input"
                          value={offlineEventPayload}
                          onChange={(e) => setOfflineEventPayload(e.target.value)}
                          placeholder='{"phoneNumber": "+1-555-9999"}'
                          rows="4"
                        />
                      </div>
                      
                      {/* Send Button for Offline Machines */}
                      <button 
                        className="send-event-btn"
                        onClick={() => {
                          wsLogger.debug('Sending event to arbitrary machine:', arbitraryMachineId);
                          sendEventToArbitraryMachine();
                        }}
                        disabled={!isConnected || !arbitraryMachineId || !arbitraryMachineId.trim()}
                      >
                        Send Event → {!isConnected ? '(Not Connected)' : !arbitraryMachineId ? '(No Machine Selected)' : ''}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>

        <div className="right-panel">
          <div className="panel-header">
            {currentMode === 'live' ? (
              <>
                {selectedMachine ? (
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px', justifyContent: 'flex-start' }}>
                    <div style={{
                      width: '400px',
                      marginLeft: '-10px',
                      padding: '8px 12px',
                      background: '#495057',
                      border: '1px solid #6c757d',
                      borderRadius: '6px',
                      fontSize: '13px',
                      fontWeight: '500',
                      color: '#f8f9fa',
                      fontFamily: '"Inter", sans-serif'
                    }}>
                      Machine: {selectedMachine}, Current State: <span style={{ color: '#ffc107', fontWeight: '600' }}>{liveState || 'IDLE'}</span>
                      {countdownState === liveState && countdownRemaining > 0 && (
                        <span style={{ 
                          marginLeft: '12px', 
                          color: '#ff6b6b',
                          fontWeight: '600',
                          animation: countdownRemaining <= 5 ? 'pulse 1s infinite' : 'none'
                        }}>
                          ⏱️ {countdownRemaining}s
                        </span>
                      )}
                    </div>
                    <button
                      onClick={() => setViewMode('tree')}
                      style={{
                        padding: '5px 12px',
                        fontSize: '12px',
                        fontWeight: '500',
                        border: '1px solid',
                        borderColor: viewMode === 'tree' ? '#17a2b8' : '#6c757d',
                        background: viewMode === 'tree' ? '#17a2b8' : '#6c757d',
                        color: 'white',
                        borderRadius: '4px',
                        cursor: 'pointer',
                        transition: 'all 0.2s ease',
                        whiteSpace: 'nowrap',
                        fontFamily: '"Inter", sans-serif',
                        opacity: viewMode === 'tree' ? 1 : 0.8
                      }}
                      onMouseEnter={(e) => {
                        if (viewMode !== 'tree') {
                          e.target.style.opacity = '1';
                        }
                      }}
                      onMouseLeave={(e) => {
                        if (viewMode !== 'tree') {
                          e.target.style.opacity = '0.8';
                        }
                      }}
                    >
                      🌳 Transition Tree
                    </button>
                    <button
                      onClick={() => setViewMode('events')}
                      style={{
                        padding: '5px 12px',
                        fontSize: '12px',
                        fontWeight: '500',
                        border: '1px solid',
                        borderColor: viewMode === 'events' ? '#17a2b8' : '#6c757d',
                        background: viewMode === 'events' ? '#17a2b8' : '#6c757d',
                        color: 'white',
                        borderRadius: '4px',
                        cursor: 'pointer',
                        transition: 'all 0.2s ease',
                        whiteSpace: 'nowrap',
                        fontFamily: '"Inter", sans-serif',
                        opacity: viewMode === 'events' ? 1 : 0.8
                      }}
                      onMouseEnter={(e) => {
                        if (viewMode !== 'events') {
                          e.target.style.opacity = '1';
                        }
                      }}
                      onMouseLeave={(e) => {
                        if (viewMode !== 'events') {
                          e.target.style.opacity = '0.8';
                        }
                      }}
                    >
                      📋 Event Viewer
                    </button>
                  </div>
                ) : (
                  <span style={{ color: '#999' }}>No Machine Selected</span>
                )}
                {liveHistory.length > 0 && (
                  <button 
                    className="refresh-btn" 
                    onClick={() => setLiveHistory([])}
                    style={{ marginLeft: 'auto' }}
                  >
                    Clear History
                  </button>
                )}
              </>
            ) : (
              'State Machine History'
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
              // Live mode display - always show tree when connected
              isConnected ? (
                (() => {
                  wsLogger.debug('Rendering LiveHistoryDisplay with treeViewData:', treeViewData);
                  // Pass transitions and selectedMachineId from treeViewStore
                  const transitions = treeViewData.transitions || [];
                  const selectedMachineId = treeViewData.selectedMachineId || null;
                  
                  wsLogger.log('MonitoringApp', 'Selected machine:', selectedMachineId);
                  wsLogger.log('MonitoringApp', 'Transitions count:', transitions.length);
                  
                  return (
                <LiveHistoryDisplay 
                  wsConnection={wsRef.current}
                  transitions={transitions}
                  mysqlHistory={mysqlHistory}
                  selectedMachineId={selectedMachineId}
                  countdownState={countdownState}
                  countdownRemaining={countdownRemaining}
                  viewMode={viewMode}
                />
                  );
                })()
              ) : (
                <div className="empty-state">
                  <h3>🔴 Live Monitoring</h3>
                  <p>Connecting to WebSocket server...</p>
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