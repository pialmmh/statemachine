import React, { useState, useEffect } from 'react';

function StateTreeView({ stateInstances, onSelectTransition, selectedTransition }) {
  // Simply keep all states expanded all the time
  const getAllStateKeys = () => {
    const keys = new Set();
    stateInstances.forEach(instance => {
      keys.add(`${instance.state}-${instance.instanceNumber}`);
    });
    return keys;
  };
  
  const [expandedStates, setExpandedStates] = useState(getAllStateKeys);
  
  // Update expanded states whenever stateInstances changes
  useEffect(() => {
    setExpandedStates(getAllStateKeys());
  }, [stateInstances]);

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

  // Get icon for event or transition
  const getEventIcon = (transition) => {
    const event = transition.event;
    
    // For incoming transitions that are timeouts, treat them as regular state changes
    if (transition.direction === 'incoming' && event === 'Timeout') {
      return '⚙️'; // Regular state change icon
    }
    
    // Check for timeout events specifically (outgoing or events)
    if (event && (event.toLowerCase().includes('timeout') || event === 'Timeout')) {
      return '⏰'; // Clock icon for timeout events
    } else if (transition.eventSent || transition.direction === 'event') {
      // This is an event that was sent
      return '⚡'; // Lightning bolt for other events
    } else if (transition.stateChange || transition.fromState !== transition.toState) {
      // This is a state transition
      return '⚙️'; // Gear icon for state transitions
    } else if (event === 'Initial State' || event === 'Initial') {
      return '◉'; // Circle/dot for initial state
    } else {
      return '•'; // Bullet point for other items
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
                fontFamily: '"Inter", "SF Pro Text", "Segoe UI", sans-serif',
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
                      <span style={{ 
                        marginRight: '6px', 
                        fontSize: '14px',
                        color: (transition.eventSent || transition.direction === 'event') ? '#dc3545' : 'inherit',
                        filter: (transition.eventSent || transition.direction === 'event') && !transition.event?.toLowerCase().includes('timeout') ? 'hue-rotate(-55deg) saturate(5) brightness(0.9)' : 'none',
                        display: 'inline-block'
                      }}>
                        {getEventIcon(transition)}
                      </span>
                      <div style={{ flex: 1 }}>
                        {/* Transition Header */}
                        <div style={{ 
                          display: 'flex', 
                          alignItems: 'center',
                          marginBottom: '2px'
                        }}>
                          <span style={{ 
                            color: transition.eventSent ? '#9b59b6' : '#495057', // Purple for events, gray for state changes
                            fontSize: '11px',
                            fontWeight: '600'
                          }}>
                            {/* Show different label based on direction */}
                            {transition.direction === 'incoming' && transition.event === 'Timeout' ? 'State Change' : transition.event}
                          </span>
                          <span style={{ 
                            color: '#6c757d',
                            fontSize: '10px',
                            marginLeft: '6px'
                          }}>
                            {transition.direction === 'event' ? '(event)' : 
                             transition.fromState !== transition.toState ? `(→ ${transition.toState})` : 
                             transition.direction === 'incoming' ? `(← ${transition.fromState})` : ''}
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
                          {/* Entry Action Status */}
                          {transition.entryActionStatus && (
                            <span style={{
                              fontWeight: '600',
                              color: transition.entryActionStatus === 'executed' ? '#28a745' :
                                     transition.entryActionStatus === 'skipped' ? '#ffc107' :
                                     transition.entryActionStatus === 'failed' ? '#dc3545' :
                                     transition.entryActionStatus === 'none' ? '#6c757d' : '#6c757d'
                            }}>
                              {transition.entryActionStatus === 'executed' ? '• ✓ Entry Actions' :
                               transition.entryActionStatus === 'skipped' ? '• ⟳ Entry Skipped' :
                               transition.entryActionStatus === 'failed' ? '• ✗ Entry Failed' :
                               transition.entryActionStatus === 'none' ? '• ○ No Entry Actions' : ''}
                            </span>
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