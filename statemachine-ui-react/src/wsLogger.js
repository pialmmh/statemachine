// WebSocket Logger for React App
// Sends logs to backend for storage

class WSLogger {
  constructor() {
    this.ws = null;
    this.queue = [];
    this.isConnected = false;
  }

  setWebSocket(ws) {
    this.ws = ws;
    this.isConnected = ws && ws.readyState === WebSocket.OPEN;
    
    // Flush queued messages
    if (this.isConnected) {
      this.flushQueue();
    }
  }

  log(category, message, data = null) {
    const logEntry = {
      action: 'LOG',
      timestamp: new Date().toISOString(),
      category: category,
      message: message,
      data: data
    };

    if (this.isConnected && this.ws) {
      try {
        this.ws.send(JSON.stringify(logEntry));
      } catch (e) {
        console.error('Failed to send log:', e);
        this.queue.push(logEntry);
      }
    } else {
      // Queue the message for later
      this.queue.push(logEntry);
      
      // Also log to console as fallback
      console.log(`[${category}] ${message}`, data || '');
    }
  }

  flushQueue() {
    while (this.queue.length > 0 && this.isConnected && this.ws) {
      const entry = this.queue.shift();
      try {
        this.ws.send(JSON.stringify(entry));
      } catch (e) {
        console.error('Failed to flush log:', e);
        this.queue.unshift(entry); // Put it back
        break;
      }
    }
  }

  // Convenience methods
  info(message, data) {
    this.log('INFO', message, data);
  }

  error(message, data) {
    this.log('ERROR', message, data);
  }

  debug(message, data) {
    this.log('DEBUG', message, data);
  }

  ws_in(message) {
    this.log('WS_IN', 'Received WebSocket message', message);
  }

  ws_out(message) {
    this.log('WS_OUT', 'Sending WebSocket message', message);
  }

  history(message, data) {
    this.log('HISTORY', message, data);
  }

  render(component, message, data) {
    this.log('RENDER', `[${component}] ${message}`, data);
  }
}

// Create singleton instance
const wsLogger = new WSLogger();

export default wsLogger;