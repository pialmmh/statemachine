import React, { useState } from 'react';

function StateTreeView({ stateInstances, onSelectTransition, selectedTransition }) {
  // Initialize with all states expanded
  const [expandedStates, setExpandedStates] = useState(() => {
    const initial = new Set();
    stateInstances.forEach(instance => {
      initial.add(`${instance.state}-${instance.instanceNumber}`);
    });
    return initial;
  });

  const toggleState = (stateKey) => {
    const newExpanded = new Set(expandedStates);
    if (newExpanded.has(stateKey)) {
      newExpanded.delete(stateKey);
    } else {
      newExpanded.add(stateKey);
    }
    setExpandedStates(newExpanded);
  };

  // Get icon for state - generic icons
  const getStateIcon = (state) => {
    // Use generic state machine icons
    if (state === 'IDLE' || state === 'INITIAL' || state === 'START') {
      return '⭕'; // Initial/idle state
    } else if (state.includes('END') || state.includes('FINAL') || state.includes('COMPLETE')) {
      return '🏁'; // End state
    } else if (state.includes('ERROR') || state.includes('FAIL')) {
      return '⚠️'; // Error state
    } else if (state.includes('WAIT') || state.includes('PENDING')) {
      return '⏸️'; // Waiting state
    } else if (state.includes('PROCESS') || state.includes('RUNNING')) {
      return '▶️'; // Active/processing state
    } else {
      return '◉'; // Generic state
    }
  };

  // Get icon for event - generic icons
  const getEventIcon = (event) => {
    // Use generic event icons
    if (event === 'Initial State' || event === 'Initial') {
      return '🎯'; // Initial/start event
    } else if (event.includes('TIMEOUT') || event.includes('Timeout')) {
      return '⏱️'; // Timeout event
    } else if (event.includes('ERROR') || event.includes('FAIL')) {
      return '❗'; // Error event
    } else if (event.includes('SUCCESS') || event.includes('COMPLETE')) {
      return '✓'; // Success event
    } else if (event.includes('CANCEL') || event.includes('ABORT')) {
      return '✕'; // Cancel event
    } else {
      return '→'; // Generic transition
    }
  };

  return (
    <div style={{
      background: 'white',
      borderRadius: '6px',
      padding: '10px',
      height: '100%',
      overflowY: 'auto',
      fontSize: '12px',
      fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
    }}>
      {stateInstances.map((instance, idx) => {
        const stateKey = `${instance.state}-${instance.instanceNumber}`;
        const isExpanded = expandedStates.has(stateKey);
        
        return (
          <div key={stateKey} style={{ marginBottom: '10px' }}>
            {/* State Header */}
            <div 
              style={{ 
                display: 'flex',
                alignItems: 'center',
                padding: '6px 4px',
                cursor: 'pointer',
                fontWeight: '600',
                color: '#2c3e50',
                borderRadius: '4px',
                transition: 'background 0.2s',
                ':hover': { background: '#f0f0f0' }
              }}
              onClick={() => toggleState(stateKey)}
              onMouseEnter={(e) => e.currentTarget.style.background = '#f0f0f0'}
              onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
            >
              <span style={{ marginRight: '6px', fontSize: '10px', opacity: 0.6 }}>
                {isExpanded ? '▼' : '▶'}
              </span>
              <span style={{ marginRight: '6px', fontSize: '14px' }}>
                {getStateIcon(instance.state)}
              </span>
              <span style={{ flex: 1 }}>
                {instance.state}
              </span>
              {instance.instanceNumber > 1 && (
                <span style={{ 
                  background: '#e9ecef',
                  color: '#495057',
                  padding: '1px 5px',
                  borderRadius: '10px',
                  fontSize: '10px',
                  fontWeight: 'normal'
                }}>
                  #{instance.instanceNumber}
                </span>
              )}
            </div>

            {/* Transitions */}
            {isExpanded && (
              <div style={{ marginLeft: '20px', marginTop: '5px' }}>
                {instance.transitions.map((transition) => (
                  <div key={transition.stepNumber}>
                    {/* Transition Node */}
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        padding: '6px 8px',
                        marginBottom: '4px',
                        cursor: 'pointer',
                        borderRadius: '4px',
                        border: selectedTransition === transition ? '2px solid #667eea' : '1px solid #e9ecef',
                        background: selectedTransition === transition ? '#f8f9ff' : '#fafbfc',
                        transition: 'all 0.2s'
                      }}
                      onClick={() => onSelectTransition(transition)}
                      onMouseEnter={(e) => {
                        if (selectedTransition !== transition) {
                          e.currentTarget.style.background = '#f0f2f5';
                        }
                      }}
                      onMouseLeave={(e) => {
                        if (selectedTransition !== transition) {
                          e.currentTarget.style.background = '#fafbfc';
                        }
                      }}
                    >
                      <span style={{ marginRight: '8px', fontSize: '14px' }}>
                        {getEventIcon(transition.event)}
                      </span>
                      <div style={{ flex: 1 }}>
                        {/* Transition Header */}
                        <div style={{ 
                          display: 'flex', 
                          alignItems: 'center',
                          marginBottom: '2px'
                        }}>
                          <span style={{ 
                            color: '#495057',
                            fontSize: '11px',
                            fontWeight: '600'
                          }}>
                            {transition.event}
                          </span>
                          <span style={{ 
                            color: '#6c757d',
                            fontSize: '10px',
                            marginLeft: '6px'
                          }}>
                            {transition.fromState !== transition.toState && `(→ ${transition.toState})`}
                          </span>
                        </div>

                        {/* Compact info */}
                        <div style={{ 
                          fontSize: '10px',
                          color: '#868e96',
                          display: 'flex',
                          gap: '8px'
                        }}>
                          <span>Step #{transition.stepNumber}</span>
                          {transition.eventData && Object.keys(transition.eventData).length > 0 && (
                            <span>• 📦 Has payload</span>
                          )}
                        </div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}

export default StateTreeView;