import React, { useState, useEffect, useRef } from 'react';
import './LogViewer.css';

const LogViewer = () => {
    const [events, setEvents] = useState([]);
    const [page, setPage] = useState(1);
    const [pageSize] = useState(20);
    const [totalPages, setTotalPages] = useState(1);
    const [totalEvents, setTotalEvents] = useState(0);
    const [selectedDate, setSelectedDate] = useState(new Date().toISOString().split('T')[0]);
    const [selectedCategory, setSelectedCategory] = useState('');
    const [selectedMachine, setSelectedMachine] = useState('');
    const [availableCategories, setAvailableCategories] = useState([]);
    const [availableMachines, setAvailableMachines] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [connectionStatus, setConnectionStatus] = useState('disconnected');
    const [expandedEvent, setExpandedEvent] = useState(null);
    
    const wsRef = useRef(null);

    // WebSocket connection
    useEffect(() => {
        const connectWebSocket = () => {
            const ws = new WebSocket('ws://localhost:9999');
            
            ws.onopen = () => {
                console.log('Connected to WebSocket server');
                setConnectionStatus('connected');
                setError(null);
                // Load initial events
                loadEvents(1);
            };
            
            ws.onmessage = (event) => {
                try {
                    const data = JSON.parse(event.data);
                    if (data.type === 'EVENTS_DATA') {
                        setEvents(data.events || []);
                        setTotalPages(data.totalPages || 1);
                        setTotalEvents(data.totalEvents || 0);
                        setAvailableCategories(data.availableCategories || []);
                        setAvailableMachines(data.availableMachines || []);
                        setLoading(false);
                    } else if (data.type === 'EVENTS_ERROR') {
                        setError(data.error);
                        setLoading(false);
                    }
                } catch (err) {
                    console.error('Error parsing WebSocket message:', err);
                }
            };
            
            ws.onclose = () => {
                console.log('WebSocket connection closed');
                setConnectionStatus('disconnected');
                // Attempt to reconnect after 3 seconds
                setTimeout(connectWebSocket, 3000);
            };
            
            ws.onerror = (error) => {
                console.error('WebSocket error:', error);
                setConnectionStatus('error');
                setError('WebSocket connection error');
            };
            
            wsRef.current = ws;
        };
        
        connectWebSocket();
        
        return () => {
            if (wsRef.current) {
                wsRef.current.close();
            }
        };
    }, []);

    // Load events function
    const loadEvents = (pageNum) => {
        if (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) {
            setError('WebSocket not connected');
            return;
        }
        
        setLoading(true);
        setError(null);
        
        const request = {
            action: 'GET_EVENTS',
            page: pageNum,
            pageSize: pageSize,
            date: selectedDate,
            category: selectedCategory,
            machineId: selectedMachine
        };
        
        wsRef.current.send(JSON.stringify(request));
    };

    // Reload events when filters change
    useEffect(() => {
        if (connectionStatus === 'connected') {
            setPage(1);
            loadEvents(1);
        }
    }, [selectedDate, selectedCategory, selectedMachine, connectionStatus]);

    // Pagination handlers
    const handlePrevPage = () => {
        if (page > 1) {
            const newPage = page - 1;
            setPage(newPage);
            loadEvents(newPage);
        }
    };

    const handleNextPage = () => {
        if (page < totalPages) {
            const newPage = page + 1;
            setPage(newPage);
            loadEvents(newPage);
        }
    };

    const handleFirstPage = () => {
        setPage(1);
        loadEvents(1);
    };

    const handleLastPage = () => {
        setPage(totalPages);
        loadEvents(totalPages);
    };

    // Format timestamp for display
    const formatTimestamp = (timestamp) => {
        if (!timestamp) return 'N/A';
        const date = new Date(timestamp.replace('T', ' '));
        return date.toLocaleString();
    };

    // Get event category color
    const getCategoryColor = (category) => {
        const colors = {
            'STATE_CHANGE': '#4CAF50',
            'WEBSOCKET_IN': '#2196F3',
            'WEBSOCKET_OUT': '#03A9F4',
            'REGISTRY_CREATE': '#8BC34A',
            'REGISTRY_REMOVE': '#FF9800',
            'REGISTRY_REHYDRATE': '#9C27B0',
            'TIMEOUT': '#F44336',
            'EVENT_FIRED': '#FFC107',
            'ERROR': '#F44336'
        };
        return colors[category] || '#9E9E9E';
    };

    // Toggle event details
    const toggleEventDetails = (eventId) => {
        setExpandedEvent(expandedEvent === eventId ? null : eventId);
    };

    return (
        <div className="log-viewer">
            <div className="log-viewer-filters">
                <h3>Event Logs</h3>
                
                <div className="filter-controls">
                    <div className="filter-group">
                        <label>Date:</label>
                        <input 
                            type="date" 
                            value={selectedDate}
                            onChange={(e) => setSelectedDate(e.target.value)}
                            max={new Date().toISOString().split('T')[0]}
                        />
                    </div>

                    <div className="filter-group">
                        <label>Category:</label>
                        <select 
                            value={selectedCategory}
                            onChange={(e) => setSelectedCategory(e.target.value)}
                        >
                            <option value="">All</option>
                            {availableCategories.map(cat => (
                                <option key={cat} value={cat}>{cat}</option>
                            ))}
                        </select>
                    </div>

                    <div className="filter-group">
                        <label>Machine:</label>
                        <select 
                            value={selectedMachine}
                            onChange={(e) => setSelectedMachine(e.target.value)}
                        >
                            <option value="">All</option>
                            {availableMachines.map(machine => (
                                <option key={machine} value={machine}>{machine}</option>
                            ))}
                        </select>
                    </div>

                    <button 
                        className="refresh-button"
                        onClick={() => loadEvents(page)}
                        disabled={loading || connectionStatus !== 'connected'}
                    >
                        🔄
                    </button>
                </div>
                
                <div className={`connection-status ${connectionStatus}`}>
                    <span className="status-dot"></span>
                    {connectionStatus === 'connected' ? 'Connected' : 
                     connectionStatus === 'error' ? 'Error' : 'Disconnected'}
                </div>
            </div>

            {error && (
                <div className="error-message">
                    Error: {error}
                </div>
            )}

            {loading && (
                <div className="loading-indicator">
                    Loading events...
                </div>
            )}

            <div className="events-info">
                Showing {events.length} of {totalEvents} events (Page {page} of {totalPages})
            </div>

            <div className="events-table-container">
                <table className="events-table">
                    <thead>
                        <tr>
                            <th>Time</th>
                            <th>Category</th>
                            <th>Event Type</th>
                            <th>Machine</th>
                            <th>Source → Dest</th>
                            <th>State Change</th>
                            <th>Status</th>
                            <th>Time (ms)</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        {events.map(event => (
                            <React.Fragment key={event.id}>
                                <tr className={`event-row ${event.success === false ? 'error' : ''}`}>
                                    <td className="timestamp">{formatTimestamp(event.timestamp)}</td>
                                    <td>
                                        <span 
                                            className="event-category"
                                            style={{ backgroundColor: getCategoryColor(event.eventCategory) }}
                                        >
                                            {event.eventCategory}
                                        </span>
                                    </td>
                                    <td>{event.eventType}</td>
                                    <td className="machine-id">{event.machineId || 'N/A'}</td>
                                    <td className="flow">
                                        <span className="source">{event.source || 'N/A'}</span>
                                        <span className="arrow">→</span>
                                        <span className="dest">{event.destination || 'N/A'}</span>
                                    </td>
                                    <td className="state-change">
                                        {event.stateBefore && event.stateAfter ? (
                                            <>
                                                <span className="state">{event.stateBefore}</span>
                                                <span className="arrow">→</span>
                                                <span className="state">{event.stateAfter}</span>
                                            </>
                                        ) : 'N/A'}
                                    </td>
                                    <td>
                                        {event.success === false ? (
                                            <span className="status-error">❌ Error</span>
                                        ) : (
                                            <span className="status-success">✅ Success</span>
                                        )}
                                    </td>
                                    <td className="processing-time">{event.processingTimeMs || 0}</td>
                                    <td>
                                        <button 
                                            className="details-button"
                                            onClick={() => toggleEventDetails(event.id)}
                                        >
                                            {expandedEvent === event.id ? '▼' : '▶'} Details
                                        </button>
                                    </td>
                                </tr>
                                {expandedEvent === event.id && (
                                    <tr className="event-details-row">
                                        <td colSpan="9">
                                            <div className="event-details">
                                                <div className="detail-section">
                                                    <strong>Event ID:</strong> {event.id}
                                                </div>
                                                {event.errorMessage && (
                                                    <div className="detail-section error">
                                                        <strong>Error:</strong> {event.errorMessage}
                                                    </div>
                                                )}
                                                {event.details && (
                                                    <div className="detail-section">
                                                        <strong>Details:</strong>
                                                        <pre>{JSON.stringify(event.details, null, 2)}</pre>
                                                    </div>
                                                )}
                                            </div>
                                        </td>
                                    </tr>
                                )}
                            </React.Fragment>
                        ))}
                    </tbody>
                </table>
            </div>

            <div className="pagination-controls">
                <button 
                    onClick={handleFirstPage}
                    disabled={page === 1 || loading}
                    className="pagination-button"
                >
                    ⏮ First
                </button>
                <button 
                    onClick={handlePrevPage}
                    disabled={page === 1 || loading}
                    className="pagination-button"
                >
                    ◀ Previous
                </button>
                <span className="page-info">
                    Page {page} of {totalPages}
                </span>
                <button 
                    onClick={handleNextPage}
                    disabled={page === totalPages || loading}
                    className="pagination-button"
                >
                    Next ▶
                </button>
                <button 
                    onClick={handleLastPage}
                    disabled={page === totalPages || loading}
                    className="pagination-button"
                >
                    Last ⏭
                </button>
            </div>
        </div>
    );
};

export default LogViewer;