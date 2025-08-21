import React from 'react';

function TransitionDetailPanel({ transition, countdownState, countdownRemaining }) {
  console.log('TransitionDetailPanel received transition:', transition);
  if (transition) {
    console.log('  Context before:', transition.contextBefore);
    console.log('  Context after:', transition.contextAfter);
  }
  
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
        fontSize: '14px',
        fontFamily: '"Inter", "SF Pro Display", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif'
      }}>
        <div style={{ textAlign: 'center' }}>
          <div style={{ fontSize: '48px', marginBottom: '15px', opacity: 0.3 }}>📋</div>
          <p style={{ fontFamily: '"Inter", sans-serif' }}>Select a transition from the tree to view details</p>
        </div>
      </div>
    );
  }

  const eventBgColor = '#d1ecf1';
  const eventColor = '#17a2b8';

  // Special handling for TRANSITION events
  if (transition.event === 'TRANSITION') {
    const fromState = transition.state;
    const toState = transition.transitionToState;
    
    return (
      <div style={{
        background: 'white',
        borderRadius: '8px',
        height: '100%',
        overflowY: 'auto',
        display: 'flex',
        flexDirection: 'column',
        fontFamily: '"Inter", "SF Pro Display", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif'
      }}>
        {/* Header for TRANSITION */}
        <div style={{
          background: '#d4edda',
          padding: '15px 20px',
          borderBottom: '2px solid #dee2e6',
          borderTopLeftRadius: '8px',
          borderTopRightRadius: '8px'
        }}>
          <h2 style={{
            margin: 0,
            fontSize: '18px',
            fontWeight: '600',
            color: '#155724',
            fontFamily: '"Inter", "SF Pro Display", sans-serif',
            letterSpacing: '-0.01em'
          }}>
            Step: TRANSITION, {fromState} → {toState}
          </h2>
        </div>
        
        {/* Content area - empty for transitions */}
        <div style={{
          flex: 1,
          padding: '20px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#6c757d',
          fontSize: '14px'
        }}>
          <div style={{ textAlign: 'center' }}>
            <div style={{ fontSize: '48px', marginBottom: '15px', opacity: 0.3 }}>🔀</div>
            <p>State transition from <strong>{fromState}</strong> to <strong>{toState}</strong></p>
            <p style={{ fontSize: '12px', opacity: 0.7 }}>No additional context for transition events</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div style={{
      background: 'white',
      borderRadius: '8px',
      height: '100%',
      overflowY: 'auto',
      display: 'flex',
      flexDirection: 'column',
      fontFamily: '"Inter", "SF Pro Display", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif'
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
            fontWeight: '600',
            fontFamily: '"Inter", "SF Pro Display", sans-serif',
            letterSpacing: '-0.01em'
          }}>
            Step {transition.stepNumber}: {transition.event}
          </span>
          {transition.event !== 'TRANSITION' && (
            <span style={{ fontSize: '14px', color: '#000000', fontFamily: '"Inter", sans-serif', fontWeight: '700' }}>
              State: {transition.state}
            </span>
          )}
          <span style={{ marginLeft: 'auto', fontSize: '12px', color: '#6c757d', fontFamily: '"Inter", sans-serif' }}>
            {transition.timestamp}
          </span>
          {/* Countdown Timer - Show if there's an active countdown, regardless of selected transition */}
          {countdownState && countdownRemaining > 0 && (
            <div style={{ 
              background: 'rgba(102, 126, 234, 0.1)', 
              padding: '4px 10px', 
              borderRadius: '15px', 
              fontSize: '13px',
              fontWeight: '600',
              fontFamily: '"Inter", sans-serif',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              color: eventColor,
              border: `1px solid ${eventColor}`,
              animation: countdownRemaining <= 5 ? 'pulse 1s infinite' : 'none'
            }}>
              <span>⏱️</span>
              <span>{countdownState}: {countdownRemaining}s</span>
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
            gap: '8px',
            fontFamily: '"Inter", "SF Pro Display", sans-serif',
            letterSpacing: '-0.01em'
          }}>
            📌 Event Payload
          </h3>
          <pre style={{
            background: '#f8f9fa',
            border: '1px solid #dee2e6',
            borderRadius: '6px',
            padding: '12px',
            fontSize: '12px',
            fontFamily: '"Inter", "SF Pro Text", "Segoe UI", -apple-system, sans-serif',
            overflowX: 'auto',
            margin: 0
          }}>
            {JSON.stringify(transition.eventPayload || transition.eventData || {}, null, 2)}
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
              gap: '8px',
              fontFamily: '"Inter", "SF Pro Display", sans-serif',
              letterSpacing: '-0.01em'
            }}>
              🏛️ Registry Status Before
            </h3>
            <div style={{
              background: '#fff5f5',
              border: '1px solid #f5c6cb',
              borderRadius: '6px',
              padding: '12px',
              fontSize: '12px',
              fontFamily: '"Inter", sans-serif',
              lineHeight: '1.6',
              letterSpacing: '-0.01em'
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
              gap: '8px',
              fontFamily: '"Inter", "SF Pro Display", sans-serif',
              letterSpacing: '-0.01em'
            }}>
              🏛️ Registry Status After
            </h3>
            <div style={{
              background: '#f0f8ff',
              border: '1px solid #bee5eb',
              borderRadius: '6px',
              padding: '12px',
              fontSize: '12px',
              fontFamily: '"Inter", sans-serif',
              lineHeight: '1.6',
              letterSpacing: '-0.01em'
            }}>
              Status: {(transition.persistentContext?.registryStatus?.status || 'ACTIVE') === 'ACTIVE' ? '✅ ACTIVE' : `❌ ${transition.persistentContext?.registryStatus?.status}`} | 
              Hydrated: {transition.persistentContext?.registryStatus?.hydrated ? '✅ Yes' : '🔄 No'} | 
              Online: {transition.persistentContext?.registryStatus?.online !== false ? '🟢 Yes' : '🔴 No'}
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
              marginBottom: '10px',
              fontFamily: '"Inter", "SF Pro Display", sans-serif',
              letterSpacing: '-0.01em'
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
                <h4 style={{ fontSize: '12px', fontWeight: '600', marginBottom: '8px', color: '#721c24', fontFamily: '"Inter", sans-serif' }}>
                  Persistent Context
                </h4>
                <pre style={{
                  background: 'white',
                  border: '1px solid #f5c6cb',
                  borderRadius: '4px',
                  padding: '8px',
                  fontSize: '10px',
                  fontFamily: '"Inter", "SF Pro Text", "Segoe UI", -apple-system, sans-serif',
                  overflowX: 'auto',
                  margin: 0,
                  maxHeight: '300px',
                  overflowY: 'auto'
                }}>
                  {JSON.stringify(transition.persistentContext || {}, null, 2)}
                </pre>
              </div>

              {/* Volatile Context */}
              <div>
                <h4 style={{ fontSize: '12px', fontWeight: '600', marginBottom: '8px', color: '#721c24', fontFamily: '"Inter", sans-serif' }}>
                  Volatile Context
                </h4>
                <pre style={{
                  background: 'white',
                  border: '1px solid #f5c6cb',
                  borderRadius: '4px',
                  padding: '8px',
                  fontSize: '10px',
                  fontFamily: '"Inter", "SF Pro Text", "Segoe UI", -apple-system, sans-serif',
                  overflowX: 'auto',
                  margin: 0,
                  maxHeight: '300px',
                  overflowY: 'auto'
                }}>
                  {JSON.stringify(transition.volatileContext || {}, null, 2)}
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
              marginBottom: '10px',
              fontFamily: '"Inter", "SF Pro Display", sans-serif',
              letterSpacing: '-0.01em'
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
                <h4 style={{ fontSize: '12px', fontWeight: '600', marginBottom: '8px', color: '#004085', fontFamily: '"Inter", sans-serif' }}>
                  Persistent Context
                </h4>
                <pre style={{
                  background: 'white',
                  border: '1px solid #bee5eb',
                  borderRadius: '4px',
                  padding: '8px',
                  fontSize: '10px',
                  fontFamily: '"Inter", "SF Pro Text", "Segoe UI", -apple-system, sans-serif',
                  overflowX: 'auto',
                  margin: 0,
                  maxHeight: '300px',
                  overflowY: 'auto'
                }}>
                  {JSON.stringify(transition.persistentContext || {}, null, 2)}
                </pre>
              </div>

              {/* Volatile Context */}
              <div>
                <h4 style={{ fontSize: '12px', fontWeight: '600', marginBottom: '8px', color: '#004085', fontFamily: '"Inter", sans-serif' }}>
                  Volatile Context
                </h4>
                <pre style={{
                  background: 'white',
                  border: '1px solid #bee5eb',
                  borderRadius: '4px',
                  padding: '8px',
                  fontSize: '10px',
                  fontFamily: '"Inter", "SF Pro Text", "Segoe UI", -apple-system, sans-serif',
                  overflowX: 'auto',
                  margin: 0,
                  maxHeight: '300px',
                  overflowY: 'auto'
                }}>
                  {JSON.stringify(transition.volatileContext || {}, null, 2)}
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