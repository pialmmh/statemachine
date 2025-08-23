// Configuration for the State Machine UI React App

const config = {
  // WebSocket configuration
  wsHost: 'localhost',
  wsPort: 9999,
  
  // Display settings
  maxEventDisplayLength: 50,  // Maximum characters to display for event payload in table
  
  // Timing settings
  countdownInterval: 1000,  // Countdown update interval in milliseconds
  historyPollInterval: 2000,  // History polling interval in milliseconds
  
  // UI settings
  expandAllByDefault: true,  // Whether to expand all state nodes by default
  showDebugLogs: false,  // Whether to show debug logs in console
};

export default config;