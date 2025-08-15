import React from 'react';
import ContextDisplay from './ContextDisplay';

function TransitionCard({ transition }) {
  const eventBgColor = transition.event === 'Initial State' ? '#e8f5e9' : '#d1ecf1';
  const eventColor = transition.event === 'Initial State' ? '#4caf50' : '#17a2b8';
  
  return (
    <div style={{ 
      background: 'white', 
      border: '1px solid #dee2e6', 
      borderRadius: '10px', 
      margin: '15px', 
      overflow: 'hidden', 
      boxShadow: '0 2px 6px rgba(0,0,0,0.05)' 
    }}>
      {/* Step Header */}
      <div style={{ 
        background: eventBgColor, 
        padding: '10px 15px', 
        borderBottom: '1px solid #dee2e6', 
        display: 'flex', 
        justifyContent: 'space-between', 
        alignItems: 'center' 
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
          <span style={{ fontWeight: 600, color: '#495057' }}>Step {transition.stepNumber}</span>
          <span style={{ 
            background: eventColor, 
            color: 'white', 
            padding: '3px 10px', 
            borderRadius: '4px', 
            fontSize: '12px', 
            fontWeight: 600 
          }}>
            {transition.event}
          </span>
          <span style={{ fontSize: '12px', color: '#6c757d' }}>
            {transition.fromState === 'Initial' 
              ? `Initial State: ${transition.toState}` 
              : `${transition.fromState} → ${transition.toState}`}
          </span>
        </div>
      </div>

      {/* Three-column grid for context */}
      <div style={{ 
        padding: '15px', 
        display: 'grid', 
        gridTemplateColumns: '1fr 1fr 1fr', 
        gap: '15px' 
      }}>
        {/* Event Payload */}
        <ContextDisplay
          title="📌 Event Payload"
          data={transition.eventData || {}}
          bgColor="#f8f9fa"
          borderColor="#28a745"
          titleColor="#28a745"
        />

        {/* Registry Status Before */}
        <ContextDisplay
          title="Registry Status Before:"
          registryStatus={transition.contextBefore?.registryStatus}
          persistentContext={transition.contextBefore?.persistentContext || {}}
          volatileContext={transition.contextBefore?.volatileContext || {}}
          bgColor="#fff5f5"
          borderColor="#dc3545"
          titleColor="#dc3545"
        />

        {/* Registry Status After */}
        <ContextDisplay
          title="Registry Status After:"
          registryStatus={transition.contextAfter?.registryStatus}
          persistentContext={transition.contextAfter?.persistentContext || {}}
          volatileContext={transition.contextAfter?.volatileContext || {}}
          bgColor="#f0f8ff"
          borderColor="#17a2b8"
          titleColor="#17a2b8"
        />
      </div>
    </div>
  );
}

export default TransitionCard;