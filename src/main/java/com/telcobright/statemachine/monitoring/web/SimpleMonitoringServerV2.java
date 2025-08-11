package com.telcobright.statemachine.monitoring.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple State Machine Monitoring Server with Live Mode
 * 
 * Provides a web interface to view state machine monitoring data
 * with both snapshot viewing and live monitoring capabilities.
 */
public class SimpleMonitoringServerV2 {
    
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/statemachine_monitoring";
    private static final String DB_USER = "statemachine";
    private static final String DB_PASSWORD = "monitoring123";
    
    private HttpServer server;
    private Connection dbConnection;
    private boolean databaseAvailable = false;
    
    public static void main(String[] args) throws Exception {
        SimpleMonitoringServerV2 monitor = new SimpleMonitoringServerV2();
        monitor.start(8091);
        
        System.out.println("🚀 State Machine Monitoring Server Started!");
        System.out.println("📊 Open your browser and go to: http://localhost:8091");
        System.out.println("🎯 Custom monitoring interface for TelcoBright State Machines");
        
        // Keep server running
        Thread.currentThread().join();
    }
    
    public void start(int port) throws Exception {
        initDatabase();
        
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new MainPageHandler());
        server.createContext("/api/runs", new RunsApiHandler());
        server.createContext("/api/history", new HistoryApiHandler());
        server.setExecutor(null);
        server.start();
    }
    
    private void initDatabase() {
        try {
            Class.forName("org.postgresql.Driver");
            dbConnection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            databaseAvailable = true;
            System.out.println("✅ Connected to monitoring database");
        } catch (Exception e) {
            System.out.println("⚠️ Database not available - using sample data");
            System.out.println("   Install PostgreSQL JDBC driver for live data");
            databaseAvailable = false;
        }
    }
    
    class MainPageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = generateMainPage();
            
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, html.getBytes().length);
            
            OutputStream os = exchange.getResponseBody();
            os.write(html.getBytes());
            os.close();
        }
    }
    
    class RunsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                List<RunInfo> runs;
                if (databaseAvailable) {
                    runs = fetchRunsFromDatabase();
                } else {
                    runs = getSampleRuns();
                }
                
                String json = runsToJson(runs);
                
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, json.getBytes().length);
                
                OutputStream os = exchange.getResponseBody();
                os.write(json.getBytes());
                os.close();
                
            } catch (Exception e) {
                sendError(exchange, "Error fetching runs: " + e.getMessage());
            }
        }
    }
    
    class HistoryApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String query = exchange.getRequestURI().getQuery();
                String runId = null;
                if (query != null && query.startsWith("runId=")) {
                    runId = query.split("=")[1];
                }
                
                if (runId == null) {
                    sendError(exchange, "runId parameter required");
                    return;
                }
                
                List<StateTransition> history;
                if (databaseAvailable) {
                    history = fetchHistoryFromDatabase(runId);
                } else {
                    history = getSampleHistory(runId);
                }
                
                String json = historyToJson(history);
                
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, json.getBytes().length);
                
                OutputStream os = exchange.getResponseBody();
                os.write(json.getBytes());
                os.close();
                
            } catch (Exception e) {
                sendError(exchange, "Error fetching history: " + e.getMessage());
            }
        }
    }
    
    private void sendError(HttpExchange exchange, String error) throws IOException {
        String json = "{\"error\": \"" + error + "\"}";
        exchange.sendResponseHeaders(500, json.getBytes().length);
        OutputStream os = exchange.getResponseBody();
        os.write(json.getBytes());
        os.close();
    }
    
    private List<RunInfo> fetchRunsFromDatabase() throws SQLException {
        String sql = """
            SELECT DISTINCT 
                run_id, 
                machine_id, 
                machine_type,
                MAX(timestamp) as latest_timestamp,
                COUNT(*) as transition_count,
                MAX(triggering_class_name) as triggering_class_name
            FROM state_machine_snapshots 
            GROUP BY run_id, machine_id, machine_type
            ORDER BY latest_timestamp DESC 
            LIMIT 20
        """;
        
        List<RunInfo> runs = new ArrayList<>();
        
        try (PreparedStatement stmt = dbConnection.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                RunInfo run = new RunInfo();
                run.runId = rs.getString("run_id");
                run.machineId = rs.getString("machine_id");
                run.machineType = rs.getString("machine_type");
                run.latestTimestamp = rs.getTimestamp("latest_timestamp").toString();
                run.transitionCount = rs.getInt("transition_count");
                run.triggeringClassName = rs.getString("triggering_class_name");
                runs.add(run);
            }
        }
        
        return runs;
    }
    
    private List<RunInfo> getSampleRuns() {
        List<RunInfo> runs = new ArrayList<>();
        
        // Sample data
        RunInfo run1 = new RunInfo();
        run1.runId = "sample-run-001";
        run1.machineId = "call-machine-123";
        run1.machineType = "CallMachine";
        run1.latestTimestamp = LocalDateTime.now().minusMinutes(5).toString();
        run1.transitionCount = 7;
        run1.triggeringClassName = "CallMachineDemo";
        runs.add(run1);
        
        RunInfo run2 = new RunInfo();
        run2.runId = "sample-run-002";
        run2.machineId = "sms-machine-456";
        run2.machineType = "SmsMachine";
        run2.latestTimestamp = LocalDateTime.now().minusMinutes(10).toString();
        run2.transitionCount = 5;
        run2.triggeringClassName = "SmsMachineDemo";
        runs.add(run2);
        
        return runs;
    }
    
    private List<StateTransition> fetchHistoryFromDatabase(String runId) throws SQLException {
        String sql = """
            SELECT 
                version,
                timestamp,
                state_before,
                state_after,
                event_type,
                transition_duration,
                event_payload_json,
                context_before_json,
                context_after_json,
                registry_status
            FROM state_machine_snapshots 
            WHERE run_id = ?
            ORDER BY version ASC
        """;
        
        List<StateTransition> history = new ArrayList<>();
        
        try (PreparedStatement stmt = dbConnection.prepareStatement(sql)) {
            stmt.setString(1, runId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                StateTransition transition = new StateTransition();
                transition.version = rs.getLong("version");
                transition.timestamp = rs.getTimestamp("timestamp").toString();
                transition.stateBefore = rs.getString("state_before");
                transition.stateAfter = rs.getString("state_after");
                transition.eventType = rs.getString("event_type");
                transition.transitionDuration = rs.getLong("transition_duration");
                transition.eventPayloadJson = rs.getString("event_payload_json");
                transition.contextBeforeJson = rs.getString("context_before_json");
                transition.contextAfterJson = rs.getString("context_after_json");
                transition.registryStatus = rs.getString("registry_status");
                history.add(transition);
            }
        }
        
        return history;
    }
    
    private List<StateTransition> getSampleHistory(String runId) {
        List<StateTransition> history = new ArrayList<>();
        
        if ("sample-run-001".equals(runId)) {
            // Sample transitions for CallMachine
            String[] states = {"IDLE", "INCOMING", "RINGING", "IN_CALL", "COMPLETED"};
            String[] events = {"INCOMING_CALL", "SESSION_PROGRESS", "ANSWER", "HANGUP"};
            
            for (int i = 0; i < events.length; i++) {
                StateTransition t = new StateTransition();
                t.version = i + 1;
                t.timestamp = LocalDateTime.now().minusMinutes(10 - i * 2).toString();
                t.stateBefore = states[i];
                t.stateAfter = states[i + 1];
                t.eventType = events[i];
                t.transitionDuration = 50 + i * 10;
                t.registryStatus = "REGISTERED_ACTIVE";
                history.add(t);
            }
        }
        
        return history;
    }
    
    private String runsToJson(List<RunInfo> runs) {
        StringBuilder json = new StringBuilder();
        json.append("[");
        
        for (int i = 0; i < runs.size(); i++) {
            if (i > 0) json.append(",");
            RunInfo run = runs.get(i);
            json.append("{")
                .append("\"runId\":\"").append(escapeJson(run.runId)).append("\",")
                .append("\"machineId\":\"").append(escapeJson(run.machineId)).append("\",")
                .append("\"machineType\":\"").append(escapeJson(run.machineType)).append("\",")
                .append("\"latestTimestamp\":\"").append(escapeJson(run.latestTimestamp)).append("\",")
                .append("\"transitionCount\":").append(run.transitionCount).append(",")
                .append("\"triggeringClassName\":").append(run.triggeringClassName != null ? "\"" + escapeJson(run.triggeringClassName) + "\"" : "null")
                .append("}");
        }
        
        json.append("]");
        return json.toString();
    }
    
    private String historyToJson(List<StateTransition> history) {
        StringBuilder json = new StringBuilder();
        json.append("[");
        
        for (int i = 0; i < history.size(); i++) {
            if (i > 0) json.append(",");
            StateTransition t = history.get(i);
            json.append("{")
                .append("\"version\":").append(t.version).append(",")
                .append("\"timestamp\":\"").append(escapeJson(t.timestamp)).append("\",")
                .append("\"stateBefore\":\"").append(escapeJson(t.stateBefore)).append("\",")
                .append("\"stateAfter\":\"").append(escapeJson(t.stateAfter)).append("\",")
                .append("\"eventType\":\"").append(escapeJson(t.eventType)).append("\",")
                .append("\"transitionDuration\":").append(t.transitionDuration).append(",")
                .append("\"registryStatus\":\"").append(escapeJson(t.registryStatus)).append("\",")
                .append("\"eventPayloadJson\":").append(t.eventPayloadJson != null ? "\"" + escapeJson(t.eventPayloadJson) + "\"" : "null").append(",")
                .append("\"contextBeforeJson\":").append(t.contextBeforeJson != null ? "\"" + escapeJson(t.contextBeforeJson) + "\"" : "null").append(",")
                .append("\"contextAfterJson\":").append(t.contextAfterJson != null ? "\"" + escapeJson(t.contextAfterJson) + "\"" : "null")
                .append("}");
        }
        
        json.append("]");
        return json.toString();
    }
    
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }
    
    private String generateMainPage() {
        String dbStatusIcon = databaseAvailable ? "✅" : "⚠️";
        String dbStatusText = databaseAvailable ? "DB Connected" : "Sample Data";
        String dbStatusColor = databaseAvailable ? "#28a745" : "#ffc107";
        
        return String.format("""
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>State Machine Monitoring - TelcoBright</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f5f5f5; }
        
        .header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 15px 20px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        
        .header-left {
            flex: 1;
        }
        
        .header h1 { font-size: 24px; margin-bottom: 5px; }
        .header p { font-size: 14px; opacity: 0.9; }
        
        .header-right {
            display: flex;
            align-items: center;
            gap: 15px;
        }
        
        .db-status {
            display: flex;
            align-items: center;
            gap: 5px;
            font-size: 12px;
            padding: 4px 8px;
            background: rgba(255,255,255,0.1);
            border-radius: 4px;
        }
        
        .mode-buttons {
            display: flex;
            gap: 5px;
        }
        
        .mode-btn {
            padding: 6px 12px;
            background: rgba(255,255,255,0.2);
            color: white;
            border: 1px solid rgba(255,255,255,0.3);
            border-radius: 4px;
            cursor: pointer;
            font-size: 13px;
            transition: all 0.2s;
        }
        
        .mode-btn:hover {
            background: rgba(255,255,255,0.3);
        }
        
        .mode-btn.active {
            background: white;
            color: #667eea;
            font-weight: 600;
        }
        
        .container {
            display: flex;
            height: calc(100vh - 70px);
            gap: 10px;
            padding: 10px;
        }
        
        .left-panel {
            width: 350px;
            background: white;
            border-radius: 8px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
            display: flex;
            flex-direction: column;
        }
        
        .right-panel {
            flex: 1;
            background: white;
            border-radius: 8px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
            display: flex;
            flex-direction: column;
            overflow: hidden;
        }
        
        .panel-header {
            background: #f8f9fa;
            padding: 12px 15px;
            border-bottom: 1px solid #dee2e6;
            font-weight: 600;
            color: #495057;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        
        .run-list {
            flex: 1;
            overflow-y: auto;
            padding: 10px;
        }
        
        .run-item {
            padding: 12px;
            border: 1px solid #e9ecef;
            border-radius: 6px;
            margin-bottom: 8px;
            cursor: pointer;
            transition: all 0.2s ease;
        }
        
        .run-item:hover {
            background: #f8f9fa;
            border-color: #007bff;
        }
        
        .run-item.selected {
            background: #007bff;
            color: white;
            border-color: #007bff;
        }
        
        .triggering-class {
            background: linear-gradient(135deg, #28a745, #20c997);
            color: white;
            padding: 3px 8px;
            border-radius: 4px;
            font-size: 11px;
            font-weight: 600;
            margin-bottom: 6px;
            display: inline-block;
        }
        
        .history-content {
            flex: 1;
            overflow-y: auto;
            padding: 15px;
        }
        
        .transition-item {
            background: white;
            border: 1px solid #dee2e6;
            margin-bottom: 15px;
            border-radius: 8px;
            overflow: hidden;
            box-shadow: 0 2px 8px rgba(0,0,0,0.05);
        }
        
        .transition-header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 12px 15px;
            font-weight: 600;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        
        .transition-body {
            padding: 15px;
        }
        
        .state-flow {
            display: flex;
            align-items: center;
            gap: 15px;
            margin-bottom: 15px;
            padding: 15px;
            background: #f8f9fa;
            border-radius: 6px;
        }
        
        .state-badge {
            padding: 8px 16px;
            background: white;
            border: 2px solid #007bff;
            border-radius: 20px;
            font-weight: 600;
            color: #007bff;
        }
        
        .arrow {
            color: #6c757d;
            font-size: 20px;
        }
        
        .event-info {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 15px;
            margin-top: 15px;
        }
        
        .info-item {
            padding: 10px;
            background: #f8f9fa;
            border-radius: 6px;
        }
        
        .info-label {
            font-size: 12px;
            color: #6c757d;
            margin-bottom: 5px;
        }
        
        .info-value {
            font-weight: 600;
            color: #495057;
        }
        
        .json-display {
            background: #f8f9fa;
            border: 1px solid #e9ecef;
            border-radius: 4px;
            padding: 12px;
            font-family: 'Courier New', monospace;
            font-size: 12px;
            white-space: pre-wrap;
            word-wrap: break-word;
            max-height: 200px;
            overflow-y: auto;
            margin-top: 10px;
        }
        
        .empty-state {
            text-align: center;
            color: #6c757d;
            padding: 40px;
        }
        
        .refresh-btn {
            background: #17a2b8;
            color: white;
            border: none;
            padding: 5px 12px;
            border-radius: 4px;
            cursor: pointer;
            font-size: 12px;
        }
        
        .refresh-btn:hover {
            background: #138496;
        }
        
        .loading {
            text-align: center;
            padding: 20px;
            color: #007bff;
        }
        
        /* Live mode styles */
        .event-panel {
            background: #f8f9fa;
            border: 1px solid #dee2e6;
            border-radius: 6px;
            padding: 12px;
            margin: 10px;
            display: none;
        }
        
        .event-select {
            width: 100%;
            padding: 8px;
            border: 1px solid #dee2e6;
            border-radius: 4px;
            margin-bottom: 8px;
            font-size: 13px;
        }
        
        .event-payload {
            width: 100%;
            min-height: 100px;
            padding: 8px;
            border: 1px solid #dee2e6;
            border-radius: 4px;
            font-family: monospace;
            font-size: 12px;
            resize: vertical;
            margin-bottom: 8px;
        }
        
        .send-event-btn {
            width: 100%;
            padding: 8px;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            font-weight: 600;
            transition: opacity 0.2s;
        }
        
        .send-event-btn:hover {
            opacity: 0.9;
        }
        
        .send-event-btn:disabled {
            opacity: 0.5;
            cursor: not-allowed;
        }
        
        .connection-status {
            display: inline-flex;
            align-items: center;
            gap: 5px;
            padding: 4px 8px;
            background: rgba(255,255,255,0.1);
            border-radius: 4px;
            font-size: 12px;
            display: none;
        }
        
        .status-dot {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            animation: pulse 2s infinite;
        }
        
        .status-dot.connected {
            background: #28a745;
        }
        
        .status-dot.disconnected {
            background: #dc3545;
            animation: none;
        }
        
        @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.5; }
        }
        
        /* Toast notifications */
        .toast-container {
            position: fixed;
            bottom: 20px;
            right: 20px;
            z-index: 1000;
        }
        
        .toast {
            padding: 12px 16px;
            background: #333;
            color: white;
            border-radius: 4px;
            margin-bottom: 8px;
            animation: slideIn 0.3s ease-out;
            box-shadow: 0 4px 6px rgba(0,0,0,0.1);
        }
        
        @keyframes slideIn {
            from {
                transform: translateX(100%);
                opacity: 0;
            }
            to {
                transform: translateX(0);
                opacity: 1;
            }
        }
        
        .toast.error {
            background: #dc3545;
        }
        
        .toast.success {
            background: #28a745;
        }
        
        .toast.info {
            background: #17a2b8;
        }
    </style>
</head>
<body>
    <div class="header">
        <div class="header-left">
            <h1>📊 TelcoBright State Machine Monitoring</h1>
            <p>Real-time monitoring of CallMachine and SmsMachine state transitions</p>
        </div>
        <div class="header-right">
            <div class="db-status" style="color: %s">
                <span>%s</span>
                <span>%s</span>
            </div>
            <div class="connection-status" id="connectionStatus">
                <span class="status-dot disconnected" id="statusDot"></span>
                <span id="statusText">Disconnected</span>
            </div>
            <div class="mode-buttons">
                <button class="mode-btn active" onclick="setMode('snapshot')">📸 Snapshot Viewer</button>
                <button class="mode-btn" onclick="setMode('live')">🔴 Live Viewer</button>
            </div>
        </div>
    </div>
    
    <div class="container">
        <div class="left-panel">
            <div class="panel-header">
                <span id="leftPanelTitle">📋 Recent Runs</span>
                <button class="refresh-btn" onclick="refreshData()">Refresh</button>
            </div>
            <!-- Event Panel for Live Mode -->
            <div class="event-panel" id="eventPanel">
                <h4 style="margin: 0 0 8px 0; font-size: 14px; color: #495057;">Send Event to Machine</h4>
                <select class="event-select" id="eventSelect" onchange="selectEvent()">
                    <option value="">Select Event...</option>
                    <option value="INCOMING_CALL">INCOMING_CALL</option>
                    <option value="ANSWER">ANSWER</option>
                    <option value="HANGUP">HANGUP</option>
                    <option value="SESSION_PROGRESS">SESSION_PROGRESS</option>
                </select>
                <textarea class="event-payload" id="eventPayload" placeholder="Event payload (JSON)"></textarea>
                <button class="send-event-btn" id="sendEventBtn" onclick="sendEvent()" disabled>Send Event</button>
            </div>
            <div class="run-list" id="runList">
                <div class="loading">Loading state machine runs...</div>
            </div>
        </div>
        
        <div class="right-panel">
            <div class="panel-header" id="historyHeader">
                📊 State Machine History
            </div>
            <div class="history-content" id="historyContent">
                <div class="empty-state">
                    <h3>Welcome to State Machine Monitoring</h3>
                    <p>Select a run from the left panel to view its state transition history</p>
                    <p style="margin-top: 20px;">
                        <strong>Features:</strong><br>
                        • View complete state transition history<br>
                        • Inspect event payloads and context<br>
                        • Track registry status for each transition<br>
                        • Monitor transition durations
                    </p>
                </div>
            </div>
        </div>
    </div>
    
    <div class="toast-container" id="toastContainer"></div>

    <script>
        let currentPage = 0;
        let selectedRunId = null;
        let currentMode = 'snapshot';
        let ws = null;
        let totalEventCount = 0;
        let stateChangeCount = 0;
        
        // Event templates for live mode
        const eventTemplates = {
            'INCOMING_CALL': { callerNumber: '+1-555-9999' },
            'ANSWER': {},
            'HANGUP': { reason: 'normal' },
            'SESSION_PROGRESS': { sdp: 'v=0', ringNumber: 1 }
        };
        
        window.onload = function() {
            loadRuns();
        };
        
        function loadRuns() {
            fetch('/api/runs?page=' + currentPage)
                .then(response => response.json())
                .then(runs => {
                    const runList = document.getElementById('runList');
                    runList.innerHTML = '';
                    
                    if (runs.length === 0) {
                        runList.innerHTML = '<div class="empty-state"><p>No runs found. Run a state machine demo to see data.</p></div>';
                        return;
                    }
                    
                    runs.forEach(run => {
                        const runItem = createRunItem(run);
                        runList.appendChild(runItem);
                    });
                })
                .catch(error => {
                    console.error('Error loading runs:', error);
                    document.getElementById('runList').innerHTML = 
                        '<div class="empty-state"><p>Error loading runs. Check console for details.</p></div>';
                });
        }
        
        function createRunItem(run) {
            const div = document.createElement('div');
            div.className = 'run-item';
            div.onclick = () => selectRun(run.runId, div);
            
            const classNameDisplay = run.triggeringClassName ? 
                `<div class="triggering-class">🚀 ${run.triggeringClassName}</div>` : '';
            
            div.innerHTML = `
                ${classNameDisplay}
                <div style="font-weight: 600; font-size: 13px; margin-bottom: 4px;">${run.runId}</div>
                <div style="font-size: 12px; color: #6c757d;">
                    🏷️ ${run.machineId} (${run.machineType})<br>
                    🔄 ${run.transitionCount} transitions<br>
                    ⏰ ${new Date(run.latestTimestamp).toLocaleString()}
                </div>
            `;
            
            return div;
        }
        
        function selectRun(runId, element) {
            document.querySelectorAll('.run-item').forEach(item => {
                item.classList.remove('selected');
            });
            
            element.classList.add('selected');
            selectedRunId = runId;
            
            document.getElementById('historyHeader').innerHTML = `📊 History: ${runId}`;
            loadHistory(runId);
        }
        
        function loadHistory(runId) {
            document.getElementById('historyContent').innerHTML = '<div class="loading">Loading state transitions...</div>';
            
            fetch('/api/history?runId=' + encodeURIComponent(runId))
                .then(response => response.json())
                .then(history => {
                    displayHistory(history);
                })
                .catch(error => {
                    console.error('Error loading history:', error);
                    document.getElementById('historyContent').innerHTML = 
                        '<div class="empty-state"><p>Error loading history. Check console for details.</p></div>';
                });
        }
        
        function displayHistory(history) {
            const content = document.getElementById('historyContent');
            content.innerHTML = '';
            
            if (history.length === 0) {
                content.innerHTML = '<div class="empty-state"><p>No transitions found for this run.</p></div>';
                return;
            }
            
            history.forEach((transition, index) => {
                const div = document.createElement('div');
                div.className = 'transition-item';
                
                div.innerHTML = `
                    <div class="transition-header">
                        <span>Transition ${transition.version}: ${transition.eventType}</span>
                        <span style="font-size: 12px;">${new Date(transition.timestamp).toLocaleString()}</span>
                    </div>
                    <div class="transition-body">
                        <div class="state-flow">
                            <div class="state-badge">${transition.stateBefore}</div>
                            <div class="arrow">→</div>
                            <div class="state-badge">${transition.stateAfter}</div>
                        </div>
                        <div class="event-info">
                            <div class="info-item">
                                <div class="info-label">Event Type</div>
                                <div class="info-value">${transition.eventType}</div>
                            </div>
                            <div class="info-item">
                                <div class="info-label">Duration</div>
                                <div class="info-value">${transition.transitionDuration}ms</div>
                            </div>
                            <div class="info-item">
                                <div class="info-label">Registry Status</div>
                                <div class="info-value">${transition.registryStatus || 'N/A'}</div>
                            </div>
                        </div>
                        ${transition.eventPayloadJson ? `
                            <div class="json-display">
                                <strong>Event Payload:</strong><br>
                                ${formatJson(transition.eventPayloadJson)}
                            </div>
                        ` : ''}
                    </div>
                `;
                
                content.appendChild(div);
            });
        }
        
        function formatJson(jsonString) {
            if (!jsonString) return 'null';
            try {
                const parsed = JSON.parse(jsonString);
                return JSON.stringify(parsed, null, 2);
            } catch (e) {
                return jsonString;
            }
        }
        
        function refreshData() {
            if (currentMode === 'snapshot') {
                loadRuns();
                if (selectedRunId) {
                    loadHistory(selectedRunId);
                }
            } else {
                // In live mode, reconnect WebSocket
                if (ws) {
                    ws.close();
                }
                connectWebSocket();
            }
        }
        
        function setMode(mode) {
            currentMode = mode;
            
            // Update button states
            document.querySelectorAll('.mode-btn').forEach(btn => {
                btn.classList.remove('active');
                if (btn.textContent.includes(mode === 'snapshot' ? 'Snapshot' : 'Live')) {
                    btn.classList.add('active');
                }
            });
            
            // Update UI elements
            const connectionStatus = document.getElementById('connectionStatus');
            const eventPanel = document.getElementById('eventPanel');
            const leftPanelTitle = document.getElementById('leftPanelTitle');
            const historyHeader = document.getElementById('historyHeader');
            
            if (mode === 'live') {
                connectionStatus.style.display = 'flex';
                eventPanel.style.display = 'block';
                leftPanelTitle.textContent = '🔴 Live Monitoring';
                historyHeader.textContent = '📊 Live State Updates';
                connectWebSocket();
                startLiveMode();
            } else {
                connectionStatus.style.display = 'none';
                eventPanel.style.display = 'none';
                leftPanelTitle.textContent = '📋 Recent Runs';
                historyHeader.textContent = '📊 State Machine History';
                
                if (ws) {
                    ws.close();
                    ws = null;
                }
                
                loadRuns();
            }
        }
        
        function connectWebSocket() {
            const wsUrl = 'ws://localhost:9999';
            
            try {
                ws = new WebSocket(wsUrl);
                
                ws.onopen = () => {
                    updateConnectionStatus(true);
                    document.getElementById('sendEventBtn').disabled = false;
                    showToast('Connected to live machine', 'success');
                    ws.send(JSON.stringify({ action: 'GET_STATE' }));
                };
                
                ws.onmessage = (event) => {
                    try {
                        const data = JSON.parse(event.data);
                        handleLiveMessage(data);
                    } catch (e) {
                        console.error('Error parsing message:', e);
                    }
                };
                
                ws.onerror = (error) => {
                    showToast('WebSocket connection error', 'error');
                };
                
                ws.onclose = () => {
                    updateConnectionStatus(false);
                    document.getElementById('sendEventBtn').disabled = true;
                    showToast('Disconnected from server. Retrying...', 'error');
                    
                    // Retry connection after 3 seconds if still in live mode
                    if (currentMode === 'live') {
                        setTimeout(connectWebSocket, 3000);
                    }
                };
            } catch (e) {
                showToast('Failed to connect to WebSocket server', 'error');
                if (currentMode === 'live') {
                    setTimeout(connectWebSocket, 3000);
                }
            }
        }
        
        function updateConnectionStatus(connected) {
            const dot = document.getElementById('statusDot');
            const text = document.getElementById('statusText');
            
            if (connected) {
                dot.classList.remove('disconnected');
                dot.classList.add('connected');
                text.textContent = 'Connected';
            } else {
                dot.classList.remove('connected');
                dot.classList.add('disconnected');
                text.textContent = 'Disconnected';
            }
        }
        
        function handleLiveMessage(data) {
            switch (data.type) {
                case 'CURRENT_STATE':
                case 'PERIODIC_UPDATE':
                    updateLiveDisplay(data);
                    break;
                case 'STATE_CHANGE':
                    stateChangeCount++;
                    updateLiveDisplay(data);
                    addLiveTransition(data);
                    break;
            }
            totalEventCount++;
        }
        
        function updateLiveDisplay(data) {
            const content = document.getElementById('historyContent');
            
            // Create or update live display
            let liveDisplay = document.getElementById('liveDisplay');
            if (!liveDisplay) {
                content.innerHTML = `
                    <div id="liveDisplay">
                        <div class="transition-item" style="margin-bottom: 20px;">
                            <div class="transition-header">
                                🔴 Live State Information
                            </div>
                            <div class="transition-body">
                                <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px;">
                                    <div class="info-item">
                                        <div class="info-label">Machine ID</div>
                                        <div class="info-value" id="liveMachineId">-</div>
                                    </div>
                                    <div class="info-item">
                                        <div class="info-label">Current State</div>
                                        <div class="info-value" id="liveCurrentState" style="font-size: 18px; font-weight: bold; color: #007bff;">-</div>
                                    </div>
                                    <div class="info-item">
                                        <div class="info-label">Registry Status</div>
                                        <div class="info-value" id="liveRegistryStatus">-</div>
                                    </div>
                                    <div class="info-item">
                                        <div class="info-label">Total Events</div>
                                        <div class="info-value" id="liveTotalEvents">0</div>
                                    </div>
                                    <div class="info-item">
                                        <div class="info-label">State Changes</div>
                                        <div class="info-value" id="liveStateChanges">0</div>
                                    </div>
                                    <div class="info-item">
                                        <div class="info-label">Last Update</div>
                                        <div class="info-value" id="liveLastUpdate">-</div>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <h3 style="margin: 20px 0 10px 0;">Recent Transitions</h3>
                        <div id="liveTransitions"></div>
                    </div>
                `;
                liveDisplay = document.getElementById('liveDisplay');
            }
            
            // Update live stats
            if (data.machineId) {
                document.getElementById('liveMachineId').textContent = data.machineId;
            }
            if (data.currentState) {
                document.getElementById('liveCurrentState').textContent = data.currentState;
            }
            if (data.isRegistered !== undefined) {
                document.getElementById('liveRegistryStatus').textContent = 
                    data.isRegistered ? '✅ REGISTERED' : '❌ NOT REGISTERED';
            }
            document.getElementById('liveTotalEvents').textContent = totalEventCount;
            document.getElementById('liveStateChanges').textContent = stateChangeCount;
            document.getElementById('liveLastUpdate').textContent = new Date().toLocaleTimeString();
        }
        
        function addLiveTransition(data) {
            const transitionsDiv = document.getElementById('liveTransitions');
            if (!transitionsDiv) return;
            
            const transitionItem = document.createElement('div');
            transitionItem.className = 'transition-item';
            transitionItem.style.animation = 'slideIn 0.3s ease-out';
            
            const timestamp = new Date().toLocaleTimeString();
            
            transitionItem.innerHTML = `
                <div class="transition-header">
                    ${data.oldState || 'UNKNOWN'} → ${data.newState || data.currentState}
                    <span style="font-size: 12px;">${timestamp}</span>
                </div>
                <div class="transition-body">
                    ${data.context ? `
                        <div class="json-display">
                            <strong>Context:</strong><br>
                            <pre style="margin: 0;">${JSON.stringify(data.context, null, 2)}</pre>
                        </div>
                    ` : '<p>No context data</p>'}
                </div>
            `;
            
            transitionsDiv.insertBefore(transitionItem, transitionsDiv.firstChild);
            
            // Keep only last 10 transitions
            while (transitionsDiv.children.length > 10) {
                transitionsDiv.removeChild(transitionsDiv.lastChild);
            }
        }
        
        function startLiveMode() {
            // Clear previous content
            const runList = document.getElementById('runList');
            runList.innerHTML = '<div style="padding: 20px; color: #28a745; text-align: center;">🔴 Live mode active<br><small>State changes will appear in real-time</small></div>';
            
            // Clear history content
            const historyContent = document.getElementById('historyContent');
            historyContent.innerHTML = '<div class="loading">Waiting for live data...</div>';
        }
        
        function selectEvent() {
            const event = document.getElementById('eventSelect').value;
            if (event && eventTemplates[event]) {
                document.getElementById('eventPayload').value = 
                    JSON.stringify(eventTemplates[event], null, 2);
            }
        }
        
        function sendEvent() {
            if (!ws || ws.readyState !== WebSocket.OPEN) {
                showToast('Not connected to server', 'error');
                return;
            }
            
            const event = document.getElementById('eventSelect').value;
            if (!event) {
                showToast('Please select an event', 'error');
                return;
            }
            
            try {
                const payload = JSON.parse(document.getElementById('eventPayload').value || '{}');
                ws.send(JSON.stringify({ action: event, payload }));
                showToast(`Sent event: ${event}`, 'success');
                
                // Clear form
                document.getElementById('eventSelect').value = '';
                document.getElementById('eventPayload').value = '';
            } catch (e) {
                showToast('Invalid JSON payload', 'error');
            }
        }
        
        function showToast(message, type = 'info') {
            const container = document.getElementById('toastContainer');
            const toast = document.createElement('div');
            toast.className = `toast ${type}`;
            toast.textContent = message;
            container.appendChild(toast);
            
            setTimeout(() => {
                toast.style.animation = 'slideIn 0.3s ease-out reverse';
                setTimeout(() => toast.remove(), 300);
            }, 3000);
        }
    </script>
</body>
</html>
        """, dbStatusColor, dbStatusIcon, dbStatusText);
    }
    
    // Data classes
    static class RunInfo {
        String runId;
        String machineId;
        String machineType;
        String latestTimestamp;
        int transitionCount;
        String triggeringClassName;
    }
    
    static class StateTransition {
        long version;
        String timestamp;
        String stateBefore;
        String stateAfter;
        String eventType;
        long transitionDuration;
        String registryStatus;
        String eventPayloadJson;
        String contextBeforeJson;
        String contextAfterJson;
    }
    
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}