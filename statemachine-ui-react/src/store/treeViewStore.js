/**
 * TreeView Store - Simplified to receive and display backend state
 * The backend manages all state through History.java
 */
import wsLogger from '../wsLogger';

class TreeViewStore {
  constructor() {
    this.state = {
      availableMachines: [],
      selectedMachineId: null,
      transitions: [],
      lastUpdate: null,
      version: 0
    };
    
    this.listeners = [];
    this.wsConnection = null;
  }

  // Set WebSocket connection for sending UI events to backend
  setWebSocket(ws) {
    this.wsConnection = ws;
  }

  // Subscribe to store changes
  subscribe(listener) {
    this.listeners.push(listener);
    return () => {
      this.listeners = this.listeners.filter(l => l !== listener);
    };
  }

  // Notify all listeners of state change
  notifyListeners() {
    this.listeners.forEach(listener => listener(this.state));
  }

  // Get current state
  getSnapshot() {
    return { ...this.state };
  }

  /**
   * Replace entire store with backend update
   * Called when TREEVIEW_STORE_UPDATE is received from WebSocket
   */
  replaceStore(backendStore) {
    wsLogger.log('TreeViewStore', 'Replacing store with backend update, version:', backendStore.version);
    wsLogger.log('TreeViewStore', 'Selected machine:', backendStore.selectedMachineId);
    wsLogger.log('TreeViewStore', 'Transitions count:', backendStore.transitions?.length || 0);
    wsLogger.log('TreeViewStore', 'Raw backend store:', JSON.stringify(backendStore));
    
    // Directly replace the entire state with simplified structure
    this.state = {
      availableMachines: backendStore.availableMachines || [],
      selectedMachineId: backendStore.selectedMachineId || null,
      transitions: backendStore.transitions || [],
      lastUpdate: backendStore.lastUpdate || null,
      version: backendStore.version || 0
    };
    
    wsLogger.log('TreeViewStore', 'New state after update:', JSON.stringify(this.state));
    this.notifyListeners();
  }

  /**
   * Send UI action to backend
   * The backend will process and send back updated store
   */
  sendAction(action, payload) {
    wsLogger.log('TreeViewStore', 'sendAction called with:', action, JSON.stringify(payload));
    wsLogger.log('TreeViewStore', 'wsConnection exists:', !!this.wsConnection);
    wsLogger.log('TreeViewStore', 'wsConnection state:', this.wsConnection ? this.wsConnection.readyState : 'null');
    wsLogger.log('TreeViewStore', 'WebSocket.OPEN value:', WebSocket.OPEN);
    wsLogger.log('TreeViewStore', 'readyState === OPEN?', this.wsConnection ? (this.wsConnection.readyState === WebSocket.OPEN) : false);
    wsLogger.log('TreeViewStore', 'readyState === 1?', this.wsConnection ? (this.wsConnection.readyState === 1) : false);
    
    if (this.wsConnection && this.wsConnection.readyState === 1) {  // Use numeric value directly
      const message = {
        type: 'TREEVIEW_ACTION',
        action: action,
        payload: payload,
        timestamp: new Date().toISOString()
      };
      
      wsLogger.log('TreeViewStore', 'Sending TREEVIEW_ACTION to backend:', action, JSON.stringify(payload));
      wsLogger.log('TreeViewStore', 'Full message being sent:', JSON.stringify(message));
      this.wsConnection.send(JSON.stringify(message));
    } else {
      wsLogger.log('TreeViewStore', 'WebSocket not ready - cannot send action. Connection state:', 
        this.wsConnection ? this.wsConnection.readyState : 'null');
      wsLogger.log('TreeViewStore', 'WebSocket object:', this.wsConnection);
    }
  }

  // UI Actions that send requests to backend

  selectMachine(machineId) {
    wsLogger.log('TreeViewStore', 'selectMachine called with:', machineId);
    this.sendAction('SELECT_MACHINE', { machineId });
  }

  // Reset store (local only - for disconnect scenarios)
  reset() {
    this.state = {
      availableMachines: [],
      selectedMachineId: null,
      transitions: [],
      lastUpdate: null,
      version: 0
    };
    this.notifyListeners();
  }

}

// Create singleton instance
const treeViewStore = new TreeViewStore();

export default treeViewStore;