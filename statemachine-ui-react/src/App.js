import React, { useState } from 'react';
import MonitoringApp from './MonitoringApp';
import LogViewer from './LogViewer';
import './App.css';

function App() {
  const [activeTab, setActiveTab] = useState('live');

  return (
    <div className="app">
      <div className="app-header">
        <div className="header-left">
          <span className="app-title">📊 TelcoBright State Machine Monitoring</span>
          <span className="app-subtitle">Real-time monitoring of CallMachine and SmsMachine state transitions</span>
        </div>
        <div className="tab-navigation">
          <button 
            className={`tab-button ${activeTab === 'live' ? 'active' : ''}`}
            onClick={() => setActiveTab('live')}
          >
            🔴 Live Viewer
          </button>
          <button 
            className={`tab-button ${activeTab === 'snapshot' ? 'active' : ''}`}
            onClick={() => setActiveTab('snapshot')}
          >
            📸 Snapshot Viewer
          </button>
          <button 
            className={`tab-button ${activeTab === 'logs' ? 'active' : ''}`}
            onClick={() => setActiveTab('logs')}
          >
            📋 Event Logs
          </button>
        </div>
      </div>
      
      <div className="app-content">
        {activeTab === 'logs' && <LogViewer />}
        {activeTab === 'snapshot' && <MonitoringApp mode="snapshot" />}
        {activeTab === 'live' && <MonitoringApp mode="live" />}
      </div>
    </div>
  );
}

export default App;