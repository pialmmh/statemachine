import React from 'react';

function TransitionDetailPanel({ transition, countdownState, countdownRemaining }) {
  if (!transition) {
    return (
      <div style={{
        background: 'white',
        borderRadius: '8px',
        padding: '20px',
        height: '100%',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        color: '#6c757d',
        fontSize: '14px'
      }}>
        <div style={{ textAlign: 'center' }}>
          <div style={{ fontSize: '48px', marginBottom: '15px', opacity: 0.3 }}>📋</div>
          <p>Select a transition from the tree to view details</p>
        </div>
      </div>
    );
  }

  const eventBgColor = transition.event === 'Initial State' ? '#e8f5e9' : '#d1ecf1';
  const eventColor = transition.event === 'Initial State' ? '#4caf50' : '#17a2b8';

  return (
    <div style={{
      background: 'white',
      borderRadius: '8px',
      height: '100%',
      overflowY: 'auto',
      display: 'flex',
      flexDirection: 'column'
    }}>
      {/* Header */}
      <div style={{
        background: eventBgColor,
        padding: '15px 20px',
        borderBottom: '2px solid #dee2e6',
        borderTopLeftRadius: '8px',
        borderTopRightRadius: '8px'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
          <span style={{
            background: eventColor,
            color: 'white',
            padding: '5px 12px',
            borderRadius: '4px',
            fontSize: '14px',
            fontWeight: '600'
          }}>
            Step {transition.stepNumber}: {transition.event}
          </span>
          <span style={{ fontSize: '14px', color: '#495057' }}>
            {transition.fromState === 'Initial'
              ? `Initial State: ${transition.toState}`
              : `${transition.fromState} → ${transition.toState}`}
          </span>
          <span style={{ marginLeft: 'auto', fontSize: '12px', color: '#6c757d' }}>
            {transition.timestamp}
          </span>
          {/* Countdown Timer */}
          {countdownState === transition.toState && countdownRemaining > 0 && (
            <div style={{ 
              background: 'rgba(102, 126, 234, 0.1)', 
              padding: '4px 10px', 
              borderRadius: '15px', 
              fontSize: '13px',
              fontWeight: '600',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              color: eventColor,
              border: `1px solid ${eventColor}`
            }}>
              <span>⏱️</span>
              <span>{countdownRemaining}s remaining</span>
            </div>
          )}
        </div>
      </div>

      {/* Content */}
      <div style={{ flex: 1, padding: '20px' }}>
        {/* Event Payload */}
        <div style={{ marginBottom: '25px' }}>
          <h3 style={{ 
            fontSize: '14px', 
            fontWeight: '600', 
            color: '#28a745',
            marginBottom: '10px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px'
          }}>
            📌 Event Payload
          </h3>
          <pre style={{
            background: '#f8f9fa',
            border: '1px solid #dee2e6',
            borderRadius: '6px',
            padding: '12px',
            fontSize: '12px',
            fontFamily: 'Consolas, monospace',
            overflowX: 'auto',
            margin: 0
          }}>
            {JSON.stringify(transition.eventData || {}, null, 2)}
          </pre>
        </div>

        {/* Registry Status Comparison */}
        <div style={{ 
          display: 'grid', 
          gridTemplateColumns: '1fr 1fr', 
          gap: '20px',
          marginBottom: '25px'
        }}>
          {/* Registry Status Before */}
          <div>
            <h3 style={{ 
              fontSize: '14px', 
              fontWeight: '600', 
              color: '#dc3545',
              marginBottom: '10px',
              display: 'flex',
              alignItems: 'center',
              gap: '8px'
            }}>
              🏛️ Registry Status Before
            </h3>
            <div style={{
              background: '#fff5f5',
              border: '1px solid #f5c6cb',
              borderRadius: '6px',
              padding: '12px',
              fontSize: '12px',
              fontFamily: 'monospace',
              lineHeight: '1.5'
            }}>
              Status: {(transition.contextBefore?.registryStatus?.status || 'ACTIVE') === 'ACTIVE' ? '✅ ACTIVE' : `❌ ${transition.contextBefore?.registryStatus?.status}`} | 
              Hydrated: {transition.contextBefore?.registryStatus?.hydrated ? '✅ Yes' : '🔄 No'} | 
              Online: {transition.contextBefore?.registryStatus?.online !== false ? '🟢 Yes' : '🔴 No'}
            </div>
          </div>

          {/* Registry Status After */}
          <div>
            <h3 style={{ 
              fontSize: '14px', 
              fontWeight: '600', 
              color: '#17a2b8',
              marginBottom: '10px',
              display: 'flex',
              alignItems: 'center',
              gap: '8px'
            }}>
              🏛️ Registry Status After
            </h3>
            <div style={{
              background: '#f0f8ff',
              border: '1px solid #bee5eb',
              borderRadius: '6px',
              padding: '12px',
              fontSize: '12px',
              fontFamily: 'monospace',
              lineHeight: '1.5'
            }}>
              Status: {(transition.contextAfter?.registryStatus?.status || 'ACTIVE') === 'ACTIVE' ? '✅ ACTIVE' : `❌ ${transition.contextAfter?.registryStatus?.status}`} | 
              Hydrated: {transition.contextAfter?.registryStatus?.hydrated ? '✅ Yes' : '🔄 No'} | 
              Online: {transition.contextAfter?.registryStatus?.online !== false ? '🟢 Yes' : '🔴 No'}
            </div>
          </div>
        </div>

        {/* Context Comparison */}
        <div style={{ 
          display: 'grid', 
          gridTemplateColumns: '1fr 1fr', 
          gap: '20px' 
        }}>
          {/* Context Before */}
          <div>
            <h3 style={{ 
              fontSize: '14px', 
              fontWeight: '600', 
              color: '#dc3545',
              marginBottom: '10px'
            }}>
              🔴 Context Before
            </h3>
            
            <div style={{
              background: '#fff5f5',
              border: '1px solid #f5c6cb',
              borderRadius: '6px',
              padding: '15px'
            }}>
              {/* Persistent Context */}
              <div style={{ marginBottom: '15px' }}>
                <h4 style={{ fontSize: '12px', fontWeight: '600', marginBottom: '8px', color: '#721c24' }}>
                  Persistent Context
                </h4>
                <pre style={{
                  background: 'white',
                  border: '1px solid #f5c6cb',
                  borderRadius: '4px',
                  padding: '8px',
                  fontSize: '10px',
                  fontFamily: 'Consolas, monospace',
                  overflowX: 'auto',
                  margin: 0,
                  maxHeight: '300px',
                  overflowY: 'auto'
                }}>
                  {JSON.stringify(transition.contextBefore?.persistentContext || {}, null, 2)}
                </pre>
              </div>

              {/* Volatile Context */}
              <div>
                <h4 style={{ fontSize: '12px', fontWeight: '600', marginBottom: '8px', color: '#721c24' }}>
                  Volatile Context
                </h4>
                <pre style={{
                  background: 'white',
                  border: '1px solid #f5c6cb',
                  borderRadius: '4px',
                  padding: '8px',
                  fontSize: '10px',
                  fontFamily: 'Consolas, monospace',
                  overflowX: 'auto',
                  margin: 0,
                  maxHeight: '300px',
                  overflowY: 'auto'
                }}>
                  {JSON.stringify(transition.contextBefore?.volatileContext || {}, null, 2)}
                </pre>
              </div>
            </div>
          </div>

          {/* Context After */}
          <div>
            <h3 style={{ 
              fontSize: '14px', 
              fontWeight: '600', 
              color: '#17a2b8',
              marginBottom: '10px'
            }}>
              🔵 Context After
            </h3>
            
            <div style={{
              background: '#f0f8ff',
              border: '1px solid #bee5eb',
              borderRadius: '6px',
              padding: '15px'
            }}>
              {/* Persistent Context */}
              <div style={{ marginBottom: '15px' }}>
                <h4 style={{ fontSize: '12px', fontWeight: '600', marginBottom: '8px', color: '#004085' }}>
                  Persistent Context
                </h4>
                <pre style={{
                  background: 'white',
                  border: '1px solid #bee5eb',
                  borderRadius: '4px',
                  padding: '8px',
                  fontSize: '10px',
                  fontFamily: 'Consolas, monospace',
                  overflowX: 'auto',
                  margin: 0,
                  maxHeight: '300px',
                  overflowY: 'auto'
                }}>
                  {JSON.stringify(transition.contextAfter?.persistentContext || {}, null, 2)}
                </pre>
              </div>

              {/* Volatile Context */}
              <div>
                <h4 style={{ fontSize: '12px', fontWeight: '600', marginBottom: '8px', color: '#004085' }}>
                  Volatile Context
                </h4>
                <pre style={{
                  background: 'white',
                  border: '1px solid #bee5eb',
                  borderRadius: '4px',
                  padding: '8px',
                  fontSize: '10px',
                  fontFamily: 'Consolas, monospace',
                  overflowX: 'auto',
                  margin: 0,
                  maxHeight: '300px',
                  overflowY: 'auto'
                }}>
                  {JSON.stringify(transition.contextAfter?.volatileContext || {}, null, 2)}
                </pre>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default TransitionDetailPanel;