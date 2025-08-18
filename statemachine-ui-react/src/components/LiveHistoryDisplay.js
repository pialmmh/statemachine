import React, { useState } from 'react';
import StateTreeView from './StateTreeView';
import TransitionDetailPanel from './TransitionDetailPanel';

function LiveHistoryDisplay({ liveHistory, countdownState, countdownRemaining }) {
  const [selectedTransition, setSelectedTransition] = useState(null);

  if (liveHistory.length === 0) {
    return null;
  }

  // Group transitions by states - each re-entry creates a new instance
  const stateInstances = [];
  const stateInstanceMap = new Map(); // Maps state-instanceNumber to instance object
  const stateCounters = new Map(); // Track instance numbers for each state
  const currentStateInstance = new Map(); // Track the current active instance for each state
  
  // Process transitions and events in order
  liveHistory.forEach((transition, index) => {
    let fromInstanceKey = null;
    let toInstanceKey = null;
    
    // For events that don't change state (eventSent flag)
    if (transition.eventSent && transition.fromState === transition.toState) {
      // This is just an event, not a state change
      const state = transition.fromState;
      if (!currentStateInstance.has(state)) {
        // Create first instance if needed
        const instanceNumber = 1;
        stateCounters.set(state, instanceNumber);
        currentStateInstance.set(state, instanceNumber);
        const instanceKey = `${state}-${instanceNumber}`;
        
        const instance = {
          state: state,
          instanceNumber: instanceNumber,
          transitions: []
        };
        stateInstances.push(instance);
        stateInstanceMap.set(instanceKey, instance);
      }
      
      // Add event to current state instance
      const instanceNumber = currentStateInstance.get(state);
      const instanceKey = `${state}-${instanceNumber}`;
      if (stateInstanceMap.has(instanceKey)) {
        stateInstanceMap.get(instanceKey).transitions.push({
          ...transition,
          direction: 'event' // Mark as an event
        });
      }
      return; // Skip the rest for events
    }
    
    // Handle fromState first to ensure it exists
    if (transition.fromState !== 'Initial') {
      // Get or create instance for fromState
      if (!currentStateInstance.has(transition.fromState)) {
        // First time seeing this state as source
        const instanceNumber = 1;
        stateCounters.set(transition.fromState, instanceNumber);
        currentStateInstance.set(transition.fromState, instanceNumber);
        fromInstanceKey = `${transition.fromState}-${instanceNumber}`;
        
        // Create the instance
        const instance = {
          state: transition.fromState,
          instanceNumber: instanceNumber,
          transitions: []
        };
        stateInstances.push(instance);
        stateInstanceMap.set(fromInstanceKey, instance);
      } else {
        // Use current instance for this state
        const instanceNumber = currentStateInstance.get(transition.fromState);
        fromInstanceKey = `${transition.fromState}-${instanceNumber}`;
      }
    }
    
    // Handle toState
    let toInstanceNumber;
    
    // Check if we're re-entering a state that we've left
    if (currentStateInstance.has(transition.toState)) {
      const currentInstance = currentStateInstance.get(transition.toState);
      const currentKey = `${transition.toState}-${currentInstance}`;
      const currentStateInst = stateInstanceMap.get(currentKey);
      
      // Check if this state has any outgoing transitions
      // If it does, we're re-entering and need a new instance
      const hasOutgoing = currentStateInst && currentStateInst.transitions.some(t => t.direction === 'outgoing');
      
      if (hasOutgoing) {
        // Re-entry: create new instance
        toInstanceNumber = (stateCounters.get(transition.toState) || 0) + 1;
        stateCounters.set(transition.toState, toInstanceNumber);
      } else {
        // Same instance - still in this state
        toInstanceNumber = currentInstance;
      }
    } else {
      // First time entering this state
      toInstanceNumber = 1;
      stateCounters.set(transition.toState, 1);
    }
    
    currentStateInstance.set(transition.toState, toInstanceNumber);
    toInstanceKey = `${transition.toState}-${toInstanceNumber}`;
    
    // Create toState instance if it doesn't exist
    if (!stateInstanceMap.has(toInstanceKey)) {
      const instance = {
        state: transition.toState,
        instanceNumber: toInstanceNumber,
        transitions: []
      };
      stateInstances.push(instance);
      stateInstanceMap.set(toInstanceKey, instance);
    }
    
    // Add transitions
    // For state changes, add both outgoing and incoming
    if (transition.stateChange || transition.fromState !== transition.toState) {
      // Add outgoing transition to fromState
      if (fromInstanceKey && stateInstanceMap.has(fromInstanceKey)) {
        stateInstanceMap.get(fromInstanceKey).transitions.push({
          ...transition,
          direction: 'outgoing'
        });
      }
      
      // Add incoming transition to toState
      if (stateInstanceMap.has(toInstanceKey)) {
        stateInstanceMap.get(toInstanceKey).transitions.push({
          ...transition,
          direction: 'incoming'
        });
      }
    } else {
      // This is an event within the same state
      if (toInstanceKey && stateInstanceMap.has(toInstanceKey)) {
        stateInstanceMap.get(toInstanceKey).transitions.push({
          ...transition,
          direction: 'event'
        });
      }
    }
  });

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