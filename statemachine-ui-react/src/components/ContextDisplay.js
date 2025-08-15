import React from 'react';

function ContextDisplay({ 
  title, 
  data, 
  registryStatus, 
  persistentContext, 
  volatileContext,
  bgColor, 
  borderColor, 
  titleColor 
}) {
  // If we have just data (for Event Payload)
  if (data !== undefined) {
    return (
      <div style={{ 
        background: bgColor, 
        borderRadius: '6px', 
        padding: '12px', 
        borderLeft: `3px solid ${borderColor}` 
      }}>
        <div style={{ 
          fontWeight: 600, 
          color: titleColor, 
          marginBottom: '8px', 
          fontSize: '13px' 
        }}>
          {title}
        </div>
        <pre style={{ 
          margin: 0, 
          background: 'white', 
          padding: '8px', 
          borderRadius: '4px', 
          overflowX: 'auto', 
          fontSize: '10px', 
          lineHeight: 1.3 
        }}>
          {JSON.stringify(data, null, 2)}
        </pre>
      </div>
    );
  }

  // For Registry Status Before/After
  return (
    <div style={{ 
      background: bgColor, 
      borderRadius: '6px', 
      padding: '12px', 
      borderLeft: `3px solid ${borderColor}`, 
      fontSize: '11px' 
    }}>
      <div style={{ fontWeight: 600, color: titleColor, marginBottom: '8px' }}>
        {title}
      </div>
      
      {registryStatus && (
        <div style={{ fontFamily: 'monospace', fontSize: '11px', lineHeight: 1.4, marginBottom: '12px' }}>
          Status: {registryStatus.status || 'ACTIVE'}<br />
          Hydrated: {registryStatus.hydrated ? '✅ Yes' : '🔄 No'}<br />
          Online: {registryStatus.online !== false ? '🟢 Yes' : '🔴 No'}
        </div>
      )}
      
      {persistentContext && (
        <div style={{ marginBottom: '8px' }}>
          <div style={{ fontWeight: 600, color: '#495057', marginBottom: '4px', fontSize: '11px' }}>
            Persistent Context:
          </div>
          <pre style={{ 
            margin: 0, 
            fontSize: '10px', 
            fontFamily: 'monospace', 
            background: '#f8f9fa', 
            padding: '8px', 
            borderRadius: '4px', 
            lineHeight: 1.3 
          }}>
            {JSON.stringify(persistentContext, null, 2)}
          </pre>
        </div>
      )}
      
      {volatileContext && (
        <div>
          <div style={{ fontWeight: 600, color: '#495057', marginBottom: '4px', fontSize: '11px' }}>
            Volatile Context:
          </div>
          <pre style={{ 
            margin: 0, 
            fontSize: '10px', 
            fontFamily: 'monospace', 
            background: '#f8f9fa', 
            padding: '8px', 
            borderRadius: '4px', 
            lineHeight: 1.3 
          }}>
            {JSON.stringify(volatileContext, null, 2)}
          </pre>
        </div>
      )}
    </div>
  );
}

export default ContextDisplay;