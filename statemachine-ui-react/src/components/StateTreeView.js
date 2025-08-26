import React, { useState } from 'react';
import wsLogger from '../wsLogger';

function StateTreeView({ transitions, selectedMachineId, onSelectTransition, selectedTransition, countdownState, countdownRemaining }) {
  // Convert transitions to stateInstances format to work with original tree
  const stateInstances = React.useMemo(() => {
    if (!transitions || !selectedMachineId) return [];
    
    // Group transitions by state lifecycle
    const stateGroups = [];
    const stateInstanceMap = {};
    let currentState = null;
    
    transitions.forEach((transition, idx) => {
      const isEntry = transition.event === 'Start' || transition.event === 'Entry';
      const isTransition = transition.fromState !== transition.toState;
      
      // Check if this transition leads to offline state
      const leadsToOffline = transition.isOffline || false;
      
      if (isEntry) {
        // This is an entry event - create or find the state group for the target state
        const targetState = transition.toState;
        
        // Check if we need to create a new instance
        if (currentState !== targetState) {
          // Entering a new state
          if (!stateInstanceMap[targetState]) {
            stateInstanceMap[targetState] = 0;
          }
          stateInstanceMap[targetState]++;
          
          // Create new state group
          const newGroup = {
            state: targetState,
            instanceNumber: stateInstanceMap[targetState],
            transitions: [],
            isOffline: leadsToOffline
          };
          stateGroups.push(newGroup);
          currentState = targetState;
          
          // Add the entry event
          newGroup.transitions.push({
            ...transition,
            event: 'Entry',
            eventData: transition.payload || {},
            entryActionStatus: transition.entryActionStatus || 'none'
          });
          
          // Mark this group as offline if this transition leads to offline
          if (leadsToOffline) {
            newGroup.isOffline = true;
          }
        }
      } else if (isTransition) {
        // This is a transition event - add it to the FROM state
        const fromGroups = stateGroups.filter(g => g.state === transition.fromState);
        if (fromGroups.length > 0) {
          const fromGroup = fromGroups[fromGroups.length - 1];
          fromGroup.transitions.push({
            ...transition,
            event: transition.event,
            eventData: transition.payload || {},
            entryActionStatus: transition.entryActionStatus || 'none'
          });
        }
        
        // Update current state to the target state
        const previousState = currentState;
        currentState = transition.toState;
        
        // If we're transitioning to a different state, create a new state group for the target
        // This ensures we show the final state after transitions like HANGUP -> IDLE
        if (transition.fromState !== transition.toState && currentState !== previousState) {
          // Check if we need to create a new instance for the target state
          const existingTargetGroups = stateGroups.filter(g => g.state === transition.toState);
          const needsNewInstance = existingTargetGroups.length === 0 || 
                                  existingTargetGroups[existingTargetGroups.length - 1].state !== previousState;
          
          if (needsNewInstance) {
            if (!stateInstanceMap[transition.toState]) {
              stateInstanceMap[transition.toState] = 0;
            }
            stateInstanceMap[transition.toState]++;
            
            // Create new state group for the target state
            const newGroup = {
              state: transition.toState,
              instanceNumber: stateInstanceMap[transition.toState],
              transitions: [],
              isOffline: false
            };
            
            // Add an entry transition to show we arrived at this state
            newGroup.transitions.push({
              stepNumber: transition.stepNumber,
              fromState: transition.fromState,
              toState: transition.toState,
              event: 'Entry',
              timestamp: transition.timestamp,
              duration: transition.duration,
              machineId: transition.machineId,
              eventData: {},
              entryActionStatus: 'none'
            });
            
            stateGroups.push(newGroup);
          }
        }
        
        // If this transition leads to offline, mark the last state group
        if (leadsToOffline && stateGroups.length > 0) {
          const lastGroup = stateGroups[stateGroups.length - 1];
          if (lastGroup.state === transition.toState) {
            lastGroup.isOffline = true;
          }
        }
      } else {
        // This is a stay event (same fromState and toState, not an entry)
        const currentGroups = stateGroups.filter(g => g.state === currentState);
        if (currentGroups.length > 0) {
          const currentGroup = currentGroups[currentGroups.length - 1];
          currentGroup.transitions.push({
            ...transition,
            event: transition.event,
            eventData: transition.payload || {},
            entryActionStatus: transition.entryActionStatus || 'none'
          });
        }
      }
    });
    
    // stateGroups is already an array, just return it (it's already in chronological order)
    return stateGroups;
  }, [transitions, selectedMachineId]);

  // Initialize with all states expanded
  const [expandedStates, setExpandedStates] = useState(new Set());

  // Auto-expand all states when stateInstances changes
  React.useEffect(() => {
    if (stateInstances.length > 0) {
      const allStateKeys = new Set();
      stateInstances.forEach(instance => {
        allStateKeys.add(`${instance.state}-${instance.instanceNumber}`);
      });
      setExpandedStates(allStateKeys);
    } else {
      setExpandedStates(new Set());
    }
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

  // Helper to expand all states
  const expandAll = () => {
    const allStateKeys = new Set();
    stateInstances.forEach(instance => {
      allStateKeys.add(`${instance.state}-${instance.instanceNumber}`);
    });
    setExpandedStates(allStateKeys);
  };

  // Helper to collapse all states
  const collapseAll = () => {
    setExpandedStates(new Set());
  };

  // Get icon for state - Windows folder style
  const getStateIcon = (isExpanded) => {
    return isExpanded ? '📂' : '📁'; // Open/closed folder
  };

  // Get icon for event - Windows file style
  const getEventIcon = (event, eventIgnored) => {
    if (eventIgnored) {
      return '🚫'; // Prohibited/no-entry icon for ignored events
    } else if (event === 'Entry' || event === 'Initial State' || event === 'Initial' || event === 'Start') {
      return '🎯'; // Target/shooting practice icon for Entry
    } else if (event === 'TIMEOUT') {
      return '⏰'; // Clock icon for timeout
    } else {
      return '⚡'; // Flash/thunderbolt icon for all other events
    }
  };

  // Get timeout duration for known states
  const getStateTimeout = (stateName) => {
    const timeouts = {
      'RINGING': '30s',
      'CONNECTED': '30s'
    };
    return timeouts[stateName] || null;
  };

  // Don't render anything if no machine is selected
  if (!selectedMachineId || selectedMachineId === '') {
    return null;
  }

  if (!stateInstances || stateInstances.length === 0) {
    return (
      <div style={{ padding: '20px', color: '#666', textAlign: 'center' }}>
        <p>No transitions recorded for {selectedMachineId}</p>
      </div>
    );
  }

  return (
    <div style={{
      background: 'white',
      borderRadius: '6px',
      padding: '0',
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
      fontSize: '12px',
      fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      position: 'relative'
    }}>
      {/* Expand/Collapse All Buttons */}
      <div style={{
        display: 'flex',
        gap: '8px',
        padding: '10px 10px 8px 10px',
        borderBottom: '1px solid #e9ecef',
        background: 'white',
        borderTopLeftRadius: '6px',
        borderTopRightRadius: '6px'
      }}>
        <button
          onClick={expandAll}
          style={{
            padding: '4px 8px',
            fontSize: '11px',
            border: '1px solid #ced4da',
            borderRadius: '3px',
            background: 'white',
            cursor: 'pointer',
            color: '#495057',
            fontWeight: '500'
          }}
          onMouseEnter={(e) => { e.currentTarget.style.background = '#e9ecef'; }}
          onMouseLeave={(e) => { e.currentTarget.style.background = 'white'; }}
        >
          ⊞ Expand All
        </button>
        <button
          onClick={collapseAll}
          style={{
            padding: '4px 8px',
            fontSize: '11px',
            border: '1px solid #ced4da',
            borderRadius: '3px',
            background: 'white',
            cursor: 'pointer',
            color: '#495057',
            fontWeight: '500'
          }}
          onMouseEnter={(e) => { e.currentTarget.style.background = '#e9ecef'; }}
          onMouseLeave={(e) => { e.currentTarget.style.background = 'white'; }}
        >
          ⊟ Collapse All
        </button>
      </div>

      {/* Tree Content */}
      <div style={{
        flex: 1,
        overflowY: 'auto',
        overflowX: 'hidden',
        padding: '10px'
      }}>
        {stateInstances.map((instance, idx) => {
        const stateKey = `${instance.state}-${instance.instanceNumber}`;
        const isExpanded = expandedStates.has(stateKey);
        
        // Check if this is the latest instance of this state
        const isLatestInstance = !stateInstances.slice(idx + 1).some(
          otherInstance => otherInstance.state === instance.state
        );
        
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
                {/* Show offline indicator */}
                {instance.isOffline && (
                  <span style={{ 
                    marginLeft: '6px',
                    padding: '2px 6px',
                    background: '#e74c3c',
                    color: 'white',
                    borderRadius: '3px',
                    fontSize: '10px',
                    fontWeight: '600',
                    letterSpacing: '0.5px'
                  }}>
                    OFFLINE
                  </span>
                )}
                {/* Show countdown only on the latest instance of this state and NOT offline */}
                {isLatestInstance && !instance.isOffline && countdownState === instance.state && countdownRemaining > 0 && (
                  <span style={{ 
                    marginLeft: '6px',
                    color: countdownRemaining <= 5 ? '#ff6b6b' : '#6c757d',
                    fontSize: '11px',
                    fontWeight: countdownRemaining <= 5 ? '600' : 'normal',
                    animation: countdownRemaining <= 5 ? 'pulse 1s infinite' : 'none',
                    display: 'inline-block',
                    minWidth: '35px',
                    textAlign: 'center'
                  }}>
                    ({countdownRemaining}s)
                  </span>
                )}
                {/* Show static timeout on latest instance if no active countdown and NOT offline */}
                {isLatestInstance && !instance.isOffline && (!countdownState || countdownState !== instance.state) && getStateTimeout(instance.state) && (
                  <span style={{ 
                    marginLeft: '6px',
                    color: '#6c757d',
                    fontSize: '11px',
                    fontWeight: 'normal',
                    opacity: 0.7
                  }}>
                    ({getStateTimeout(instance.state)})
                  </span>
                )}
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
                      onClick={() => onSelectTransition({ ...transition, isOffline: instance.isOffline })}
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
                        {getEventIcon(transition.event, transition.eventIgnored)}
                      </span>
                      <div style={{ flex: 1 }}>
                        {/* Transition Header */}
                        <div style={{ 
                          display: 'flex', 
                          alignItems: 'center',
                          marginBottom: '2px'
                        }}>
                          <span style={{ 
                            color: transition.eventIgnored ? '#6c757d' : '#495057',
                            fontSize: '11px',
                            fontWeight: '600',
                            opacity: transition.eventIgnored ? 0.7 : 1
                          }}>
                            {transition.event}
                          </span>
                          {transition.eventIgnored && (
                            <span style={{ 
                              color: '#dc3545',
                              fontSize: '10px',
                              marginLeft: '6px',
                              fontWeight: '600'
                            }}>
                              (ignored)
                            </span>
                          )}
                          <span style={{ 
                            color: '#6c757d',
                            fontSize: '10px',
                            marginLeft: '6px'
                          }}>
                            {!transition.eventIgnored && 
                             transition.event !== 'Entry' && 
                             transition.event !== 'Start' && 
                             transition.event !== 'Initial' &&
                             transition.fromState !== transition.toState && 
                             `(→ ${transition.toState})`}
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
                          {/* Entry Action Status - only show if there are actual entry actions */}
                          {transition.entryActionStatus && transition.entryActionStatus !== 'none' && (
                            <span style={{
                              fontWeight: '600',
                              color: transition.entryActionStatus === 'executed' ? '#28a745' :
                                     transition.entryActionStatus === 'skipped' ? '#ffc107' :
                                     transition.entryActionStatus === 'failed' ? '#dc3545' : '#6c757d'
                            }}>
                              {transition.entryActionStatus === 'executed' ? '• ✓ Entry Actions' :
                               transition.entryActionStatus === 'skipped' ? '• ⟳ Entry Skipped' :
                               transition.entryActionStatus === 'failed' ? '• ✗ Entry Failed' : ''}
                            </span>
                          )}
                          {transition.fromState && (
                            <span>• from {transition.fromState}</span>
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
    </div>
  );
}

export default StateTreeView;