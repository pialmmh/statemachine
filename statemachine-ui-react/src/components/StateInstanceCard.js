import React from 'react';
import TransitionCard from './TransitionCard';

function StateInstanceCard({ instance, countdownState, countdownRemaining }) {
  return (
    <div style={{ 
      background: '#fff', 
      borderRadius: '8px', 
      marginBottom: '20px', 
      overflow: 'hidden', 
      boxShadow: '0 2px 4px rgba(0,0,0,0.1)' 
    }}>
      {/* State Header */}
      <div style={{ 
        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)', 
        color: 'white', 
        padding: '12px 20px', 
        position: 'relative' 
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
            <h3 style={{ margin: 0, fontSize: '16px' }}>
              State: {instance.state}
              {instance.instanceNumber > 1 && (
                <span style={{ opacity: 0.8, fontSize: '14px' }}> (#{instance.instanceNumber})</span>
              )}
            </h3>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', fontSize: '13px', opacity: 0.9 }}>
              <span>🔄 {instance.transitions.length} event{instance.transitions.length > 1 ? 's' : ''}</span>
              <span style={{ opacity: 0.7 }}>|</span>
              <span>Steps {instance.transitions[0].stepNumber}-{instance.transitions[instance.transitions.length - 1].stepNumber}</span>
              <span style={{ opacity: 0.7 }}>|</span>
              <span>🕒 {instance.transitions[0].timestamp ? instance.transitions[0].timestamp.split('T')[1] || instance.transitions[0].timestamp : new Date().toLocaleTimeString()}</span>
            </div>
          </div>
          {/* Countdown Timer */}
          {countdownState === instance.state && countdownRemaining > 0 && (
            <div style={{ 
              background: 'rgba(255,255,255,0.2)', 
              padding: '5px 12px', 
              borderRadius: '20px', 
              fontSize: '14px',
              fontWeight: '600',
              display: 'flex',
              alignItems: 'center',
              gap: '8px'
            }}>
              <span>⏱️</span>
              <span>{countdownRemaining}s</span>
            </div>
          )}
        </div>
      </div>

      {/* Transitions for this state instance */}
      {instance.transitions.map(transition => (
        <TransitionCard key={transition.stepNumber} transition={transition} />
      ))}
    </div>
  );
}

export default StateInstanceCard;