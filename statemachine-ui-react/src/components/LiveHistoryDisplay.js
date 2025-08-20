import React, { useState } from 'react';
import StateTreeView from './StateTreeView';
import TransitionDetailPanel from './TransitionDetailPanel';
import wsLogger from '../wsLogger';

function LiveHistoryDisplay({ liveHistory, countdownState, countdownRemaining }) {
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
    `${inst.state}-${inst.instanceNumber}: ${inst.transitions.length} transitions`
  ));

  return (
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
        <div style={{ 
          padding: '10px', 
          background: 'white',
          color: '#495057',
          borderRadius: '6px',
          marginBottom: '10px',
          fontSize: '14px',
          fontWeight: '600',
          borderBottom: '1px solid #dee2e6'
        }}>
          State Transition Tree
          {countdownState && countdownRemaining > 0 && (
            <span style={{ 
              float: 'right',
              background: '#f8f9fa', 
              padding: '2px 8px', 
              borderRadius: '12px', 
              fontSize: '12px',
              color: '#dc3545'
            }}>
              ⏱️ {countdownRemaining}s
            </span>
          )}
        </div>
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
  );
}

export default LiveHistoryDisplay;