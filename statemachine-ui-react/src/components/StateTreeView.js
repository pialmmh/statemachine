import React, { useState, useEffect } from 'react';

function StateTreeView({ stateInstances, onSelectTransition, selectedTransition }) {
  console.log('StateTreeView received stateInstances:', stateInstances);
  stateInstances.forEach(inst => {
    console.log(`  State ${inst.state}-${inst.instanceNumber}: ${inst.transitions.length} transitions`);
    inst.transitions.forEach((t, i) => {
      console.log(`    Transition ${i}: event="${t.event}", direction="${t.direction}", keys=${Object.keys(t).join(',')}`);
    });
  });
  
  // Simply keep all states expanded all the time
  const getAllStateKeys = () => {
    const keys = new Set();
    stateInstances.forEach(instance => {
      keys.add(`${instance.state}-${instance.instanceNumber}`);
    });
    console.log('getAllStateKeys returning:', Array.from(keys));
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

  const expandAll = () => {
    setExpandedStates(getAllStateKeys());
  };

  const collapseAll = () => {
    setExpandedStates(new Set());
  };

  // Get icon for state - Windows folder style
  const getStateIcon = (isExpanded) => {
    return isExpanded ? '📂' : '📁'; // Open/closed folder
  };

  // Get icon for event or transition
  const getEventIcon = (transition) => {
    const event = transition.event;
    
    // Check for transition events
    if (event && event.toUpperCase() === 'TRANSITION') {
      return '🔀'; // Twisted arrows for explicit state transitions
    }
    
    // Check for timeout events
    if (event && (event.toUpperCase() === 'TIMEOUT' || event.toLowerCase().includes('timeout'))) {
      return '⏰'; // Clock icon for timeout events
    }
    
    // Check for entry events (including BEFORE/AFTER entry actions)
    if (event && (event.toUpperCase() === 'ENTRY' || event.toUpperCase().includes('ENTRY'))) {
      return '🎯'; // Target icon for entry events
    }
    
    // For any other event (INCOMING_CALL, ANSWER, HANGUP, REJECT, etc.), use lightning bolt
    // This includes events that cause transitions or stay in the same state
    if (event && event.toUpperCase() !== 'ENTRY' && !event.toLowerCase().includes('timeout')) {
      return '⚡'; // Lightning bolt for all other events
    }
    
    // Check for direction-based icons (for compatibility with old format)
    if (transition.eventSent || transition.direction === 'event') {
      return '⚡'; // Lightning bolt for events
    }
    
    if (transition.stateChange || (transition.fromState && transition.fromState !== transition.toState)) {
      return '⚙️'; // Gear icon for state transitions
    }
    
    // Default
    return '•'; // Bullet point for other items
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
      {/* Expand/Collapse buttons */}
      <div style={{
        position: 'sticky',
        top: 0,
        background: 'white',
        borderBottom: '1px solid #e9ecef',
        marginBottom: '10px',
        paddingBottom: '8px',
        marginTop: '-5px',
        zIndex: 10,
        display: 'flex',
        gap: '8px'
      }}>
        <button
          onClick={expandAll}
          style={{
            padding: '4px 8px',
            fontSize: '11px',
            background: '#f8f9fa',
            border: '1px solid #dee2e6',
            borderRadius: '4px',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: '4px',
            color: '#495057'
          }}
          onMouseOver={(e) => e.target.style.background = '#e9ecef'}
          onMouseOut={(e) => e.target.style.background = '#f8f9fa'}
          title="Expand All"
        >
          <span style={{ fontSize: '12px' }}>▼</span> Expand All
        </button>
        <button
          onClick={collapseAll}
          style={{
            padding: '4px 8px',
            fontSize: '11px',
            background: '#f8f9fa',
            border: '1px solid #dee2e6',
            borderRadius: '4px',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: '4px',
            color: '#495057'
          }}
          onMouseOver={(e) => e.target.style.background = '#e9ecef'}
          onMouseOut={(e) => e.target.style.background = '#f8f9fa'}
          title="Collapse All"
        >
          <span style={{ fontSize: '12px' }}>▶</span> Collapse All
        </button>
      </div>
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
                  <div key={transition.id || transition.stepNumber || tIdx}>
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
                        fontSize: getEventIcon(transition) === '🔀' ? '12px' : '14px',
                        color: (() => {
                          const icon = getEventIcon(transition);
                          // Make lightning bolt reddish
                          if (icon === '⚡') return '#dc3545';
                          // Keep timeout yellow/orange
                          if (icon === '⏰') return '#fd7e14';
                          // Keep entry target blue
                          if (icon === '🎯') return '#0066cc';
                          // Keep transition twisted arrows purple
                          if (icon === '🔀') return '#8b5cf6';
                          return 'inherit';
                        })(),
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
                            color: transition.isEntryStep ? '#28a745' : 
                                   transition.eventSent ? '#9b59b6' : '#495057', // Green for entry, Purple for events, gray for state changes
                            fontSize: '11px',
                            fontWeight: '600'
                          }}>
                            {/* Show event name */}
                            {transition.event === 'TRANSITION' ? 
                              `Transition → ${transition.transitionToState}` : 
                              (transition.event || 'Unknown')}
                          </span>
                          <span style={{ 
                            color: '#6c757d',
                            fontSize: '10px',
                            marginLeft: '6px'
                          }}>
                            {transition.event === 'TRANSITION' ? '' :
                             transition.direction === 'event' ? '(event)' : 
                             transition.direction === 'entry' ? '' :
                             transition.fromState !== transition.toState ? `(→ ${transition.toState})` : ''}
                          </span>
                        </div>

                        {/* Compact info */}
                        <div style={{ 
                          fontSize: '10px',
                          color: '#868e96',
                          display: 'flex',
                          gap: '8px'
                        }}>
                          <span>Step #{transition.id || transition.stepNumber || tIdx + 1}</span>
                          {((transition.eventData && Object.keys(transition.eventData).length > 0) || 
                            (transition.eventPayload && typeof transition.eventPayload === 'object' && Object.keys(transition.eventPayload).length > 0)) && (
                            <span>• 📦 Has payload</span>
                          )}
                          {/* Entry Action Status - show only for entry steps */}
                          {(transition.event === 'ENTRY' || (transition.isEntryStep && transition.event === 'Entry')) && (
                            <span style={{
                              fontWeight: '600',
                              color: '#6c757d'
                            }}>
                              • ○ No Entry Actions
                            </span>
                          )}
                          {/* Show context info for Before/After Entry Actions */}
                          {(transition.event === 'Before Entry Actions' || transition.event === 'After Entry Actions') && (
                            <span style={{
                              fontWeight: '600',
                              color: '#28a745'
                            }}>
                              • ✓ Context {transition.event === 'Before Entry Actions' ? 'before' : 'after'} actions
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