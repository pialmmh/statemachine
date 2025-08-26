import React, { useState, useEffect } from 'react';
import StateTreeView from './StateTreeView';
import TransitionDetailPanel from './TransitionDetailPanel';
import PayloadModal from './PayloadModal';
import wsLogger from '../wsLogger';
import config from '../config';
import treeViewStore from '../store/treeViewStore';

function LiveHistoryDisplay({ transitions, mysqlHistory, selectedMachineId, countdownState, countdownRemaining, viewMode = 'tree', wsConnection }) {
  const [selectedTransition, setSelectedTransition] = useState(null);
  const [modalPayload, setModalPayload] = useState(null);
  const [modalEventName, setModalEventName] = useState(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  
  // Set WebSocket connection in store
  useEffect(() => {
    if (wsConnection) {
      treeViewStore.setWebSocket(wsConnection);
    }
  }, [wsConnection]);

  wsLogger.render('LiveHistoryDisplay', '=== LiveHistoryDisplay RENDER ===');
  wsLogger.render('LiveHistoryDisplay', '  Machine:', selectedMachineId || 'none');
  wsLogger.render('LiveHistoryDisplay', '  Transitions:', transitions?.length || 0);

  // Helper function to format payload for display (first 50 chars)
  const formatPayloadDisplay = (payload) => {
    if (!payload) return '';
    const jsonStr = JSON.stringify(payload);
    if (jsonStr.length <= 50) {
      return jsonStr;
    }
    return jsonStr.substring(0, 50) + '...';
  };

  // Helper function to open payload modal
  const openPayloadModal = (payload, eventName) => {
    setModalPayload(payload);
    setModalEventName(eventName);
    setIsModalOpen(true);
  };

  // Helper function to close modal
  const closeModal = () => {
    setIsModalOpen(false);
    setModalPayload(null);
    setModalEventName(null);
  };

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      {/* Conditional View Rendering */}
      {viewMode === 'tree' ? (
        <div style={{ 
          display: 'grid', 
          gridTemplateColumns: '320px 1fr', 
          gap: '20px',
          height: '100%',
          minHeight: '600px'
        }}>
          {/* Left: Tree View with independent scroll */}
          <div style={{ 
            background: '#f8f9fa',
            borderRadius: '8px',
            padding: '0',
            overflowY: 'auto',
            overflowX: 'hidden',
            boxShadow: '0 2px 4px rgba(0,0,0,0.05)',
            height: '100%',
            position: 'relative'
          }}>
            <StateTreeView 
              transitions={transitions}
              selectedMachineId={selectedMachineId}
              onSelectTransition={setSelectedTransition}
              selectedTransition={selectedTransition}
              countdownState={countdownState}
              countdownRemaining={countdownRemaining}
            />
          </div>

          {/* Right: Detail Panel with independent scroll */}
          <div style={{ 
            background: '#ffffff',
            borderRadius: '8px',
            boxShadow: '0 2px 8px rgba(0,0,0,0.08)',
            overflowY: 'auto',
            overflowX: 'hidden',
            height: '100%',
            position: 'relative'
          }}>
            <TransitionDetailPanel 
              transition={selectedTransition}
              countdownState={countdownState}
              countdownRemaining={countdownRemaining}
              allTransitions={transitions}
            />
          </div>
        </div>
      ) : (
        /* Event Viewer Table */
        <div style={{
          background: 'white',
          borderRadius: '8px',
          padding: '10px 20px 20px 20px',
          height: '100%',
          overflowY: 'auto',
          boxShadow: '0 2px 8px rgba(0,0,0,0.08)'
        }}>
          {!mysqlHistory || mysqlHistory.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '40px', color: '#666' }}>
              {!selectedMachineId ? 
                'Select a machine to view its events' : 
                `No events recorded for ${selectedMachineId}`}
            </div>
          ) : (
            <table style={{
              width: '100%',
              borderCollapse: 'collapse',
              fontSize: '12px',
              fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
            }}>
              <thead>
                <tr style={{ borderBottom: '2px solid #dee2e6' }}>
                  <th style={{ padding: '8px 8px', textAlign: 'left', fontWeight: '600', color: '#495057' }}>#</th>
                  <th style={{ padding: '8px 8px', textAlign: 'left', fontWeight: '600', color: '#495057' }}>State</th>
                  <th style={{ padding: '8px 8px', textAlign: 'left', fontWeight: '600', color: '#495057' }}>Event</th>
                  <th style={{ padding: '8px 8px', textAlign: 'left', fontWeight: '600', color: '#495057' }}>Transition</th>
                  <th style={{ padding: '8px 8px', textAlign: 'left', fontWeight: '600', color: '#495057' }}>Timestamp</th>
                  <th style={{ padding: '8px 8px', textAlign: 'left', fontWeight: '600', color: '#495057' }}>Duration</th>
                  <th style={{ padding: '8px 8px', textAlign: 'left', fontWeight: '600', color: '#495057' }}>Payload</th>
                </tr>
              </thead>
              <tbody>
                {mysqlHistory.map((event, idx) => {
                  // Parse MySQL history format
                  const transition = {
                    stepNumber: event.id || idx + 1,
                    event: event.event || 'UNKNOWN',
                    fromState: event.state || 'UNKNOWN',
                    toState: event.transitionToState || event.state || 'UNKNOWN',
                    timestamp: event.datetime ? new Date(event.datetime).toLocaleTimeString() : '-',
                    duration: event.duration || 0,
                    payload: event.eventPayload || null,
                    transitionOrStay: event.transitionOrStay || false,
                    eventIgnored: event.eventIgnored || false
                  };
                  
                  return (
                    <tr key={transition.stepNumber || idx} style={{ 
                    borderBottom: '1px solid #e9ecef',
                    background: idx % 2 === 0 ? 'white' : '#f8f9fa',
                    opacity: transition.eventIgnored ? 0.6 : 1
                  }}>
                    <td style={{ padding: '10px 8px', color: '#6c757d', textAlign: 'left' }}>
                      {transition.stepNumber}
                    </td>
                    <td style={{ padding: '10px 8px', textAlign: 'left' }}>
                      <span style={{ fontWeight: '600', color: '#495057' }}>
                        {transition.fromState}
                      </span>
                    </td>
                    <td style={{ padding: '10px 8px', textAlign: 'left' }}>
                      <span style={{ fontWeight: '500', color: transition.eventIgnored ? '#6c757d' : '#212529' }}>
                        {/* Use same icons as tree view */}
                        {transition.eventIgnored ? '🚫 ' :
                         transition.event === 'Entry' || transition.event === 'Initial State' || 
                         transition.event === 'Initial' || transition.event === 'Start' ? '🎯 ' : 
                         transition.event === 'TIMEOUT' ? '⏰ ' : '⚡ '}
                        {transition.event}
                        {transition.eventIgnored && ' (ignored)'}
                      </span>
                    </td>
                    <td style={{ padding: '10px 8px', textAlign: 'left' }}>
                      <span style={{ color: transition.transitionOrStay ? '#28a745' : '#6c757d' }}>
                        {transition.transitionOrStay ? '→ ' + transition.toState : '(stayed)'}
                      </span>
                    </td>
                    <td style={{ padding: '10px 8px', color: '#6c757d', fontSize: '11px', textAlign: 'left' }}>
                      🕐 {transition.timestamp}
                    </td>
                    <td style={{ padding: '10px 8px', color: '#6c757d', fontSize: '11px', textAlign: 'left' }}>
                      ⏱️ {transition.duration > 0 ? `${transition.duration}ms` : '-'}
                    </td>
                    <td style={{ padding: '10px 8px', textAlign: 'left' }}>
                      {transition.payload ? (
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <span style={{ 
                            color: '#6c757d', 
                            fontSize: '11px',
                            fontFamily: 'monospace',
                            maxWidth: '200px',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap'
                          }}>
                            📦 {formatPayloadDisplay(transition.payload)}
                          </span>
                          <button
                            onClick={() => openPayloadModal(transition.payload, transition.event)}
                            style={{
                              padding: '2px 8px',
                              fontSize: '10px',
                              background: '#17a2b8',
                              color: 'white',
                              border: 'none',
                              borderRadius: '3px',
                              cursor: 'pointer',
                              fontWeight: '500'
                            }}
                            onMouseEnter={(e) => { e.currentTarget.style.background = '#138496'; }}
                            onMouseLeave={(e) => { e.currentTarget.style.background = '#17a2b8'; }}
                          >
                            👁️ View
                          </button>
                        </div>
                      ) : (
                        <span style={{ color: '#dee2e6', fontSize: '11px' }}>-</span>
                      )}
                    </td>
                  </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>
      )}
      
      {/* Payload Modal */}
      <PayloadModal 
        isOpen={isModalOpen}
        onClose={closeModal}
        payload={modalPayload}
        eventName={modalEventName}
      />
    </div>
  );
}

export default LiveHistoryDisplay;