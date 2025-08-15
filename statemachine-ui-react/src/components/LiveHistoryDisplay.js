import React, { useState } from 'react';
import StateTreeView from './StateTreeView';
import TransitionDetailPanel from './TransitionDetailPanel';

function LiveHistoryDisplay({ liveHistory, countdownState, countdownRemaining }) {
  const [selectedTransition, setSelectedTransition] = useState(null);

  if (liveHistory.length === 0) {
    return null;
  }

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
          background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
          color: 'white',
          borderRadius: '6px',
          marginBottom: '10px',
          fontSize: '14px',
          fontWeight: '600'
        }}>
          🌳 State Transition Tree
          {countdownState && countdownRemaining > 0 && (
            <span style={{ 
              float: 'right',
              background: 'rgba(255,255,255,0.2)', 
              padding: '2px 8px', 
              borderRadius: '12px', 
              fontSize: '12px'
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