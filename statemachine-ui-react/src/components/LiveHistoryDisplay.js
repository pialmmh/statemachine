import React, { useState } from 'react';
import StateTreeView from './StateTreeView';
import TransitionDetailPanel from './TransitionDetailPanel';
import wsLogger from '../wsLogger';

function LiveHistoryDisplay({ liveHistory, countdownState, countdownRemaining, viewMode = 'tree' }) {
  const [selectedTransition, setSelectedTransition] = useState(null);

  wsLogger.render('LiveHistoryDisplay', '=== LiveHistoryDisplay RENDER ===');
  wsLogger.render('LiveHistoryDisplay', '  Received', liveHistory.length, 'items');

  if (liveHistory.length === 0) {
    wsLogger.render('LiveHistoryDisplay', 'LiveHistoryDisplay: No history entries, returning null');
    return null;
  }
  
  // Backend now sends pre-grouped history with state instances
  // Each item in liveHistory is a state instance with:
  // - state: the state name
  // - instanceNumber: which instance of this state (for re-entries)  
  // - transitions: array of events/transitions in this state instance
  
  let stateInstances;
  
  // Check if this is the new grouped format or old raw format
  if (liveHistory.length > 0 && liveHistory[0].state && liveHistory[0].transitions) {
    // New grouped format from backend - use as-is
    stateInstances = liveHistory;
    wsLogger.render('LiveHistoryDisplay', 'Using grouped history from backend:', stateInstances.length, 'state instances');
    console.log('GROUPED FORMAT DETECTED:', JSON.stringify(liveHistory, null, 2));
  } else {
    // Old raw format - convert to grouped format for compatibility
    wsLogger.render('LiveHistoryDisplay', 'Converting raw history to grouped format');
    
    // Simple conversion: each transition becomes its own state instance
    const instanceMap = new Map();
    const instanceCounters = new Map();
    
    liveHistory.forEach(transition => {
      const state = transition.state || transition.fromState || 'UNKNOWN';
      
      // Get or create counter for this state
      if (!instanceCounters.has(state)) {
        instanceCounters.set(state, 1);
      }
      
      // Check if we need a new instance (re-entry)
      const isReEntry = transition.event === 'ENTRY' && instanceMap.has(state);
      if (isReEntry) {
        instanceCounters.set(state, instanceCounters.get(state) + 1);
      }
      
      const instanceNum = instanceCounters.get(state);
      const instanceKey = `${state}-${instanceNum}`;
      
      // Get or create instance
      if (!instanceMap.has(instanceKey)) {
        instanceMap.set(instanceKey, {
          state: state,
          instanceNumber: instanceNum,
          transitions: []
        });
      }
      
      // Add transition to instance
      instanceMap.get(instanceKey).transitions.push({
        ...transition,
        direction: transition.transitionOrStay ? 'outgoing' : 
                   transition.event === 'ENTRY' ? 'entry' : 'event'
      });
    });
    
    stateInstances = Array.from(instanceMap.values());
  }
  
  wsLogger.render('LiveHistoryDisplay', '=== Final state instances:', stateInstances.map(inst => 
    `${inst.state}-${inst.instanceNumber}: ${inst.transitions ? inst.transitions.length : 0} transitions`
  ));

  // Flatten all transitions for event viewer
  const getAllEvents = () => {
    const events = [];
    stateInstances.forEach(instance => {
      if (instance.transitions && Array.isArray(instance.transitions)) {
        instance.transitions.forEach(transition => {
          events.push({
            ...transition,
            stateName: instance.state,
            instanceNumber: instance.instanceNumber
          });
        });
      }
    });
    // Sort by datetime first, then by special event ordering
    return events.sort((a, b) => {
      // First try to sort by datetime if available
      if (a.datetime && b.datetime) {
        const dateA = new Date(a.datetime).getTime();
        const dateB = new Date(b.datetime).getTime();
        if (dateA !== dateB) {
          return dateA - dateB;
        }
      }
      
      // If timestamps are equal, we need special ordering:
      // 1. Event that causes transition (e.g., INCOMING_CALL, TIMEOUT)
      // 2. TRANSITION event (synthetic)
      // 3. ENTRY event to the new state
      
      const eventA = a.event || '';
      const eventB = b.event || '';
      
      // Helper to get event priority (lower number = earlier in sequence)
      const getEventPriority = (event) => {
        if (event === 'TRANSITION') return 2; // Middle
        if (event === 'ENTRY') return 3; // Last
        return 1; // Everything else (INCOMING_CALL, TIMEOUT, etc.) comes first
      };
      
      const priorityA = getEventPriority(eventA);
      const priorityB = getEventPriority(eventB);
      
      if (priorityA !== priorityB) {
        return priorityA - priorityB;
      }
      
      // If same priority, fall back to ID
      return (a.id || 0) - (b.id || 0);
    });
  };

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      {/* Conditional View Rendering */}
      {viewMode === 'tree' ? (
        <div style={{ 
          display: 'grid', 
          gridTemplateColumns: '280px 1fr', 
          gap: '20px',
          height: '100%',
          minHeight: '600px'
        }}>
          {/* Left: Tree View */}
          <div style={{ 
            background: '#f8f9fa',
            borderRadius: '8px',
            padding: '10px',
            overflowY: 'auto',
            boxShadow: '0 2px 4px rgba(0,0,0,0.05)'
          }}>
            <StateTreeView 
              stateInstances={stateInstances}
              onSelectTransition={setSelectedTransition}
              selectedTransition={selectedTransition}
            />
          </div>

          {/* Right: Detail Panel */}
          <div style={{ 
            background: '#ffffff',
            borderRadius: '8px',
            boxShadow: '0 2px 8px rgba(0,0,0,0.08)',
            overflow: 'hidden'
          }}>
            <TransitionDetailPanel 
              transition={selectedTransition}
              countdownState={countdownState}
              countdownRemaining={countdownRemaining}
            />
          </div>
        </div>
      ) : (
        /* Event Viewer Table */
        <div style={{
          background: 'white',
          borderRadius: '8px',
          padding: '10px 20px 20px 20px',
          height: '100%',
          overflowY: 'auto',
          boxShadow: '0 2px 8px rgba(0,0,0,0.08)'
        }}>
          <table style={{
            width: '100%',
            borderCollapse: 'collapse',
            fontSize: '12px',
            fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
          }}>
            <thead>
              <tr style={{ borderBottom: '2px solid #dee2e6' }}>
                <th style={{ padding: '8px 8px', textAlign: 'left', fontWeight: '600', color: '#495057' }}>ID</th>
                <th style={{ padding: '8px 8px', textAlign: 'left', fontWeight: '600', color: '#495057' }}>State</th>
                <th style={{ padding: '8px 8px', textAlign: 'left', fontWeight: '600', color: '#495057' }}>Event</th>
                <th style={{ padding: '8px 8px', textAlign: 'left', fontWeight: '600', color: '#495057' }}>Transition</th>
                <th style={{ padding: '8px 8px', textAlign: 'left', fontWeight: '600', color: '#495057' }}>Timestamp</th>
                <th style={{ padding: '8px 8px', textAlign: 'left', fontWeight: '600', color: '#495057' }}>Payload</th>
              </tr>
            </thead>
            <tbody>
              {getAllEvents().map((event, idx) => (
                <tr key={event.id || idx} style={{ 
                  borderBottom: '1px solid #e9ecef',
                  background: idx % 2 === 0 ? 'white' : '#f8f9fa'
                }}>
                  <td style={{ padding: '10px 8px', color: '#6c757d', textAlign: 'left' }}>{event.id || idx + 1}</td>
                  <td style={{ padding: '10px 8px', fontWeight: '500', textAlign: 'left' }}>
                    {event.stateName}
                    {event.instanceNumber > 1 && (
                      <span style={{ 
                        marginLeft: '5px',
                        background: '#e9ecef',
                        padding: '1px 5px',
                        borderRadius: '10px',
                        fontSize: '10px'
                      }}>
                        #{event.instanceNumber}
                      </span>
                    )}
                  </td>
                  <td style={{ padding: '10px 8px', textAlign: 'left' }}>
                    <span style={{ 
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '5px'
                    }}>
                      {event.event === 'ENTRY' && '🎯'}
                      {event.event === 'TRANSITION' && '🔀'}
                      {event.event === 'TIMEOUT' && '⏰'}
                      {event.event && !['ENTRY', 'TRANSITION', 'TIMEOUT'].includes(event.event) && '⚡'}
                      <span style={{ color: event.eventIgnored ? '#dc3545' : '#212529' }}>
                        {event.event}
                      </span>
                    </span>
                  </td>
                  <td style={{ padding: '10px 8px', textAlign: 'left' }}>
                    {event.transitionOrStay && event.transitionToState && (
                      <span style={{ color: '#28a745', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                        <span style={{ fontSize: '12px' }}>🔀</span>
                        {event.transitionToState}
                      </span>
                    )}
                  </td>
                  <td style={{ padding: '10px 8px', color: '#6c757d', fontSize: '11px', textAlign: 'left' }}>
                    {event.datetime ? new Date(event.datetime).toLocaleTimeString() : ''}
                  </td>
                  <td style={{ padding: '10px 8px', textAlign: 'left' }}>
                    {event.eventPayload && (
                      <span style={{ 
                        background: '#e9ecef',
                        padding: '2px 6px',
                        borderRadius: '4px',
                        fontSize: '11px',
                        color: '#495057'
                      }}>
                        {typeof event.eventPayload === 'object' 
                          ? Object.keys(event.eventPayload).join(', ')
                          : '✓'}
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export default LiveHistoryDisplay;