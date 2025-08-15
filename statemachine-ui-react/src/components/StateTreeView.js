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

  // Get icon for state - Windows folder style
  const getStateIcon = (isExpanded) => {
    return isExpanded ? '📂' : '📁'; // Open/closed folder
  };

  // Get icon for event - Windows file style
  const getEventIcon = (event) => {
    // Use Windows-style document icons
    if (event === 'Initial State' || event === 'Initial') {
      return '📄'; // Document icon for initial state
    } else if (event.includes('TIMEOUT') || event.includes('Timeout')) {
      return '⏰'; // Clock icon for timeout
    } else if (event.includes('ERROR') || event.includes('FAIL')) {
      return '⚠️'; // Warning icon for errors
    } else if (event.includes('SUCCESS') || event.includes('COMPLETE')) {
      return '✅'; // Checkmark for success
    } else if (event.includes('CANCEL') || event.includes('ABORT')) {
      return '🚫'; // Prohibited sign for cancel
    } else {
      return '📝'; // Generic document icon
    }
  };

  return (
    <div style={{
      background: 'white',
      borderRadius: '6px',
      padding: '10px',
      height: '100%',
      overflowY: 'auto',
      overflowX: 'hidden',
      fontSize: '12px',
      fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      position: 'relative'
    }}>
      {stateInstances.map((instance, idx) => {
        const stateKey = `${instance.state}-${instance.instanceNumber}`;
        const isExpanded = expandedStates.has(stateKey);
        
        return (
          <div key={stateKey} style={{ 
            marginBottom: '10px',
            position: 'relative',
            zIndex: 1
          }}>
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
                background: 'transparent',
                transition: 'background 0.2s',
                position: 'relative',
                zIndex: 2
              }}
              onClick={() => toggleState(stateKey)}
              onMouseEnter={(e) => { e.currentTarget.style.background = '#f0f0f0'; }}
              onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent'; }}
            >
              <span style={{ 
                marginRight: '6px', 
                fontSize: '11px', 
                fontFamily: 'monospace',
                display: 'inline-block',
                width: '13px',
                height: '13px',
                lineHeight: '11px',
                border: '1px solid #8b8b8b',
                background: 'white',
                textAlign: 'center',
                verticalAlign: 'middle'
              }}>
                {isExpanded ? '−' : '+'}
              </span>
              <span style={{ marginRight: '6px', fontSize: '14px' }}>
                {getStateIcon(isExpanded)}
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
              <div style={{ 
                marginLeft: '19px', 
                marginTop: '2px',
                borderLeft: '1px dotted #c0c0c0',
                paddingLeft: '8px'
              }}>
                {instance.transitions.map((transition, tIdx) => (
                  <div key={transition.stepNumber}>
                    {/* Transition Node */}
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        padding: '4px 8px',
                        marginBottom: '2px',
                        cursor: 'pointer',
                        borderRadius: '3px',
                        border: selectedTransition === transition ? '1px solid #0078d4' : 'none',
                        background: selectedTransition === transition ? '#e5f1fb' : 'transparent',
                        transition: 'all 0.15s',
                        position: 'relative'
                      }}
                      onClick={() => onSelectTransition(transition)}
                      onMouseEnter={(e) => {
                        if (selectedTransition !== transition) {
                          e.currentTarget.style.background = '#f0f2f5';
                        }
                      }}
                      onMouseLeave={(e) => {
                        if (selectedTransition !== transition) {
                          e.currentTarget.style.background = 'transparent';
                        }
                      }}
                    >
                      <span style={{ 
                        position: 'absolute',
                        left: '-9px',
                        width: '9px',
                        height: '1px',
                        borderTop: '1px dotted #c0c0c0',
                        top: '50%'
                      }}></span>
                      <span style={{ marginRight: '6px', fontSize: '14px' }}>
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