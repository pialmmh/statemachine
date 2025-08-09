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
 * Enhanced State Machine Monitoring Server with Tabbed UI
 * 
 * Features:
 * - Multiple events per state transition
 * - Tabbed view: Event Details | Context Before | Context After
 * - Complete state machine context alongside registry status
 */
public class EnhancedMonitoringServer {
    
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/statemachine_monitoring";
    private static final String DB_USER = "statemachine";
    private static final String DB_PASSWORD = "monitoring123";
    
    private HttpServer server;
    private Connection dbConnection;
    
    public static void main(String[] args) throws Exception {
        EnhancedMonitoringServer monitor = new EnhancedMonitoringServer();
        monitor.start(8091);  // Using port 8091 for enhanced version
        
        System.out.println("🚀 Enhanced State Machine Monitoring Server Started!");
        System.out.println("📊 Open your browser and go to: http://localhost:8091");
        System.out.println("🎯 New tabbed interface with complete context view");
        System.out.println("📋 All events flow through StateMachineRegistry");
        
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
    
    class MainPageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = generateEnhancedMainPage();
            
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
                
                transition.eventPayloadJson = rs.getString("event_payload_json");
                transition.contextBeforeJson = rs.getString("context_before_json");
                transition.contextAfterJson = rs.getString("context_after_json");
                
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
            return base64;
        }
    }
    
    private String generateEnhancedMainPage() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Enhanced State Machine Monitoring</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f5f5f5; }
        
        .header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 15px 20px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        
        .header h1 { font-size: 24px; margin-bottom: 5px; }
        .header p { font-size: 14px; opacity: 0.9; }
        
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
        
        .class-full-path {
            font-size: 10px;
            color: #6c757d;
            font-family: monospace;
            margin-bottom: 6px;
        }
        
        .run-item.selected .class-full-path { color: #b3d7ff; }
        
        .history-content {
            flex: 1;
            overflow-y: auto;
            padding: 10px;
        }
        
        .transition-item {
            background: white;
            border: 1px solid #dee2e6;
            margin-bottom: 20px;
            border-radius: 8px;
            overflow: hidden;
            box-shadow: 0 2px 8px rgba(0,0,0,0.05);
        }
        
        .transition-table {
            width: 100%;
            border-collapse: collapse;
        }
        
        .transition-header-row {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
        }
        
        .transition-header-row td {
            padding: 12px;
            font-weight: 600;
            border-right: 1px solid rgba(255,255,255,0.2);
        }
        
        .transition-header-row td:last-child {
            border-right: none;
        }
        
        .step-info {
            width: 200px;
            font-size: 14px;
        }
        
        .column-header {
            text-align: center;
            font-size: 12px;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }
        
        .transition-state-row {
            background: #f8f9fa;
            border-bottom: 2px solid #dee2e6;
        }
        
        .transition-state-row td {
            padding: 15px 12px;
            border-right: 1px solid #e9ecef;
            vertical-align: top;
        }
        
        .state-transition {
            font-size: 16px;
            font-weight: 600;
            color: #495057;
        }
        
        .state-arrow {
            color: #007bff;
            margin: 0 8px;
        }
        
        .event-row {
            border-bottom: 1px solid #e9ecef;
            transition: background-color 0.2s;
            cursor: pointer;
        }
        
        .event-row:hover {
            background: #f8f9fa;
        }
        
        .event-row.selected {
            background: #e8f4fd;
        }
        
        .event-row td {
            padding: 10px 12px;
            border-right: 1px solid #e9ecef;
            vertical-align: top;
            font-size: 12px;
        }
        
        .event-row td:last-child {
            border-right: none;
        }
        
        .event-label {
            font-weight: 600;
            color: #007bff;
            display: flex;
            align-items: center;
            gap: 5px;
        }
        
        .event-duration {
            font-size: 10px;
            color: #6c757d;
            margin-top: 2px;
        }
        
        .detail-cell {
            max-height: 150px;
            overflow-y: auto;
            font-family: 'Courier New', monospace;
            font-size: 11px;
            line-height: 1.4;
            word-break: break-word;
        }
        
        .context-section {
            background: #f8f9fa;
            border: 1px solid #e9ecef;
            border-radius: 4px;
            padding: 8px;
            margin-bottom: 8px;
        }
        
        .registry-status-section {
            background: white;
            border: 1px solid #dee2e6;
            border-radius: 4px;
            padding: 8px;
        }
        
        .status-item {
            display: flex;
            justify-content: space-between;
            padding: 4px 0;
            font-size: 11px;
        }
        
        .status-label {
            color: #6c757d;
        }
        
        .status-value {
            font-weight: 600;
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
            line-height: 1.5;
        }
        
        .registry-status-box {
            background: white;
            border: 1px solid #dee2e6;
            border-radius: 6px;
            padding: 12px;
            margin-bottom: 15px;
        }
        
        .registry-status-header {
            font-weight: 600;
            margin-bottom: 8px;
            color: #495057;
        }
        
        .registry-status-item {
            display: flex;
            justify-content: space-between;
            padding: 6px 0;
            border-bottom: 1px solid #f1f3f5;
        }
        
        .registry-status-item:last-child { border-bottom: none; }
        
        .status-label { color: #6c757d; font-size: 12px; }
        
        .status-value {
            font-weight: 600;
            font-size: 12px;
        }
        
        .status-active { color: #28a745; }
        .status-inactive { color: #ffc107; }
        .status-not-registered { color: #dc3545; }
        
        .machine-context-box {
            margin-top: 15px;
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
        <h1>🔍 Enhanced State Machine Monitoring</h1>
        <p>Registry-mediated event flow with complete context tracking</p>
    </div>
    
    <div class="container">
        <div class="left-panel">
            <div class="panel-header">
                <span>📋 Recent Runs</span>
                <button class="refresh-btn" onclick="refreshData()">Reload</button>
            </div>
            <div class="run-list" id="runList">
                <div class="loading">Loading runs...</div>
            </div>
        </div>
        
        <div class="right-panel">
            <div class="panel-header" id="historyHeader">
                📊 State Machine History
            </div>
            <div class="history-content" id="historyContent">
                <div class="empty-state">
                    <h3>Welcome to Enhanced Monitoring!</h3>
                    <p>Select a run from the left to see complete state transitions with:</p>
                    <ul style="text-align: left; display: inline-block; margin-top: 10px;">
                        <li>Multiple events per state</li>
                        <li>Complete event payloads</li>
                        <li>Full context before/after</li>
                        <li>Registry status tracking</li>
                        <li>Machine hydration states</li>
                    </ul>
                </div>
            </div>
        </div>
    </div>

    <script>
        let currentPage = 0;
        let selectedRunId = null;
        let historyData = [];
        
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
                        runList.innerHTML = '<div class="empty-state"><p>No runs found.</p></div>';
                        return;
                    }
                    
                    runs.forEach(run => {
                        const runItem = createRunItem(run);
                        runList.appendChild(runItem);
                    });
                })
                .catch(error => {
                    console.error('Error loading runs:', error);
                });
        }
        
        function createRunItem(run) {
            const div = document.createElement('div');
            div.className = 'run-item';
            div.onclick = () => selectRun(run.runId, div);
            
            const classNameDisplay = run.triggeringClassName ? 
                `<div class="triggering-class">🚀 ${run.triggeringClassName}</div>` : '';
            
            const classPathDisplay = run.triggeringClassFullPath ? 
                `<div class="class-full-path">${run.triggeringClassFullPath}</div>` : '';
            
            div.innerHTML = `
                ${classNameDisplay}
                ${classPathDisplay}
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
            document.getElementById('historyContent').innerHTML = '<div class="loading">Loading history...</div>';
            
            fetch('/api/history?runId=' + encodeURIComponent(runId))
                .then(response => response.json())
                .then(history => {
                    historyData = history;
                    displayEnhancedHistory(history);
                })
                .catch(error => {
                    console.error('Error loading history:', error);
                    document.getElementById('historyContent').innerHTML = '<div class="empty-state"><p>Error loading history.</p></div>';
                });
        }
        
        function displayEnhancedHistory(history) {
            const content = document.getElementById('historyContent');
            content.innerHTML = '';
            
            if (history.length === 0) {
                content.innerHTML = '<div class="empty-state"><p>No transitions found.</p></div>';
                return;
            }
            
            // Each database record represents one complete state transition
            history.forEach((event, index) => {
                const div = document.createElement('div');
                div.className = 'transition-item';
                
                // Create table structure for this single transition
                div.innerHTML = `
                    <table class="transition-table">
                        <!-- Header Row -->
                        <tr class="transition-header-row">
                            <td class="step-info">Step ${event.version}</td>
                            <td class="column-header">Event Details</td>
                            <td class="column-header">Context Before<br><small>(Registry + Machine)</small></td>
                            <td class="column-header">Context After<br><small>(Registry + Machine)</small></td>
                        </tr>
                        
                        <!-- State Transition Row -->
                        <tr class="transition-state-row">
                            <td>
                                <div class="state-transition">
                                    ${event.stateBefore}
                                    <span class="state-arrow">→</span>
                                    ${event.stateAfter}
                                </div>
                            </td>
                            <td colspan="3" style="text-align: center; color: #6c757d; font-size: 11px;">
                                Event: ${event.eventType} (${event.transitionDuration}ms)
                            </td>
                        </tr>
                        
                        <!-- Event Data Row -->
                        <tr class="event-row selected" 
                            onclick="selectEventRow(this, ${index}, 0)"
                            data-transition="${index}"
                            data-event="0">
                            <td>
                                <div class="event-label">
                                    📌 Event: ${event.eventType}
                                </div>
                                <div class="event-duration">${event.transitionDuration}ms</div>
                            </td>
                            <td class="detail-cell event-detail">
                                ${formatEventDetail(event.eventPayloadJson)}
                            </td>
                            <td class="detail-cell context-before">
                                ${formatContextBefore(event.contextBeforeJson, event)}
                            </td>
                            <td class="detail-cell context-after">
                                ${formatContextAfter(event.contextAfterJson, event)}
                            </td>
                        </tr>
                    </table>
                `;
                
                content.appendChild(div);
                
                // Store event data
                div.eventData = event;
            });
        }
        
        
        function renderEventDetails(event, transitionId) {
            return `
                <div class="tab-content active" data-tab="event">
                    <div class="json-display">${formatJson(event.eventPayloadJson)}</div>
                </div>
                
                <div class="tab-content" data-tab="context-before">
                    <div class="machine-context-box">
                        <div class="registry-status-header">🔧 Machine Context</div>
                        <div class="json-display">${formatJson(event.contextBeforeJson)}</div>
                    </div>
                </div>
                
                <div class="tab-content" data-tab="context-after">
                    <div class="machine-context-box">
                        <div class="registry-status-header">🔧 Machine Context</div>
                        <div class="json-display">${formatJson(event.contextAfterJson)}</div>
                    </div>
                </div>
                
                <div class="tab-content" data-tab="registry-before">
                    <div class="registry-status-box">
                        <div class="registry-status-header">📋 Registry Status Before</div>
                        <div class="registry-status-item">
                            <span class="status-label">Status:</span>
                            <span class="status-value ${getStatusClass(event.registryStatusBefore)}">
                                ${event.registryStatusBefore}
                            </span>
                        </div>
                        <div class="registry-status-item">
                            <span class="status-label">Machine Hydrated:</span>
                            <span class="status-value">
                                ${event.machineHydratedBefore ? '💧 Yes' : '🏜️ No'}
                            </span>
                        </div>
                        <div class="registry-status-item">
                            <span class="status-label">Event Through Registry:</span>
                            <span class="status-value">
                                ${event.eventSentThroughRegistry ? '✅ Yes' : '❌ No'}
                            </span>
                        </div>
                    </div>
                </div>
                
                <div class="tab-content" data-tab="registry-after">
                    <div class="registry-status-box">
                        <div class="registry-status-header">📋 Registry Status After</div>
                        <div class="registry-status-item">
                            <span class="status-label">Status:</span>
                            <span class="status-value ${getStatusClass(event.registryStatusAfter)}">
                                ${event.registryStatusAfter}
                            </span>
                        </div>
                        <div class="registry-status-item">
                            <span class="status-label">Machine Hydrated:</span>
                            <span class="status-value">
                                ${event.machineHydratedAfter ? '💧 Yes' : '🏜️ No'}
                            </span>
                        </div>
                        <div class="registry-status-item">
                            <span class="status-label">Machine Online:</span>
                            <span class="status-value">
                                ${event.machineOnline ? '🟢 Online' : '🔴 Offline'}
                            </span>
                        </div>
                    </div>
                </div>
            `;
        }
        
        function selectEventRow(row, transitionIndex, eventIndex) {
            // Get the transition container
            const table = row.closest('table');
            const transition = table.closest('.transition-item').transitionData;
            const event = transition.events[eventIndex];
            
            // Update selected styling
            table.querySelectorAll('.event-row').forEach(r => {
                r.classList.remove('selected');
            });
            row.classList.add('selected');
            
            // Update all detail cells in the selected row
            row.querySelector('.event-detail').innerHTML = formatEventDetail(event.eventPayloadJson);
            row.querySelector('.context-before').innerHTML = formatContextBefore(event.contextBeforeJson, event);
            row.querySelector('.context-after').innerHTML = formatContextAfter(event.contextAfterJson, event);
            
            // Clear other rows' details
            table.querySelectorAll('.event-row').forEach((r, idx) => {
                if (r !== row) {
                    r.querySelector('.event-detail').innerHTML = '';
                    r.querySelector('.context-before').innerHTML = '';
                    r.querySelector('.context-after').innerHTML = '';
                    r.querySelector('.registry-before').innerHTML = '';
                    r.querySelector('.registry-after').innerHTML = '';
                }
            });
        }
        
        function formatEventDetail(eventPayloadJson) {
            if (!eventPayloadJson) return '<div style="color: #6c757d;">No event data</div>';
            
            try {
                const data = JSON.parse(eventPayloadJson);
                return `<pre style="margin: 0;">${JSON.stringify(data, null, 2)}</pre>`;
            } catch (e) {
                return `<pre style="margin: 0;">${eventPayloadJson}</pre>`;
            }
        }
        
        function formatContextBefore(contextJson, event) {
            let html = '';
            
            // Add Registry Status at the top
            html += `
                <div style="background: #fff3cd; border: 1px solid #ffc107; border-radius: 4px; padding: 6px; margin-bottom: 8px; font-size: 11px;">
                    <strong>Registry Status Before:</strong><br>
                    Status: <span style="color: ${getRegistryColor(event.registryStatusBefore)}; font-weight: 600;">${event.registryStatusBefore || 'N/A'}</span><br>
                    Hydrated: ${event.machineHydratedBefore ? '💧 Yes' : '🏜️ No'}<br>
                    Via Registry: ${event.eventSentThroughRegistry ? '✅ Yes' : '❌ No'}
                </div>
            `;
            
            // Parse and display machine data
            if (!contextJson) {
                html += '<div style="color: #6c757d;">No context data</div>';
            } else {
                try {
                    const data = JSON.parse(contextJson);
                    
                    // Show CallEntity first (persisted data)
                    if (data.callEntity) {
                        html += '<div style="font-size: 11px; font-weight: 600; margin-bottom: 4px; color: #17a2b8;">📊 CallEntity (Persisted):</div>';
                        html += `
                            <div style="background: #e7f3ff; border: 1px solid #b3d7ff; border-radius: 4px; padding: 6px; margin-bottom: 8px; font-size: 10px;">
                                <pre style="margin: 0;">${JSON.stringify(data.callEntity, null, 2)}</pre>
                            </div>
                        `;
                    }
                    
                    // Show CallContext second (volatile data)
                    if (data.callContext) {
                        html += '<div style="font-size: 11px; font-weight: 600; margin-bottom: 4px; color: #6f42c1;">🧠 CallContext (Volatile):</div>';
                        html += `
                            <div style="background: #f8f0ff; border: 1px solid #d1b3ff; border-radius: 4px; padding: 6px; font-size: 10px;">
                                <pre style="margin: 0;">${JSON.stringify(data.callContext, null, 2)}</pre>
                            </div>
                        `;
                    }
                    
                    // Fallback for old format
                    if (!data.callEntity && !data.callContext) {
                        html += '<div style="font-size: 11px; font-weight: 600; margin-bottom: 4px;">Machine Context:</div>';
                        html += `
                            <div class="context-section">
                                <pre style="margin: 0; font-size: 10px;">${JSON.stringify(data, null, 2)}</pre>
                            </div>
                        `;
                    }
                } catch (e) {
                    html += '<div style="font-size: 11px; font-weight: 600; margin-bottom: 4px;">Machine Context:</div>';
                    html += `<pre style="margin: 0; font-size: 10px;">${contextJson}</pre>`;
                }
            }
            
            return html;
        }
        
        function formatContextAfter(contextJson, event) {
            let html = '';
            
            // Add Registry Status at the top
            html += `
                <div style="background: #d4edda; border: 1px solid #28a745; border-radius: 4px; padding: 6px; margin-bottom: 8px; font-size: 11px;">
                    <strong>Registry Status After:</strong><br>
                    Status: <span style="color: ${getRegistryColor(event.registryStatusAfter)}; font-weight: 600;">${event.registryStatusAfter || 'N/A'}</span><br>
                    Hydrated: ${event.machineHydratedAfter ? '💧 Yes' : '🏜️ No'}<br>
                    Online: ${event.machineOnline ? '🟢 Yes' : '🔴 No'}
                </div>
            `;
            
            // Parse and display machine data  
            if (!contextJson) {
                html += '<div style="color: #6c757d;">No context data</div>';
            } else {
                try {
                    const data = JSON.parse(contextJson);
                    
                    // Show CallEntity first (persisted data)
                    if (data.callEntity) {
                        html += '<div style="font-size: 11px; font-weight: 600; margin-bottom: 4px; color: #17a2b8;">📊 CallEntity (Persisted):</div>';
                        html += `
                            <div style="background: #e7f3ff; border: 1px solid #b3d7ff; border-radius: 4px; padding: 6px; margin-bottom: 8px; font-size: 10px;">
                                <pre style="margin: 0;">${JSON.stringify(data.callEntity, null, 2)}</pre>
                            </div>
                        `;
                    }
                    
                    // Show CallContext second (volatile data)
                    if (data.callContext) {
                        html += '<div style="font-size: 11px; font-weight: 600; margin-bottom: 4px; color: #6f42c1;">🧠 CallContext (Volatile):</div>';
                        html += `
                            <div style="background: #f8f0ff; border: 1px solid #d1b3ff; border-radius: 4px; padding: 6px; font-size: 10px;">
                                <pre style="margin: 0;">${JSON.stringify(data.callContext, null, 2)}</pre>
                            </div>
                        `;
                    }
                    
                    // Fallback for old format
                    if (!data.callEntity && !data.callContext) {
                        html += '<div style="font-size: 11px; font-weight: 600; margin-bottom: 4px;">Machine Context:</div>';
                        html += `
                            <div class="context-section">
                                <pre style="margin: 0; font-size: 10px;">${JSON.stringify(data, null, 2)}</pre>
                            </div>
                        `;
                    }
                } catch (e) {
                    html += '<div style="font-size: 11px; font-weight: 600; margin-bottom: 4px;">Machine Context:</div>';
                    html += `<pre style="margin: 0; font-size: 10px;">${contextJson}</pre>`;
                }
            }
            
            return html;
        }
        
        function getRegistryColor(status) {
            if (!status) return '#6c757d';
            
            switch(status?.toUpperCase()) {
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
        
        
        function getStatusClass(status) {
            if (!status) return '';
            switch(status.toUpperCase()) {
                case 'REGISTERED_ACTIVE': return 'status-active';
                case 'REGISTERED_INACTIVE': return 'status-inactive';
                case 'NOT_REGISTERED': return 'status-not-registered';
                default: return '';
            }
        }
        
        function formatJson(jsonString) {
            if (!jsonString || jsonString.trim() === '') {
                return 'No data available';
            }
            
            try {
                const parsed = JSON.parse(jsonString);
                return JSON.stringify(parsed, null, 2);
            } catch (e) {
                return jsonString;
            }
        }
        
        function refreshData() {
            currentPage = 0;
            loadRuns();
            
            if (selectedRunId) {
                loadHistory(selectedRunId);
            }
        }
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