package com.telcobright.statemachine.monitoring.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple HTTP server for State Machine Monitoring
 * 
 * Features:
 * - Left panel: Latest run IDs with infinite pagination (20 per page)
 * - Right panel: Full state machine history for selected run ID
 * - Automatic data refresh from PostgreSQL
 */
public class StateMachineMonitoringServer {
    
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/statemachine_monitoring";
    private static final String DB_USER = "statemachine";
    private static final String DB_PASSWORD = "monitoring123";
    
    private HttpServer server;
    private Connection dbConnection;
    
    public static void main(String[] args) throws Exception {
        StateMachineMonitoringServer monitor = new StateMachineMonitoringServer();
        monitor.start(8090);
        
        System.out.println("🚀 State Machine Monitoring Server Started!");
        System.out.println("📊 Open your browser and go to: http://localhost:8090");
        System.out.println("💡 This will show your state machine runs automatically");
        System.out.println("🔄 Machines running with debug=true will appear here instantly");
        
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
    
    private void initDatabase() throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
            dbConnection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            System.out.println("✅ Connected to monitoring database");
        } catch (ClassNotFoundException e) {
            throw new SQLException("PostgreSQL driver not found. Add postgresql dependency.", e);
        }
    }
    
    /**
     * Main page handler - serves the HTML interface
     */
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
    
    /**
     * API handler for fetching run IDs with pagination
     */
    class RunsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String query = exchange.getRequestURI().getQuery();
                int page = 0;
                if (query != null && query.startsWith("page=")) {
                    page = Integer.parseInt(query.split("=")[1]);
                }
                
                List<RunInfo> runs = fetchRuns(page);
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
    
    /**
     * API handler for fetching full state machine history
     */
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
                
                List<StateTransition> history = fetchHistory(runId);
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
    
    /**
     * Fetch run IDs with pagination
     */
    private List<RunInfo> fetchRuns(int page) throws SQLException {
        String sql = """
            SELECT DISTINCT 
                run_id, 
                machine_id, 
                machine_type,
                MAX(timestamp) as latest_timestamp,
                COUNT(*) as transition_count,
                MAX(triggering_class_name) as triggering_class_name,
                MAX(triggering_class_full_path) as triggering_class_full_path
            FROM state_machine_snapshots 
            GROUP BY run_id, machine_id, machine_type
            ORDER BY latest_timestamp DESC 
            LIMIT 20 OFFSET ?
        """;
        
        List<RunInfo> runs = new ArrayList<>();
        
        try (PreparedStatement stmt = dbConnection.prepareStatement(sql)) {
            stmt.setInt(1, page * 20);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                RunInfo run = new RunInfo();
                run.runId = rs.getString("run_id");
                run.machineId = rs.getString("machine_id");
                run.machineType = rs.getString("machine_type");
                run.latestTimestamp = rs.getTimestamp("latest_timestamp").toString();
                run.transitionCount = rs.getInt("transition_count");
                run.triggeringClassName = rs.getString("triggering_class_name");
                run.triggeringClassFullPath = rs.getString("triggering_class_full_path");
                runs.add(run);
            }
        }
        
        return runs;
    }
    
    /**
     * Fetch full state machine history for a run ID
     */
    private List<StateTransition> fetchHistory(String runId) throws SQLException {
        String sql = """
            SELECT 
                version,
                timestamp,
                state_before,
                state_after,
                event_type,
                transition_duration,
                registry_status,
                machine_online_status,
                event_payload_json,
                context_before_json,
                context_after_json,
                registry_status_before,
                registry_status_after,
                machine_hydrated_before,
                machine_hydrated_after,
                event_sent_through_registry
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
                transition.registryStatus = rs.getString("registry_status");
                transition.machineOnline = rs.getBoolean("machine_online_status");
                
                // Add detailed context and payload information
                transition.eventPayloadJson = rs.getString("event_payload_json");
                transition.contextBeforeJson = rs.getString("context_before_json");
                transition.contextAfterJson = rs.getString("context_after_json");
                
                // Add registry status tracking
                transition.registryStatusBefore = rs.getString("registry_status_before");
                transition.registryStatusAfter = rs.getString("registry_status_after");
                transition.machineHydratedBefore = rs.getBoolean("machine_hydrated_before");
                transition.machineHydratedAfter = rs.getBoolean("machine_hydrated_after");
                transition.eventSentThroughRegistry = rs.getBoolean("event_sent_through_registry");
                
                history.add(transition);
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
                .append("\"triggeringClassName\":").append(run.triggeringClassName != null ? "\"" + escapeJson(run.triggeringClassName) + "\"" : "null").append(",")
                .append("\"triggeringClassFullPath\":").append(run.triggeringClassFullPath != null ? "\"" + escapeJson(run.triggeringClassFullPath) + "\"" : "null")
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
                .append("\"machineOnline\":").append(t.machineOnline).append(",")
                .append("\"eventPayloadJson\":").append(t.eventPayloadJson != null ? "\"" + escapeJson(decodeBase64Safe(t.eventPayloadJson)) + "\"" : "null").append(",")
                .append("\"contextBeforeJson\":").append(t.contextBeforeJson != null ? "\"" + escapeJson(decodeBase64Safe(t.contextBeforeJson)) + "\"" : "null").append(",")
                .append("\"contextAfterJson\":").append(t.contextAfterJson != null ? "\"" + escapeJson(decodeBase64Safe(t.contextAfterJson)) + "\"" : "null").append(",")
                .append("\"registryStatusBefore\":\"").append(escapeJson(t.registryStatusBefore)).append("\",")
                .append("\"registryStatusAfter\":\"").append(escapeJson(t.registryStatusAfter)).append("\",")
                .append("\"machineHydratedBefore\":").append(t.machineHydratedBefore).append(",")
                .append("\"machineHydratedAfter\":").append(t.machineHydratedAfter).append(",")
                .append("\"eventSentThroughRegistry\":").append(t.eventSentThroughRegistry)
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
    
    private String decodeBase64Safe(String base64) {
        if (base64 == null || base64.trim().isEmpty()) {
            return "";
        }
        try {
            return new String(java.util.Base64.getDecoder().decode(base64));
        } catch (Exception e) {
            return base64; // Return original if not base64 encoded
        }
    }
    
    private String generateMainPage() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>State Machine Monitoring Dashboard</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f5f5f5; }
        
        .header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 20px;
            text-align: center;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        
        .container {
            display: flex;
            height: calc(100vh - 80px);
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
        }
        
        .panel-header {
            background: #f8f9fa;
            padding: 15px;
            border-bottom: 1px solid #dee2e6;
            border-radius: 8px 8px 0 0;
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
            padding: 15px;
            border: 1px solid #e9ecef;
            border-radius: 6px;
            margin-bottom: 8px;
            cursor: pointer;
            transition: all 0.2s ease;
            background: #fff;
        }
        
        .run-item:hover {
            background: #f8f9fa;
            border-color: #007bff;
            transform: translateY(-1px);
            box-shadow: 0 2px 8px rgba(0,123,255,0.15);
        }
        
        .run-item.selected {
            background: #007bff;
            color: white;
            border-color: #007bff;
        }
        
        .run-id { font-weight: 600; font-size: 14px; margin-bottom: 5px; }
        .run-meta { font-size: 12px; color: #6c757d; }
        .run-item.selected .run-meta { color: #b3d7ff; }
        
        .triggering-class {
            background: linear-gradient(135deg, #28a745, #20c997);
            color: white;
            padding: 4px 8px;
            border-radius: 4px;
            font-size: 11px;
            font-weight: 600;
            margin-bottom: 8px;
            display: inline-block;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }
        
        .class-full-path {
            font-size: 10px;
            color: #495057;
            font-family: 'Courier New', monospace;
            background: #f8f9fa;
            padding: 2px 6px;
            border-radius: 3px;
            margin-bottom: 6px;
            border-left: 2px solid #28a745;
            word-break: break-all;
        }
        
        .run-item.selected .class-full-path {
            background: rgba(255,255,255,0.2);
            color: #e3f2fd;
            border-left-color: #81c784;
        }
        
        .history-content {
            flex: 1;
            overflow-y: auto;
            padding: 20px;
        }
        
        .transition-item {
            background: #f8f9fa;
            border-left: 4px solid #007bff;
            padding: 20px;
            margin-bottom: 15px;
            border-radius: 0 8px 8px 0;
            position: relative;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
        }
        
        .transition-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 10px;
        }
        
        .version-badge {
            background: #007bff;
            color: white;
            padding: 4px 8px;
            border-radius: 12px;
            font-size: 11px;
            font-weight: 600;
        }
        
        .duration-badge {
            background: #28a745;
            color: white;
            padding: 4px 8px;
            border-radius: 12px;
            font-size: 11px;
        }
        
        .state-flow {
            font-size: 16px;
            font-weight: 600;
            color: #495057;
            margin: 8px 0;
        }
        
        .state-arrow { color: #007bff; margin: 0 8px; }
        
        .event-type {
            background: #ffc107;
            color: #212529;
            padding: 2px 8px;
            border-radius: 4px;
            font-size: 12px;
            font-weight: 500;
        }
        
        .timestamp { color: #6c757d; font-size: 12px; }
        
        .status-online { color: #28a745; font-weight: 600; }
        .status-offline { color: #dc3545; font-weight: 600; }
        
        .detail-section {
            background: white;
            border: 1px solid #dee2e6;
            border-radius: 6px;
            margin: 12px 0;
            overflow: hidden;
        }
        
        .detail-header {
            background: #e9ecef;
            padding: 10px 15px;
            font-weight: 600;
            color: #495057;
            cursor: pointer;
            display: flex;
            justify-content: space-between;
            align-items: center;
            transition: background-color 0.2s;
        }
        
        .detail-header:hover {
            background: #dee2e6;
        }
        
        .detail-content {
            padding: 15px;
            background: #fff;
            border-top: 1px solid #dee2e6;
            display: none;
        }
        
        .detail-content.expanded {
            display: block;
        }
        
        .json-content {
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
            line-height: 1.4;
        }
        
        .toggle-icon {
            transition: transform 0.2s;
            font-size: 12px;
        }
        
        .toggle-icon.expanded {
            transform: rotate(90deg);
        }
        
        .context-diff {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 10px;
            margin-top: 10px;
        }
        
        .context-before, .context-after {
            background: #f8f9fa;
            border: 1px solid #e9ecef;
            border-radius: 4px;
            padding: 12px;
        }
        
        .context-before {
            border-left: 3px solid #dc3545;
        }
        
        .context-after {
            border-left: 3px solid #28a745;
        }
        
        .context-label {
            font-weight: 600;
            margin-bottom: 8px;
            font-size: 12px;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }
        
        .context-before .context-label {
            color: #dc3545;
        }
        
        .context-after .context-label {
            color: #28a745;
        }
        
        .no-data {
            color: #6c757d;
            font-style: italic;
            text-align: center;
            padding: 20px;
        }
        
        .load-more-btn {
            background: #007bff;
            color: white;
            border: none;
            padding: 10px 20px;
            margin: 10px;
            border-radius: 6px;
            cursor: pointer;
            font-weight: 600;
        }
        
        .load-more-btn:hover { background: #0056b3; }
        
        .refresh-btn {
            background: #17a2b8;
            color: white;
            border: none;
            padding: 6px 12px;
            border-radius: 4px;
            cursor: pointer;
            font-weight: 500;
            font-size: 12px;
            transition: background-color 0.2s;
        }
        
        .refresh-btn:hover { background: #138496; }
        
        .empty-state {
            text-align: center;
            color: #6c757d;
            padding: 40px;
        }
        
        .loading {
            text-align: center;
            padding: 20px;
            color: #007bff;
        }
    </style>
</head>
<body>
    <div class="header">
        <h1>🔍 State Machine Monitoring Dashboard</h1>
        <p>Manual monitoring of your state machine executions</p>
    </div>
    
    <div class="container">
        <div class="left-panel">
            <div class="panel-header">
                <span>📋 Recent Runs <span id="totalRuns"></span></span>
                <button class="refresh-btn" onclick="refreshData()">Reload</button>
            </div>
            <div class="run-list" id="runList">
                <div class="loading">Loading runs...</div>
            </div>
            <button class="load-more-btn" id="loadMoreBtn" onclick="loadMoreRuns()" style="display:none;">
                Load More Runs
            </button>
        </div>
        
        <div class="right-panel">
            <div class="panel-header" id="historyHeader">
                📊 State Machine History
            </div>
            <div class="history-content" id="historyContent">
                <div class="empty-state">
                    <h3>Welcome to State Machine Monitoring!</h3>
                    <p>Select a run ID from the left panel to view its complete state transition history.</p>
                    <br>
                    <p><strong>How it works:</strong></p>
                    <ul style="text-align: left; display: inline-block; margin-top: 10px;">
                        <li>Run your state machines with <code>debug=true</code></li>
                        <li>Snapshots are automatically saved to the database</li>
                        <li>View complete execution history here in real-time</li>
                        <li>Click any run ID to see detailed state transitions</li>
                    </ul>
                </div>
            </div>
        </div>
    </div>

    <script>
        let currentPage = 0;
        let selectedRunId = null;
        
        // Load initial runs on page load
        window.onload = function() {
            loadRuns();
        };
        
        function loadRuns() {
            fetch('/api/runs?page=' + currentPage)
                .then(response => response.json())
                .then(runs => {
                    const runList = document.getElementById('runList');
                    
                    if (currentPage === 0) {
                        runList.innerHTML = '';
                    }
                    
                    if (runs.length === 0 && currentPage === 0) {
                        runList.innerHTML = '<div class="empty-state"><p>No state machine runs found yet.</p><p>Run a state machine with debug=true to see data here!</p></div>';
                        return;
                    }
                    
                    runs.forEach(run => {
                        const runItem = createRunItem(run);
                        runList.appendChild(runItem);
                    });
                    
                    // Show load more button if we got 20 results (possibly more available)
                    const loadMoreBtn = document.getElementById('loadMoreBtn');
                    if (runs.length === 20) {
                        loadMoreBtn.style.display = 'block';
                    } else {
                        loadMoreBtn.style.display = 'none';
                    }
                })
                .catch(error => {
                    console.error('Error loading runs:', error);
                    document.getElementById('runList').innerHTML = '<div class="empty-state"><p>Error loading runs: ' + error.message + '</p></div>';
                });
        }
        
        function loadMoreRuns() {
            currentPage++;
            loadRuns();
        }
        
        function createRunItem(run) {
            const div = document.createElement('div');
            div.className = 'run-item';
            div.onclick = () => selectRun(run.runId, div);
            
            // Create class name display
            const classNameDisplay = run.triggeringClassName ? 
                `<div class="triggering-class">🚀 ${run.triggeringClassName}</div>` : '';
            
            const classPathDisplay = run.triggeringClassFullPath ? 
                `<div class="class-full-path">${run.triggeringClassFullPath}</div>` : '';
            
            div.innerHTML = `
                ${classNameDisplay}
                ${classPathDisplay}
                <div class="run-id">${run.runId}</div>
                <div class="run-meta">
                    🏷️ ${run.machineId} (${run.machineType})<br>
                    🔄 ${run.transitionCount} transitions<br>
                    ⏰ ${new Date(run.latestTimestamp).toLocaleString()}
                </div>
            `;
            
            return div;
        }
        
        function selectRun(runId, element) {
            // Remove previous selection
            document.querySelectorAll('.run-item').forEach(item => {
                item.classList.remove('selected');
            });
            
            // Mark current selection
            element.classList.add('selected');
            selectedRunId = runId;
            
            // Update header
            document.getElementById('historyHeader').innerHTML = `📊 History: ${runId}`;
            
            // Load history
            loadHistory(runId);
        }
        
        function loadHistory(runId) {
            document.getElementById('historyContent').innerHTML = '<div class="loading">Loading history...</div>';
            
            fetch('/api/history?runId=' + encodeURIComponent(runId))
                .then(response => response.json())
                .then(history => {
                    displayHistory(history);
                })
                .catch(error => {
                    console.error('Error loading history:', error);
                    document.getElementById('historyContent').innerHTML = '<div class="empty-state"><p>Error loading history: ' + error.message + '</p></div>';
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
                
                const statusColor = transition.machineOnline ? 'status-online' : 'status-offline';
                const statusText = transition.machineOnline ? '🟢 ONLINE' : '🔴 OFFLINE';
                
                div.innerHTML = `
                    <div class="transition-header">
                        <span class="version-badge">Step ${transition.version}</span>
                        <span class="duration-badge">${transition.transitionDuration}ms</span>
                    </div>
                    
                    <div class="state-flow">
                        <span>${transition.stateBefore}</span>
                        <span class="state-arrow">→</span>
                        <span>${transition.stateAfter}</span>
                    </div>
                    
                    <div style="margin: 10px 0;">
                        <span class="event-type">${transition.eventType}</span>
                    </div>
                    
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 10px;">
                        <span class="timestamp">⏰ ${new Date(transition.timestamp).toLocaleString()}</span>
                        <span class="${statusColor}">${statusText}</span>
                    </div>
                    
                    <div style="margin-top: 10px; display: grid; grid-template-columns: 1fr 1fr; gap: 8px; font-size: 11px;">
                        <div style="background: #f8f9fa; padding: 6px; border-radius: 4px;">
                            <strong>Registry Before:</strong><br>
                            <span style="color: ${getRegistryColor(transition.registryStatusBefore)};">
                                ${transition.registryStatusBefore} ${transition.machineHydratedBefore ? '💧' : '🏜️'}
                            </span>
                        </div>
                        <div style="background: #f8f9fa; padding: 6px; border-radius: 4px;">
                            <strong>Registry After:</strong><br>
                            <span style="color: ${getRegistryColor(transition.registryStatusAfter)};">
                                ${transition.registryStatusAfter} ${transition.machineHydratedAfter ? '💧' : '🏜️'}
                            </span>
                        </div>
                    </div>
                    
                    ${transition.eventSentThroughRegistry ? 
                        '<div style="margin-top: 8px; font-size: 10px; color: #28a745; text-align: center;"><strong>📋 EVENT SENT THROUGH REGISTRY</strong></div>' : 
                        '<div style="margin-top: 8px; font-size: 10px; color: #dc3545; text-align: center;"><strong>⚠️ DIRECT EVENT (NOT THROUGH REGISTRY)</strong></div>'
                    }
                    
                    ${createDetailSections(transition)}
                `;
                
                content.appendChild(div);
            });
            
            // Add event listeners for toggle functionality
            document.querySelectorAll('.detail-header').forEach(header => {
                header.addEventListener('click', function() {
                    const content = this.nextElementSibling;
                    const icon = this.querySelector('.toggle-icon');
                    
                    if (content.classList.contains('expanded')) {
                        content.classList.remove('expanded');
                        icon.classList.remove('expanded');
                    } else {
                        content.classList.add('expanded');
                        icon.classList.add('expanded');
                    }
                });
            });
        }
        
        function createDetailSections(transition) {
            let sections = '';
            
            // Event Payload Section
            if (transition.eventPayloadJson) {
                sections += `
                    <div class="detail-section">
                        <div class="detail-header">
                            <span>📦 Event Payload</span>
                            <span class="toggle-icon">▶</span>
                        </div>
                        <div class="detail-content">
                            <div class="json-content">${formatJson(transition.eventPayloadJson)}</div>
                        </div>
                    </div>
                `;
            }
            
            // Context Before/After Section
            if (transition.contextBeforeJson || transition.contextAfterJson) {
                sections += `
                    <div class="detail-section">
                        <div class="detail-header">
                            <span>🔄 Context Changes</span>
                            <span class="toggle-icon">▶</span>
                        </div>
                        <div class="detail-content">
                            <div class="context-diff">
                                <div class="context-before">
                                    <div class="context-label">Before</div>
                                    ${transition.contextBeforeJson ? 
                                        `<div class="json-content">${formatJson(transition.contextBeforeJson)}</div>` :
                                        `<div class="no-data">No context data</div>`
                                    }
                                </div>
                                <div class="context-after">
                                    <div class="context-label">After</div>
                                    ${transition.contextAfterJson ? 
                                        `<div class="json-content">${formatJson(transition.contextAfterJson)}</div>` :
                                        `<div class="no-data">No context data</div>`
                                    }
                                </div>
                            </div>
                        </div>
                    </div>
                `;
            }
            
            return sections;
        }
        
        function formatJson(jsonString) {
            if (!jsonString || jsonString.trim() === '') {
                return '<div class="no-data">No data available</div>';
            }
            
            try {
                const parsed = JSON.parse(jsonString);
                return JSON.stringify(parsed, null, 2);
            } catch (e) {
                // If not valid JSON, display as-is with basic formatting
                return jsonString.replace(/&/g, '&amp;')
                                .replace(/</g, '&lt;')
                                .replace(/>/g, '&gt;')
                                .replace(/"/g, '&quot;');
            }
        }
        
        function getRegistryColor(status) {
            if (!status) return '#6c757d';
            
            switch(status.toUpperCase()) {
                case 'REGISTERED_ACTIVE':
                    return '#28a745';  // Green
                case 'REGISTERED_INACTIVE':
                    return '#ffc107';  // Yellow
                case 'NOT_REGISTERED':
                    return '#dc3545';  // Red
                default:
                    return '#17a2b8';  // Blue
            }
        }
        
        // Manual refresh function
        function refreshData() {
            // Reset pagination and reload runs
            currentPage = 0;
            loadRuns();
            
            // If a run is selected, refresh its history too
            if (selectedRunId) {
                loadHistory(selectedRunId);
            }
            
            console.log('Data refreshed manually');
        }
        
        // Auto-refresh disabled - user controls refresh manually
    </script>
</body>
</html>
        """;
    }
    
    // Data classes
    static class RunInfo {
        String runId;
        String machineId;
        String machineType;
        String latestTimestamp;
        int transitionCount;
        String triggeringClassName;
        String triggeringClassFullPath;
    }
    
    static class StateTransition {
        long version;
        String timestamp;
        String stateBefore;
        String stateAfter;
        String eventType;
        long transitionDuration;
        String registryStatus;
        boolean machineOnline;
        String eventPayloadJson;
        String contextBeforeJson;
        String contextAfterJson;
        String registryStatusBefore;
        String registryStatusAfter;
        boolean machineHydratedBefore;
        boolean machineHydratedAfter;
        boolean eventSentThroughRegistry;
    }
    
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}