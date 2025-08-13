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
 * Simple State Machine Monitoring Server
 * 
 * Provides a web interface to view state machine monitoring data
 * with fallback to sample data if database is not available.
 */
public class SimpleMonitoringServer {
    
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/statemachine_monitoring";
    private static final String DB_USER = "statemachine";
    private static final String DB_PASSWORD = "monitoring123";
    
    private HttpServer server;
    private Connection dbConnection;
    private boolean databaseAvailable = false;
    private int webSocketPort = 9999; // Default WebSocket port
    
    public static void main(String[] args) throws Exception {
        SimpleMonitoringServer monitor = new SimpleMonitoringServer();
        monitor.start(8091);
        
        System.out.println("🚀 State Machine Monitoring Server Started!");
        System.out.println("📊 Open your browser and go to: http://localhost:8091");
        System.out.println("🎯 Custom monitoring interface for TelcoBright State Machines");
        
        // Keep server running
        Thread.currentThread().join();
    }
    
    public void setWebSocketPort(int port) {
        this.webSocketPort = port;
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
            try {
                String html = generateMainPage();
                
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, html.getBytes().length);
                
                OutputStream os = exchange.getResponseBody();
                os.write(html.getBytes());
                os.close();
            } catch (Exception e) {
                System.err.println("Error in MainPageHandler: " + e.getMessage());
                e.printStackTrace();
                throw new IOException(e);
            }
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
        // Implementation for real database queries (same as EnhancedMonitoringServer)
        return getSampleRuns(); // Fallback for now
    }
    
    private List<StateTransition> fetchHistoryFromDatabase(String runId) throws SQLException {
        // Implementation for real database queries (same as EnhancedMonitoringServer)
        return getSampleHistory(runId); // Fallback for now
    }
    
    private List<RunInfo> getSampleRuns() {
        List<RunInfo> runs = new ArrayList<>();
        
        // Sample run 1 - CallMachine with multiple events in same state
        RunInfo run1 = new RunInfo();
        run1.runId = "call-demo-" + System.currentTimeMillis();
        run1.machineId = "call-001";
        run1.machineType = "CallMachine";
        run1.latestTimestamp = LocalDateTime.now().minusMinutes(5).toString();
        run1.transitionCount = 11;
        run1.triggeringClassName = "MonitoredCallMachineDemo";
        run1.triggeringClassFullPath = "com.telcobright.statemachine.test.MonitoredCallMachineDemo";
        runs.add(run1);
        
        // Sample run 2 - SMS Machine
        RunInfo run2 = new RunInfo();
        run2.runId = "sms-demo-" + (System.currentTimeMillis() - 10000);
        run2.machineId = "sms-001";
        run2.machineType = "SmsMachine";
        run2.latestTimestamp = LocalDateTime.now().minusMinutes(15).toString();
        run2.transitionCount = 3;
        run2.triggeringClassName = "SmsProcessingDemo";
        run2.triggeringClassFullPath = "com.telcobright.statemachine.test.SmsProcessingDemo";
        runs.add(run2);
        
        return runs;
    }
    
    private List<StateTransition> getSampleHistory(String runId) {
        List<StateTransition> history = new ArrayList<>();
        
        if (runId.startsWith("call-demo")) {
            // CallMachine sample transitions - demonstrating multiple events in same state
            
            // Step 1: IDLE -> RINGING (incoming call)
            history.add(createSampleTransition(1, "IDLE", "RINGING", "INCOMING_CALL", 120,
                "{\"caller\":\"+1-555-DEMO\",\"callType\":\"VOICE\",\"priority\":\"normal\"}", 
                "{\"registryStatus\":\"REGISTERED_ACTIVE\",\"callId\":\"call-001\",\"fromNumber\":\"+1-555-DEMO\",\"toNumber\":\"+1-555-USER\"}", 
                "{\"registryStatus\":\"REGISTERED_ACTIVE\",\"callId\":\"call-001\",\"fromNumber\":\"+1-555-DEMO\",\"toNumber\":\"+1-555-USER\",\"currentState\":\"RINGING\",\"ringStartTime\":\"2025-08-09T21:56:39.341814146\"}"));
            
            // Step 2: RINGING -> RINGING (first session progress - trying)
            history.add(createSampleTransition(2, "RINGING", "RINGING", "SESSION_PROGRESS", 50,
                "{\"status\":\"TRYING\",\"code\":100,\"message\":\"Trying to reach destination\"}", 
                "{\"registryStatus\":\"REGISTERED_ACTIVE\",\"callId\":\"call-001\",\"currentState\":\"RINGING\",\"sessionEvents\":[]}", 
                "{\"registryStatus\":\"REGISTERED_ACTIVE\",\"callId\":\"call-001\",\"currentState\":\"RINGING\",\"sessionEvents\":[\"Trying\"]}"));
            
            // Step 3: RINGING -> RINGING (second session progress - ringing)
            history.add(createSampleTransition(3, "RINGING", "RINGING", "SESSION_PROGRESS", 45,
                "{\"status\":\"RINGING\",\"code\":180,\"message\":\"Ringing at destination\"}", 
                "{\"registryStatus\":\"REGISTERED_ACTIVE\",\"callId\":\"call-001\",\"currentState\":\"RINGING\",\"sessionEvents\":[\"Trying\"]}", 
                "{\"registryStatus\":\"REGISTERED_ACTIVE\",\"callId\":\"call-001\",\"currentState\":\"RINGING\",\"sessionEvents\":[\"Trying\",\"Ringing\"]}"));
            
            // Step 4: RINGING -> RINGING (third session progress - early media)
            history.add(createSampleTransition(4, "RINGING", "RINGING", "SESSION_PROGRESS", 55,
                "{\"status\":\"EARLY_MEDIA\",\"code\":183,\"message\":\"Session progress with early media\",\"mediaType\":\"audio\"}", 
                "{\"registryStatus\":\"REGISTERED_ACTIVE\",\"callId\":\"call-001\",\"currentState\":\"RINGING\",\"sessionEvents\":[\"Trying\",\"Ringing\"]}", 
                "{\"registryStatus\":\"REGISTERED_ACTIVE\",\"callId\":\"call-001\",\"currentState\":\"RINGING\",\"sessionEvents\":[\"Trying\",\"Ringing\",\"Early media\"]}"));
            
            // Step 5: RINGING -> CONNECTED (answer)
            history.add(createSampleTransition(5, "RINGING", "CONNECTED", "ANSWER", 200,
                "{\"answeredBy\":\"user\",\"answerTime\":\"2025-08-09T21:56:44.123456789\",\"deviceType\":\"mobile\"}", 
                "{\"registryStatus\":\"REGISTERED_ACTIVE\",\"callId\":\"call-001\",\"currentState\":\"RINGING\",\"sessionEvents\":[\"Trying\",\"Ringing\",\"Early media\"]}", 
                "{\"registryStatus\":\"REGISTERED_ACTIVE\",\"callId\":\"call-001\",\"currentState\":\"CONNECTED\",\"connectedTime\":\"" + LocalDateTime.now() + "\",\"sessionEvents\":[]}"));
            
            // Step 6: CONNECTED -> CONNECTED (DTMF event - user presses 1)
            history.add(createSampleTransition(6, "CONNECTED", "CONNECTED", "DTMF", 30,
                "{\"digit\":\"1\",\"duration\":200,\"volume\":0}", 
                "{\"registryStatus\":\"REGISTERED_ACTIVE\",\"callId\":\"call-001\",\"currentState\":\"CONNECTED\",\"dtmfBuffer\":\"\"}", 
                "{\"registryStatus\":\"REGISTERED_ACTIVE\",\"callId\":\"call-001\",\"currentState\":\"CONNECTED\",\"dtmfBuffer\":\"1\"}"));
            
            // Step 7: CONNECTED -> CONNECTED (DTMF event - user presses 2)
            history.add(createSampleTransition(7, "CONNECTED", "CONNECTED", "DTMF", 25,
                "{\"digit\":\"2\",\"duration\":200,\"volume\":0}", 
                "{\"registryStatus\":\"REGISTERED_ACTIVE\",\"callId\":\"call-001\",\"currentState\":\"CONNECTED\",\"dtmfBuffer\":\"1\"}", 
                "{\"registryStatus\":\"REGISTERED_ACTIVE\",\"callId\":\"call-001\",\"currentState\":\"CONNECTED\",\"dtmfBuffer\":\"12\"}"));
            
            // Step 8: CONNECTED -> CONNECTED (DTMF event - user presses 3)
            history.add(createSampleTransition(8, "CONNECTED", "CONNECTED", "DTMF", 28,
                "{\"digit\":\"3\",\"duration\":200,\"volume\":0}", 
                "{\"registryStatus\":\"REGISTERED_ACTIVE\",\"callId\":\"call-001\",\"currentState\":\"CONNECTED\",\"dtmfBuffer\":\"12\"}", 
                "{\"registryStatus\":\"REGISTERED_ACTIVE\",\"callId\":\"call-001\",\"currentState\":\"CONNECTED\",\"dtmfBuffer\":\"123\"}"));
            
            // Step 9: CONNECTED -> CONNECTED (Hold event)
            history.add(createSampleTransition(9, "CONNECTED", "CONNECTED", "HOLD", 35,
                "{\"holdType\":\"local\",\"reason\":\"user_initiated\",\"musicOnHold\":true}", 
                "{\"registryStatus\":\"REGISTERED_ACTIVE\",\"callId\":\"call-001\",\"currentState\":\"CONNECTED\",\"onHold\":false}", 
                "{\"registryStatus\":\"REGISTERED_ACTIVE\",\"callId\":\"call-001\",\"currentState\":\"CONNECTED\",\"onHold\":true,\"holdStartTime\":\"" + LocalDateTime.now() + "\"}"));
            
            // Step 10: CONNECTED -> CONNECTED (Resume event)
            history.add(createSampleTransition(10, "CONNECTED", "CONNECTED", "RESUME", 40,
                "{\"resumeType\":\"local\",\"holdDuration\":5000}", 
                "{\"registryStatus\":\"REGISTERED_ACTIVE\",\"callId\":\"call-001\",\"currentState\":\"CONNECTED\",\"onHold\":true}", 
                "{\"registryStatus\":\"REGISTERED_ACTIVE\",\"callId\":\"call-001\",\"currentState\":\"CONNECTED\",\"onHold\":false,\"totalHoldTime\":5000}"));
            
            // Step 11: CONNECTED -> IDLE (hangup)
            history.add(createSampleTransition(11, "CONNECTED", "IDLE", "HANGUP", 100,
                "{\"reason\":\"normal_clearing\",\"duration\":45,\"initiatedBy\":\"remote\",\"callQuality\":\"good\"}", 
                "{\"registryStatus\":\"REGISTERED_ACTIVE\",\"callId\":\"call-001\",\"currentState\":\"CONNECTED\"}", 
                "{\"registryStatus\":\"REGISTERED_ACTIVE\",\"callId\":\"call-001\",\"currentState\":\"IDLE\",\"complete\":true,\"callEndTime\":\"" + LocalDateTime.now() + "\"}"));
                
        } else if (runId.startsWith("sms-demo")) {
            // SMS Machine sample transitions
            history.add(createSampleTransition(1, "PENDING", "SENDING", "SEND_SMS", 80,
                "{\"to\":\"+1-555-TEST\",\"message\":\"Hello World!\"}", 
                "{\"smsId\":\"sms-001\",\"status\":\"PENDING\"}", 
                "{\"smsId\":\"sms-001\",\"status\":\"SENDING\"}"));
            
            history.add(createSampleTransition(2, "SENDING", "DELIVERED", "DELIVERY_CONFIRMATION", 150,
                "{\"status\":\"delivered\",\"timestamp\":\"" + LocalDateTime.now() + "\"}", 
                "{\"smsId\":\"sms-001\",\"status\":\"SENDING\"}", 
                "{\"smsId\":\"sms-001\",\"status\":\"DELIVERED\",\"complete\":true}"));
        }
        
        return history;
    }
    
    private StateTransition createSampleTransition(long version, String stateBefore, String stateAfter, 
                                                 String eventType, long duration, String eventPayload, 
                                                 String contextBefore, String contextAfter) {
        StateTransition t = new StateTransition();
        t.version = version;
        t.timestamp = LocalDateTime.now().minusMinutes(20 - version).toString();
        t.stateBefore = stateBefore;
        t.stateAfter = stateAfter;
        t.eventType = eventType;
        t.transitionDuration = duration;
        t.registryStatus = "REGISTERED_ACTIVE";
        t.machineOnline = true;
        t.eventPayloadJson = eventPayload;
        t.contextBeforeJson = contextBefore;
        t.contextAfterJson = contextAfter;
        t.registryStatusBefore = "REGISTERED_ACTIVE";
        t.registryStatusAfter = "REGISTERED_ACTIVE";
        t.machineHydratedBefore = true;
        t.machineHydratedAfter = true;
        t.eventSentThroughRegistry = true;
        return t;
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
                .append("\"eventPayloadJson\":").append(t.eventPayloadJson != null ? "\"" + escapeJson(t.eventPayloadJson) + "\"" : "null").append(",")
                .append("\"contextBeforeJson\":").append(t.contextBeforeJson != null ? "\"" + escapeJson(t.contextBeforeJson) + "\"" : "null").append(",")
                .append("\"contextAfterJson\":").append(t.contextAfterJson != null ? "\"" + escapeJson(t.contextAfterJson) + "\"" : "null").append(",")
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
    
    private String generateMainPage() {
        String dbStatusIcon = databaseAvailable ? "✅" : "⚠️";
        String dbStatusText = databaseAvailable ? "DB Connected" : "Sample Data";
        String dbStatusColor = databaseAvailable ? "#28a745" : "#ffc107";
        
        // Using StringBuilder to avoid Java string literal size limit
        StringBuilder page = new StringBuilder();
        page.append("""
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
        
        .header-left h1 { font-size: 24px; margin-bottom: 5px; }
        .header-left p { font-size: 14px; opacity: 0.9; }
        
        .header-right {
            display: flex;
            align-items: center;
            gap: 20px;
        }
        
        .db-status {
            font-size: 12px;
            padding: 5px 10px;
            background: rgba(255,255,255,0.1);
            border-radius: 4px;
            display: flex;
            align-items: center;
            gap: 5px;
        }
        
        .mode-buttons {
            display: flex;
            gap: 10px;
        }
        
        .mode-btn {
            background: rgba(255,255,255,0.2);
            border: 1px solid rgba(255,255,255,0.3);
            color: white;
            padding: 6px 12px;
            border-radius: 6px;
            cursor: pointer;
            font-size: 13px;
            transition: all 0.3s;
        }
        
        .mode-btn:hover {
            background: rgba(255,255,255,0.3);
        }
        
        .mode-btn.active {
            background: rgba(255,255,255,0.4);
            border-color: white;
        }
        
        
        .container {
            display: flex;
            height: calc(100vh - 120px);
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
            justify-content: between;
            align-items: center;
        }
        
        .transition-body {
            padding: 15px;
        }
        
        .state-flow {
            display: flex;
            align-items: center;
            margin-bottom: 15px;
            font-size: 16px;
            font-weight: 600;
        }
        
        .state {
            background: #e9ecef;
            padding: 8px 12px;
            border-radius: 6px;
            color: #495057;
        }
        
        .arrow {
            margin: 0 15px;
            color: #007bff;
            font-size: 20px;
        }
        
        .event-info {
            background: #f8f9fa;
            border: 1px solid #e9ecef;
            border-radius: 6px;
            padding: 12px;
            margin-bottom: 15px;
        }
        
        .context-section {
            margin-bottom: 15px;
        }
        
        .context-title {
            font-weight: 600;
            color: #495057;
            margin-bottom: 8px;
            font-size: 14px;
        }
        
        .context-content {
            background: #f8f9fa;
            border: 1px solid #e9ecef;
            border-radius: 4px;
            padding: 12px;
            font-family: 'Courier New', monospace;
            font-size: 12px;
            max-height: 200px;
            overflow-y: auto;
            white-space: pre-wrap;
        }
        
        .refresh-btn {
            background: #17a2b8;
            color: white;
            border: none;
            padding: 6px 12px;
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
        
        .registry-info {
            background: #e8f4fd;
            border: 1px solid #b3d7ff;
            border-radius: 4px;
            padding: 8px;
            margin: 8px 0;
            font-size: 12px;
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
            <div class="mode-buttons">
                <button class="mode-btn active" onclick="setMode('snapshot')">📸 Snapshot Viewer</button>
                <button class="mode-btn" onclick="setMode('live')">🔴 Live Viewer</button>
            </div>
        </div>
    </div>
    
    <div class="container">
        <div class="left-panel">
            <div class="panel-header">
                <span>📋 Recent Runs</span>
                <button class="refresh-btn" onclick="refreshData()">Refresh</button>
            </div>
            <div class="run-list" id="runList">
                <div class="loading">Loading state machine runs...</div>
            </div>
        </div>
        
        <div class="right-panel">
            <div class="panel-header" id="historyHeader">
                📈 State Machine History
            </div>
            <div class="history-content" id="historyContent">
                <div class="empty-state">
                    <h3>🎯 Welcome to State Machine Monitoring!</h3>
                    <p>Select a run from the left to see detailed state transitions including:</p>
                    <ul style="text-align: left; display: inline-block; margin-top: 15px;">
                        <li>📍 <strong>State Transitions:</strong> IDLE → RINGING → CONNECTED</li>
                        <li>⚡ <strong>Event Processing:</strong> IncomingCall, Answer, Hangup</li>
                        <li>📊 <strong>Performance Metrics:</strong> Transition durations</li>
                        <li>🧠 <strong>Context Data:</strong> Before/after state context</li>
                        <li>🏛️ <strong>Registry Status:</strong> Machine lifecycle tracking</li>
                    </ul>
                </div>
            </div>
        </div>
    </div>

    <script>
        let currentPage = 0;
        let selectedRunId = null;
        let currentMode = 'snapshot';
        
        window.onload = function() {
            loadRuns();
        };
        
        function setMode(mode) {
            currentMode = mode;
            document.querySelectorAll('.mode-btn').forEach(btn => {
                btn.classList.remove('active');
            });
            event.target.classList.add('active');
            
            if (mode === 'live') {
                switchToLiveMode();
            } else {
                switchToSnapshotMode();
            }
        }
        
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
                <div style="font-weight: 600; font-size: 13px; margin-bottom: 4px; color: #007bff;">🔍 ${run.runId}</div>
                <div style="font-size: 12px; color: #6c757d;">
                    📱 <strong>${run.machineType}:</strong> ${run.machineId}<br>
                    ⚡ ${run.transitionCount} state transitions<br>
                    🕒 ${new Date(run.latestTimestamp).toLocaleString()}
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
            
            document.getElementById('historyHeader').innerHTML = `📈 History: ${runId}`;
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
                content.innerHTML = '<div class="empty-state"><p>No state transitions found for this run.</p></div>';
                return;
            }
            
            // Group transitions by state
            const stateGroups = [];
            let currentGroup = null;
            
            history.forEach((transition, index) => {
                // Check if this is a same-state transition (state doesn't change)
                const isSameState = transition.stateBefore === transition.stateAfter;
                
                // If we're in the same state as the previous transition, add to current group
                if (currentGroup && currentGroup.state === transition.stateAfter) {
                    currentGroup.transitions.push(transition);
                } else {
                    // Create a new group for this state
                    currentGroup = {
                        state: transition.stateAfter,
                        fromState: transition.stateBefore,
                        transitions: [transition],
                        isSameState: isSameState
                    };
                    stateGroups.push(currentGroup);
                }
            });
            
            // Create visual groups for each state
            stateGroups.forEach((group, groupIndex) => {
                // Alternating color schemes for visual distinction
                const colorSchemes = [
                    { border: '#007bff', headerStart: '#007bff', headerEnd: '#6610f2', background: 'linear-gradient(to right, #f0f8ff, white)' },  // Blue
                    { border: '#28a745', headerStart: '#28a745', headerEnd: '#20c997', background: 'linear-gradient(to right, #f0fff4, white)' },  // Green
                    { border: '#ffc107', headerStart: '#ffc107', headerEnd: '#ff9800', background: 'linear-gradient(to right, #fffbf0, white)' },  // Yellow/Orange
                    { border: '#dc3545', headerStart: '#dc3545', headerEnd: '#c82333', background: 'linear-gradient(to right, #fff5f5, white)' },  // Red
                    { border: '#6f42c1', headerStart: '#6f42c1', headerEnd: '#5a32a3', background: 'linear-gradient(to right, #f8f5ff, white)' },  // Purple
                    { border: '#17a2b8', headerStart: '#17a2b8', headerEnd: '#138496', background: 'linear-gradient(to right, #f0fbfc, white)' }   // Cyan
                ];
                
                const colorScheme = colorSchemes[groupIndex % colorSchemes.length];
                
                const stateContainer = document.createElement('div');
                stateContainer.style.cssText = `
                    background: ${colorScheme.background};
                    border: 5px solid ${colorScheme.border};
                    border-radius: 16px;
                    margin-bottom: 30px;
                    padding: 20px;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.1);
                `;
                
                // State header
                const stateHeader = document.createElement('div');
                stateHeader.style.cssText = `
                    background: linear-gradient(135deg, ${colorScheme.headerStart}, ${colorScheme.headerEnd});
                    color: white;
                    padding: 10px 20px;
                    border-radius: 12px;
                    margin: -20px -20px 15px -20px;
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                `;
                
                const firstTransition = group.transitions[0];
                const lastTransition = group.transitions[group.transitions.length - 1];
                
                stateHeader.innerHTML = `
                    <div style="display: flex; align-items: center; gap: 20px;">
                        <div style="font-size: 24px; font-weight: bold;">
                            📍 State: ${group.state}
                        </div>
                        <div style="font-size: 13px; opacity: 0.9; padding-top: 2px;">
                            ${group.transitions.length > 1 ? 
                                `🔄 ${group.transitions.length} events in this state` : 
                                `➡️ Transitioned from ${group.fromState}`}
                        </div>
                    </div>
                    <div style="text-align: right;">
                        <div style="font-size: 12px; opacity: 0.9;">
                            Steps ${firstTransition.version}${group.transitions.length > 1 ? '-' + lastTransition.version : ''}
                        </div>
                        <div style="font-size: 11px; opacity: 0.8;">
                            🕒 ${new Date(firstTransition.timestamp).toLocaleTimeString()}
                        </div>
                    </div>
                `;
                
                stateContainer.appendChild(stateHeader);
                
                // Create cards for each event within this state
                group.transitions.forEach((transition, eventIndex) => {
                    const eventCard = document.createElement('div');
                    eventCard.style.cssText = `
                        background: white;
                        border: 1px solid #dee2e6;
                        border-radius: 10px;
                        margin-bottom: 15px;
                        overflow: hidden;
                        box-shadow: 0 2px 6px rgba(0,0,0,0.05);
                    `;
                    
                    // Event header
                    const eventHeader = document.createElement('div');
                    eventHeader.style.cssText = `
                        background: ${transition.stateBefore === transition.stateAfter ? '#fff3cd' : '#d1ecf1'};
                        padding: 10px 15px;
                        border-bottom: 1px solid #dee2e6;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                    `;
                    
                    eventHeader.innerHTML = `
                        <div style="display: flex; align-items: center; gap: 15px;">
                            <span style="font-weight: 600; color: #495057;">
                                Step ${transition.version}
                            </span>
                            <span style="background: ${transition.stateBefore === transition.stateAfter ? '#ffc107' : '#17a2b8'}; color: white; padding: 3px 10px; border-radius: 4px; font-size: 12px; font-weight: 600;">
                                ${transition.eventType}
                            </span>
                            ${transition.stateBefore !== transition.stateAfter ? 
                                `<span style="font-size: 12px; color: #6c757d;">
                                    ${transition.stateBefore} → ${transition.stateAfter}
                                </span>` : 
                                '<span style="font-size: 12px; color: #856404;">Stay in ' + transition.stateAfter + '</span>'
                            }
                        </div>
                        <div style="font-size: 11px; color: #6c757d;">
                            ⏱️ ${transition.transitionDuration}ms
                        </div>
                    `;
                    
                    eventCard.appendChild(eventHeader);
                    
                    // Event body with three columns
                    const eventBody = document.createElement('div');
                    eventBody.style.cssText = `
                        padding: 15px;
                        display: grid;
                        grid-template-columns: 1fr 1fr 1fr;
                        gap: 15px;
                    `;
                    
                    // Event payload section
                    const eventSection = document.createElement('div');
                    eventSection.style.cssText = `
                        background: #f8f9fa;
                        border-radius: 6px;
                        padding: 12px;
                        border-left: 3px solid #28a745;
                    `;
                    eventSection.innerHTML = `
                        <div style="font-weight: 600; color: #28a745; margin-bottom: 8px; font-size: 13px;">
                            📌 Event Payload
                        </div>
                        <pre style="margin: 0; background: white; padding: 8px; border-radius: 4px; overflow-x: auto; font-size: 10px; line-height: 1.3;">
${formatJson(transition.eventPayloadJson)}</pre>
                    `;
                    
                    // Context Before section
                    const contextBeforeSection = document.createElement('div');
                    contextBeforeSection.style.cssText = `
                        background: #fff5f5;
                        border-radius: 6px;
                        padding: 12px;
                        border-left: 3px solid #dc3545;
                        font-size: 11px;
                    `;
                    contextBeforeSection.innerHTML = formatContextColumn(
                        transition.contextBeforeJson, 
                        transition.registryStatusBefore,
                        transition.machineHydratedBefore,
                        'Before',
                        '#dc3545'
                    );
                    
                    // Context After section
                    const contextAfterSection = document.createElement('div');
                    contextAfterSection.style.cssText = `
                        background: #f0f8ff;
                        border-radius: 6px;
                        padding: 12px;
                        border-left: 3px solid #17a2b8;
                        font-size: 11px;
                    `;
                    contextAfterSection.innerHTML = formatContextColumn(
                        transition.contextAfterJson,
                        transition.registryStatusAfter, 
                        transition.machineHydratedAfter,
                        'After',
                        '#17a2b8'
                    );
                    
                    eventBody.appendChild(eventSection);
                    eventBody.appendChild(contextBeforeSection);
                    eventBody.appendChild(contextAfterSection);
                    
                    eventCard.appendChild(eventBody);
                    stateContainer.appendChild(eventCard);
                });
                
                content.appendChild(stateContainer);
            });
        }
        
        function formatContextColumn(contextJson, registryStatus, machineHydrated, label, color) {
            let html = `<div style="font-weight: 600; color: ${color}; margin-bottom: 8px;">
                Registry Status ${label}:
            </div>`;
            
            // Add registry status information
            const hydratedIcon = machineHydrated ? '💧' : '🔄';
            const onlineIcon = '🟢'; // Assuming online if we have data
            html += `<div style="font-family: monospace; font-size: 11px; line-height: 1.4; margin-bottom: 12px;">
                Status: ${registryStatus}<br>
                Hydrated: ${hydratedIcon} ${machineHydrated ? 'Yes' : 'No'}<br>
                Online: ${onlineIcon} Yes
            </div>`;
            
            // Parse and display context
            if (contextJson && contextJson.trim() !== '') {
                try {
                    const contextObj = JSON.parse(contextJson);
                    
                    // Separate ContextEntity (persistable) from volatile context
                    // Look for typical entity fields vs volatile fields
                    const persistentFields = {};
                    const volatileFields = {};
                    
                    Object.entries(contextObj).forEach(([key, value]) => {
                        // Entity fields typically include: callId, fromNumber, toNumber, currentState, registryStatus, timestamps, etc.
                        if (key.match(/^(callId|fromNumber|toNumber|currentState|smsId|status|complete|id|registryStatus)$/i) || 
                            key.endsWith('Id') || key.endsWith('Status') || key.endsWith('State') || key.endsWith('Time')) {
                            persistentFields[key] = value;
                        } else {
                            // Volatile fields: sessionEvents, connectedTime, etc.
                            volatileFields[key] = value;
                        }
                    });
                    
                    // Display Persistent Context
                    html += `<div style="margin-bottom: 8px;">
                        <div style="font-weight: 600; color: #495057; margin-bottom: 4px; font-size: 11px;">
                            Persistent Context:
                        </div>
                        <pre style="margin: 0; font-size: 10px; font-family: monospace; background: #f8f9fa; padding: 8px; border-radius: 4px; line-height: 1.3;">${JSON.stringify(persistentFields, null, 2)}</pre>
                    </div>`;
                    
                    // Display Volatile Context
                    html += `<div>
                        <div style="font-weight: 600; color: #495057; margin-bottom: 4px; font-size: 11px;">
                            Volatile Context:
                        </div>
                        <pre style="margin: 0; font-size: 10px; font-family: monospace; background: #f8f9fa; padding: 8px; border-radius: 4px; line-height: 1.3;">${JSON.stringify(volatileFields, null, 2)}</pre>
                    </div>`;
                    
                } catch (e) {
                    // Fallback if JSON parsing fails
                    html += `<div>
                        <div style="font-weight: 600; color: #495057; margin-bottom: 4px; font-size: 11px;">
                            Context:
                        </div>
                        <pre style="margin: 0; font-size: 10px; font-family: monospace; background: #f8f9fa; padding: 8px; border-radius: 4px; line-height: 1.3;">${contextJson}</pre>
                    </div>`;
                }
            } else {
                // Still show persistent and volatile sections even if empty
                html += `<div style="margin-bottom: 8px;">
                    <div style="font-weight: 600; color: #495057; margin-bottom: 4px; font-size: 11px;">
                        Persistent Context:
                    </div>
                    <pre style="margin: 0; font-size: 10px; font-family: monospace; background: #f8f9fa; padding: 8px; border-radius: 4px; line-height: 1.3;">{}</pre>
                </div>`;
                html += `<div>
                    <div style="font-weight: 600; color: #495057; margin-bottom: 4px; font-size: 11px;">
                        Volatile Context:
                    </div>
                    <pre style="margin: 0; font-size: 10px; font-family: monospace; background: #f8f9fa; padding: 8px; border-radius: 4px; line-height: 1.3;">{}</pre>
                </div>`;
            }
            
            return html;
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
            loadRuns();
            if (selectedRunId) {
                loadHistory(selectedRunId);
            }
        }
        
        // Live Mode Implementation
        let ws = null;
        let wsReconnectInterval = null;
        let liveTransitions = [];
        let isPaused = false;
        let currentMachineState = 'UNKNOWN';
        let transitionCounter = 0;
        let eventMetadata = {}; // Store event metadata from server
        
        function switchToLiveMode() {
            // Clean up any existing WebSocket connection first
            if (ws) {
                ws.close();
                ws = null;
            }
            if (wsReconnectInterval) {
                clearInterval(wsReconnectInterval);
                wsReconnectInterval = null;
            }
            
            // Don't reset transitions - keep the history
            // Only reset if explicitly clearing or if this is first time
            if (currentMode !== 'live') {
                // First time switching to live mode - clear everything
                liveTransitions = [];
                transitionCounter = 0;
                currentMachineState = 'UNKNOWN';
            }
            // Always reset these
            isPaused = false;
            eventMetadata = {};
            
            // Update UI for live mode
            const leftPanel = document.querySelector('.left-panel');
            const rightPanel = document.querySelector('.right-panel');
            
            // Update left panel with event builder
            leftPanel.innerHTML = `
                <div class="panel-header">
                    <span>🎮 CallMachine Control Panel</span>
                    <div class="connection-status" id="wsStatus">
                        <span class="status-dot disconnected"></span>
                        <span>Disconnected</span>
                    </div>
                </div>
                <div class="machine-status">
                    <h3>Machine Status</h3>
                    <div class="current-state">
                        Current State: <span id="currentState" class="state-badge">${currentMachineState}</span>
                    </div>
                    <div class="ws-url">WebSocket: ws://localhost:${webSocketPort}</div>
                </div>
                <div class="event-builder">
                    <h3>Send Event to CallMachine</h3>
                    <div class="event-type-selector">
                        <label>Event Type:</label>
                        <select id="eventType" onchange="updateEventPayloadHints()">
                            <!-- Options will be populated from metadata -->
                            <option value="" disabled selected>Loading events...</option>
                        </select>
                    </div>
                    <div class="payload-editor">
                        <label>Event Payload (JSON):</label>
                        <textarea id="eventPayload" placeholder='Event parameters will appear here based on selected event type'>{}</textarea>
                    </div>
                    <button class="send-event-btn" onclick="sendEvent()">📤 Send Event</button>
                </div>
                <div class="live-controls">
                    <button onclick="togglePause()" id="pauseBtn">⏸️ Pause Feed</button>
                    <button onclick="clearTransitions()">🗑️ Clear</button>
                    <button onclick="exportSession()">💾 Export</button>
                </div>
            `;
            
            // Update right panel header
            rightPanel.innerHTML = `
                <div class="panel-header">
                    <span>🔴 Live Transitions (<span id="transitionCount">0</span>)</span>
                    <span id="liveIndicator" class="live-indicator">● LIVE</span>
                </div>
                <div class="history-content" id="liveHistory">
                    <div class="loading">Connecting to WebSocket...</div>
                </div>
            `;
            
            // Add custom styles for live mode
            if (!document.getElementById('liveModeStyles')) {
                const style = document.createElement('style');
                style.id = 'liveModeStyles';
                style.textContent = `
                    .machine-status {
                        background: white;
                        padding: 15px;
                        margin: 10px;
                        border-radius: 8px;
                        border: 1px solid #dee2e6;
                    }
                    .current-state {
                        font-size: 16px;
                        margin: 10px 0;
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                    }
                    .state-badge {
                        background: #007bff;
                        color: white;
                        padding: 4px 8px;
                        border-radius: 4px;
                        font-weight: bold;
                    }
                    .ws-url {
                        color: #6c757d;
                        font-size: 12px;
                    }
                    """);
        
        // Continue with second part
        page.append("""
                    .event-builder {
                        background: white;
                        padding: 15px;
                        margin: 10px;
                        border-radius: 8px;
                        border: 1px solid #dee2e6;
                    }
                    .event-builder h3 {
                        margin-top: 0;
                        color: #495057;
                    }
                    .event-type-selector, .payload-editor {
                        margin-bottom: 15px;
                    }
                    .event-type-selector label, .payload-editor label {
                        display: block;
                        margin-bottom: 5px;
                        color: #495057;
                        font-weight: 600;
                    }
                    .event-type-selector select {
                        width: 100%;
                        padding: 8px;
                        border: 1px solid #ced4da;
                        border-radius: 4px;
                    }
                    .payload-editor textarea {
                        width: 100%;
                        height: 100px;
                        padding: 8px;
                        border: 1px solid #ced4da;
                        border-radius: 4px;
                        font-family: 'Courier New', monospace;
                    }
                    .send-event-btn {
                        width: 100%;
                        padding: 10px;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                        border: none;
                        border-radius: 6px;
                        font-weight: bold;
                        cursor: pointer;
                    }
                    .send-event-btn:hover {
                        opacity: 0.9;
                    }
                    .live-controls {
                        padding: 10px;
                        display: flex;
                        gap: 10px;
                    }
                    .live-controls button {
                        flex: 1;
                        padding: 8px;
                        border: 1px solid #dee2e6;
                        background: white;
                        border-radius: 4px;
                        cursor: pointer;
                    }
                    .live-controls button:hover {
                        background: #f8f9fa;
                    }
                    .connection-status {
                        display: flex;
                        align-items: center;
                        gap: 5px;
                    }
                    .status-dot {
                        width: 8px;
                        height: 8px;
                        border-radius: 50%;
                    }
                    .status-dot.connected {
                        background: #28a745;
                        animation: pulse 2s infinite;
                    }
                    .status-dot.disconnected {
                        background: #dc3545;
                    }
                    @keyframes pulse {
                        0% { opacity: 1; }
                        50% { opacity: 0.5; }
                        100% { opacity: 1; }
                    }
                    .live-indicator {
                        color: #dc3545;
                        animation: blink 1s infinite;
                    }
                    @keyframes blink {
                        0% { opacity: 1; }
                        50% { opacity: 0.3; }
                        100% { opacity: 1; }
                    }
                `;
                document.head.appendChild(style);
            }
            
            // Start WebSocket connection
            connectWebSocket();
        }
        
        function handleEventMetadataUpdate(message) {
            console.log('Received event metadata:', message);
            eventMetadata = message;
            
            // Update the event selector with available events
            const eventSelector = document.getElementById('eventType');
            if (eventSelector && message.machines && message.machines.length > 0) {
                // Clear existing options
                eventSelector.innerHTML = '<option value="" disabled selected>Select an event...</option>';
                
                // Get the first machine's supported events
                const machine = message.machines[0];
                if (machine.supportedEvents) {
                    // Store events for later use
                    window.availableEvents = {};
                    
                    machine.supportedEvents.forEach(event => {
                        const option = document.createElement('option');
                        option.value = event.eventType;
                        option.textContent = event.displayName || event.eventType;
                        
                        // Store the mock data for this event
                        window.availableEvents[event.eventType] = event.mockData || {};
                        
                        // Add visual indicators
                        if (event.isStayEvent) {
                            option.textContent += ' (Stay)';
                        }
                        
                        eventSelector.appendChild(option);
                    });
                    
                    console.log('Populated dropdown with', machine.supportedEvents.length, 'events');
                }
                
                // Update machine info display
                updateMachineInfo(machine);
            }
        }
        
        function handleCompleteStatus(message) {
            console.log('Received complete status:', message);
            
            // Update registry info
            if (message.registry) {
                const statusEl = document.querySelector('.connection-status');
                if (statusEl) {
                    statusEl.innerHTML = `
                        <span class="status-dot connected"></span> 
                        Connected (${message.registry.connectedClients} clients, ${message.registry.machineCount} machines)
                    `;
                }
            }
            
            // Update machine states
            if (message.machines && message.machines.length > 0) {
                const machine = message.machines[0]; // Focus on first machine
                if (machine.currentState) {
                    currentMachineState = machine.currentState;
                    const stateEl = document.getElementById('currentState');
                    if (stateEl) {
                        stateEl.textContent = currentMachineState;
                    }
                }
            }
        }
        
        function updateMachineInfo(machine) {
            // Update machine ID display if needed
            const machineIdEl = document.getElementById('machineId');
            if (machineIdEl && machine.machineId) {
                machineIdEl.textContent = machine.machineId;
            }
            
            // Update current state
            if (machine.currentState) {
                currentMachineState = machine.currentState;
                const stateEl = document.getElementById('currentState');
                if (stateEl) {
                    stateEl.textContent = currentMachineState;
                }
            }
        }
        
        function updateEventPayloadHints() {
            const eventSelector = document.getElementById('eventType');
            const payloadTextarea = document.getElementById('eventPayload');
            
            if (!eventSelector || !payloadTextarea) return;
            
            const selectedEventType = eventSelector.value;
            
            if (selectedEventType && window.availableEvents) {
                // Get the mock data for the selected event
                const mockData = window.availableEvents[selectedEventType] || {};
                
                // Format and display the mock data
                if (Object.keys(mockData).length > 0) {
                    payloadTextarea.value = JSON.stringify(mockData, null, 2);
                } else {
                    // Empty object for events with no payload
                    payloadTextarea.value = '{}';
                }
                
                console.log('Populated payload for', selectedEventType, 'with:', mockData);
            } else {
                // Default empty object
                payloadTextarea.value = '{}';
            }
        }
        
        function switchToSnapshotMode() {
            // Disconnect WebSocket
            if (ws) {
                ws.close();
                ws = null;
            }
            if (wsReconnectInterval) {
                clearInterval(wsReconnectInterval);
                wsReconnectInterval = null;
            }
            
            // Restore snapshot mode UI
            location.reload(); // Simple way to restore original UI
        }
        
        function connectWebSocket() {
            if (ws && ws.readyState === WebSocket.OPEN) {
                return;
            }
            
            try {
                ws = new WebSocket('ws://localhost:${webSocketPort}');
                
                ws.onopen = function() {
                    console.log('WebSocket connected');
                    updateConnectionStatus(true);
                    document.getElementById('liveHistory').innerHTML = '<div class="empty-state">Waiting for transitions...</div>';
                    
                    // Clear reconnect interval
                    if (wsReconnectInterval) {
                        clearInterval(wsReconnectInterval);
                        wsReconnectInterval = null;
                    }
                    
                    // Request event metadata
                    ws.send(JSON.stringify({ action: 'GET_EVENT_METADATA' }));
                };
                
                ws.onmessage = function(event) {
                    handleWebSocketMessage(event.data);
                };
                
                ws.onclose = function() {
                    console.log('WebSocket disconnected');
                    updateConnectionStatus(false);
                    
                    // Start reconnection attempts
                    if (!wsReconnectInterval) {
                        wsReconnectInterval = setInterval(connectWebSocket, 1000);
                    }
                };
                
                ws.onerror = function(error) {
                    console.error('WebSocket error:', error);
                    updateConnectionStatus(false);
                };
                
            } catch (error) {
                console.error('Failed to connect WebSocket:', error);
                updateConnectionStatus(false);
                
                // Start reconnection attempts
                if (!wsReconnectInterval) {
                    wsReconnectInterval = setInterval(connectWebSocket, 1000);
                }
            }
        }
        
        function updateConnectionStatus(connected) {
            const statusEl = document.getElementById('wsStatus');
            if (statusEl) {
                if (connected) {
                    statusEl.innerHTML = `
                        <span class="status-dot connected"></span>
                        <span>Connected</span>
                    `;
                } else {
                    statusEl.innerHTML = `
                        <span class="status-dot disconnected"></span>
                        <span>Disconnected</span>
                    `;
                }
            }
        }
        
        function handleWebSocketMessage(data) {
            try {
                const message = JSON.parse(data);
                
                // Handle different message types
                if (message.type === 'EVENT_METADATA_UPDATE') {
                    handleEventMetadataUpdate(message);
                    return;
                } else if (message.type === 'COMPLETE_STATUS') {
                    handleCompleteStatus(message);
                    return;
                } else if (message.type === 'CURRENT_STATE') {
                    // Handle initial state display
                    handleCurrentState(message);
                    return;
                } else if (message.type === 'TIMEOUT_COUNTDOWN') {
                    // Handle countdown timer updates
                    handleTimeoutCountdown(message);
                    return;
                }
                
                // Update current state if available
                if (message.stateAfter) {
                    currentMachineState = message.stateAfter;
                    const stateEl = document.getElementById('currentState');
                    if (stateEl) {
                        stateEl.textContent = currentMachineState;
                    }
                }
                
                // Add to transitions if not paused (only for STATE_CHANGE events)
                if (!isPaused && message.type === 'STATE_CHANGE') {
                    liveTransitions.push(message);  // Add to end for chronological order
                    transitionCounter++;
                    updateLiveHistory();
                    updateTransitionCount();
                    // Update countdown display after DOM changes
                    updateCountdownDisplay();
                }
            } catch (error) {
                console.error('Failed to parse WebSocket message:', error);
            }
        }
        
        // Store countdown state globally to persist across updates
        let currentCountdownState = {
            remainingSeconds: 0,
            state: null,
            isActive: false
        };
        
        function handleTimeoutCountdown(message) {
            // Update global countdown state
            if (message.remainingSeconds > 0 && message.debugMode && message.state) {
                currentCountdownState.remainingSeconds = message.remainingSeconds;
                currentCountdownState.state = message.state;
                currentCountdownState.isActive = true;
            } else {
                currentCountdownState.isActive = false;
            }
            
            // Update countdown in the state card header
            updateCountdownDisplay();
        }
        
        function updateCountdownDisplay() {
            // Find all countdown elements (there may be multiple RINGING states)
            const countdownElements = document.querySelectorAll('.state-countdown');
            
            countdownElements.forEach(countdownEl => {
                const state = countdownEl.getAttribute('data-state');
                const countdownValueEl = countdownEl.querySelector('.countdown-value');
                
                // Show countdown only for the current active state with a countdown
                if (currentCountdownState.isActive && 
                    state === currentCountdownState.state && 
                    state === currentMachineState) {
                    
                    countdownEl.style.display = 'inline-block';
                    if (countdownValueEl) {
                        countdownValueEl.textContent = currentCountdownState.remainingSeconds;
                    }
                    
                    // Static colors based on remaining time
                    if (currentCountdownState.remainingSeconds <= 5) {
                        countdownEl.style.background = 'rgba(220, 53, 69, 0.3)'; // Red background
                        countdownEl.style.color = '#fff';
                    } else if (currentCountdownState.remainingSeconds <= 10) {
                        countdownEl.style.background = 'rgba(253, 126, 20, 0.3)'; // Orange background
                        countdownEl.style.color = '#fff';
                    } else {
                        countdownEl.style.background = 'rgba(255, 255, 255, 0.2)'; // White transparent
                        countdownEl.style.color = '#fff';
                    }
                } else {
                    countdownEl.style.display = 'none';
                }
            });
        }
        
        function handleCurrentState(message) {
            console.log('Received CURRENT_STATE:', message);
            
            // Update the current state display
            if (message.currentState) {
                currentMachineState = message.currentState;
                const stateEl = document.getElementById('currentState');
                if (stateEl) {
                    stateEl.textContent = currentMachineState;
                }
            }
            
            // Only add initial state entry if we don't have any transitions yet
            // This prevents duplicate entries when reconnecting
            if (!isPaused && message.currentState && liveTransitions.length === 0) {
                // Create a synthetic initial state transition
                const initialStateEntry = {
                    type: 'INITIAL_STATE',
                    timestamp: message.timestamp || new Date().toISOString(),
                    machineId: message.machineId,
                    currentState: message.currentState,
                    stateBefore: null,  // No previous state
                    stateAfter: message.currentState,
                    eventType: 'INITIAL',
                    context: message.context,
                    contextBefore: null,  // No context before
                    contextAfter: message.context
                };
                
                liveTransitions.push(initialStateEntry);  // Add to end for chronological order
                transitionCounter++;
                updateLiveHistory();
                updateTransitionCount();
                // Update countdown display after DOM changes
                updateCountdownDisplay();
            } else {
                // Just update the display without adding to history
                updateLiveHistory();
                // Update countdown display after DOM changes
                updateCountdownDisplay();
            }
        }
        
        function updateLiveHistory() {
            const historyEl = document.getElementById('liveHistory');
            if (!historyEl) return;
            
            if (liveTransitions.length === 0) {
                historyEl.innerHTML = '<div class="empty-state">No transitions yet...</div>';
                return;
            }
            
            // Group transitions by state
            const stateGroups = [];
            let currentGroup = null;
            
            liveTransitions.forEach((transition, index) => {
                // Check if this is a same-state transition or initial state
                const isSameState = transition.stateBefore === transition.stateAfter;
                const isInitialState = transition.type === 'INITIAL_STATE' || !transition.stateBefore;
                
                // If we're in the same state as the previous transition, add to current group
                if (currentGroup && currentGroup.state === transition.stateAfter) {
                    currentGroup.transitions.push(transition);
                } else {
                    // Create a new group for this state
                    currentGroup = {
                        state: transition.stateAfter || 'UNKNOWN',
                        fromState: transition.stateBefore || 'UNKNOWN',
                        transitions: [transition],
                        isSameState: isSameState
                    };
                    stateGroups.push(currentGroup);
                }
            });
            
            let html = '';
            let stepCounter = 1;
            
            stateGroups.forEach((group, groupIndex) => {
                // Determine if this is the most recent state group for the current machine state
                const isCurrentStateGroup = (groupIndex === stateGroups.length - 1) && (group.state === currentMachineState);
                
                // State header
                html += '<div style="background: #fff; border-radius: 8px; margin-bottom: 20px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">';
                html += '<div style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 15px 20px; position: relative;">';
                html += '<div style="display: flex; justify-content: space-between; align-items: center;">';
                html += '<h3 style="margin: 0; font-size: 18px;">State: ' + group.state + '</h3>';
                
                // Add countdown for the current RINGING state (most recent one)
                if (group.state === 'RINGING' && isCurrentStateGroup) {
                    html += '<span class="state-countdown" data-state="' + group.state + '" data-group-index="' + groupIndex + '" style="display: none; background: rgba(255,255,255,0.2); padding: 4px 10px; border-radius: 4px; font-size: 14px; font-weight: bold;">';
                    html += 'Timeout in: <span class="countdown-value">30</span>s';
                    html += '</span>';
                }
                
                html += '</div>';
                html += '<div style="display: flex; align-items: center; gap: 15px; margin-top: 8px; font-size: 14px; opacity: 0.95;">';
                html += '<span>🔄 ' + group.transitions.length + ' event' + (group.transitions.length > 1 ? 's' : '') + ' in this state</span>';
                html += '<span>Steps ' + stepCounter + '-' + (stepCounter + group.transitions.length - 1) + '</span>';
                
                const latestTime = group.transitions[0].timestamp;
                if (latestTime) {
                    // Timestamp is already in HH:mm:ss.SSS format, use it directly
                    html += '<span>🕒 ' + latestTime + '</span>';
                }
                html += '</div>';
                html += '</div>';
                
                // Create cards for each event within this state - matching Snapshot viewer format
                group.transitions.forEach(transition => {
                    // Event card container
                    html += '<div style="background: white; border: 1px solid #dee2e6; border-radius: 10px; margin: 15px; overflow: hidden; box-shadow: 0 2px 6px rgba(0,0,0,0.05);">';
                    
                    // Event header
                    const isInitialState = transition.type === 'INITIAL_STATE' || !transition.stateBefore;
                    const bgColor = isInitialState ? '#e8f5e9' : (transition.stateBefore === transition.stateAfter ? '#fff3cd' : '#d1ecf1');
                    const badgeColor = isInitialState ? '#4caf50' : (transition.stateBefore === transition.stateAfter ? '#ffc107' : '#17a2b8');
                    
                    html += '<div style="background: ' + bgColor + '; padding: 10px 15px; border-bottom: 1px solid #dee2e6; display: flex; justify-content: space-between; align-items: center;">';
                    html += '<div style="display: flex; align-items: center; gap: 15px;">';
                    html += '<span style="font-weight: 600; color: #495057;">Step ' + stepCounter + '</span>';
                    // Show event name if available, otherwise fall back to type
                    let eventDisplay = isInitialState ? 'Initial' : (transition.eventName || transition.eventType || transition.type || 'UNKNOWN');
                    html += '<span style="background: ' + badgeColor + '; color: white; padding: 3px 10px; border-radius: 4px; font-size: 12px; font-weight: 600;">' + eventDisplay + '</span>';
                    
                    if (isInitialState) {
                        html += '<span style="font-size: 12px; color: #2e7d32;">Initial State: ' + (transition.stateAfter || transition.currentState || 'UNKNOWN') + '</span>';
                    } else if (transition.stateBefore !== transition.stateAfter) {
                        html += '<span style="font-size: 12px; color: #6c757d;">' + (transition.stateBefore || 'UNKNOWN') + ' → ' + (transition.stateAfter || 'UNKNOWN') + '</span>';
                    } else {
                        html += '<span style="font-size: 12px; color: #856404;">Stay in ' + (transition.stateAfter || 'UNKNOWN') + '</span>';
                    }
                    html += '</div>';
                    
                    // Duration if available
                    if (transition.transitionDuration) {
                        html += '<div style="font-size: 11px; color: #6c757d;">⏱️ ' + transition.transitionDuration + 'ms</div>';
                    }
                    html += '</div>';
                    
                    // Event body with three columns
                    html += '<div style="padding: 15px; display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 15px;">';
                    
                    // Column 1: Event Payload (green border)
                    html += '<div style="background: #f8f9fa; border-radius: 6px; padding: 12px; border-left: 3px solid #28a745;">';
                    html += '<div style="font-weight: 600; color: #28a745; margin-bottom: 8px; font-size: 13px;">📌 Event Payload</div>';
                    html += '<pre style="margin: 0; background: white; padding: 8px; border-radius: 4px; overflow-x: auto; font-size: 10px; line-height: 1.3;">';
                    
                    let payload = transition.eventPayloadJson || transition.payload;
                    if (payload) {
                        if (typeof payload === 'string') {
                            try {
                                html += JSON.stringify(JSON.parse(payload), null, 2);
                            } catch (e) {
                                html += payload;
                            }
                        } else {
                            html += JSON.stringify(payload, null, 2);
                        }
                    } else {
                        html += '{}';
                    }
                    html += '</pre>';
                    html += '</div>';
                    
                    // Column 2: Context Before (red border)
                    html += '<div style="background: #fff5f5; border-radius: 6px; padding: 12px; border-left: 3px solid #dc3545; font-size: 11px;">';
                    html += formatLiveContextColumn(transition.contextBefore || transition.context, 'ACTIVE', false, 'Before', '#dc3545');
                    html += '</div>';
                    
                    // Column 3: Context After (blue border)
                    html += '<div style="background: #f0f8ff; border-radius: 6px; padding: 12px; border-left: 3px solid #17a2b8; font-size: 11px;">';
                    html += formatLiveContextColumn(transition.contextAfter || transition.context, 'ACTIVE', false, 'After', '#17a2b8');
                    html += '</div>';
                    
                    html += '</div>'; // End of 3-column grid
                    html += '</div>'; // End of event card
                    
                    stepCounter++;
                });
                
                html += '</div>';
                html += '</div>';
            });
            
            historyEl.innerHTML = html;
        }
        
        function formatLiveContextColumn(contextData, registryStatus, machineHydrated, label, color) {
            let html = '<div style="font-weight: 600; color: ' + color + '; margin-bottom: 8px;">Registry Status ' + label + ':</div>';
            
            // Add registry status information
            const hydratedIcon = machineHydrated ? '💧' : '🔄';
            const onlineIcon = '🟢';
            html += '<div style="font-family: monospace; font-size: 11px; line-height: 1.4; margin-bottom: 12px;">';
            html += 'Status: ' + registryStatus + '<br>';
            html += 'Hydrated: ' + hydratedIcon + ' ' + (machineHydrated ? 'Yes' : 'No') + '<br>';
            html += 'Online: ' + onlineIcon + ' Yes';
            html += '</div>';
            
            // Parse and display context
            if (contextData) {
                // Separate persistent and volatile fields
                const persistentFields = {};
                const volatileFields = {};
                
                // If contextData is a string, try to parse it
                let contextObj = contextData;
                if (typeof contextData === 'string') {
                    try {
                        contextObj = JSON.parse(contextData);
                    } catch (e) {
                        // If parsing fails, show as is
                        html += '<div style="margin-bottom: 8px;">';
                        html += '<div style="font-weight: 600; color: #495057; margin-bottom: 4px; font-size: 11px;">Context:</div>';
                        html += '<pre style="margin: 0; font-size: 10px; font-family: monospace; background: #f8f9fa; padding: 8px; border-radius: 4px; line-height: 1.3;">' + contextData + '</pre>';
                        html += '</div>';
                        return html;
                    }
                }
                
                // Categorize fields
                if (contextObj && typeof contextObj === 'object') {
                    Object.entries(contextObj).forEach(([key, value]) => {
                        // Entity fields for CallContext
                        if (key.match(/^(callId|fromNumber|toNumber|currentState|callDirection|callStatus|ringCount|recordingEnabled|complete|id|registryStatus)$/i) || 
                            key.endsWith('Id') || key.endsWith('Status') || key.endsWith('State') || key.endsWith('Time')) {
                            persistentFields[key] = value;
                        } else {
                            volatileFields[key] = value;
                        }
                    });
                }
                
                // Display Persistent Context
                html += '<div style="margin-bottom: 8px;">';
                html += '<div style="font-weight: 600; color: #495057; margin-bottom: 4px; font-size: 11px;">Persistent Context:</div>';
                html += '<pre style="margin: 0; font-size: 10px; font-family: monospace; background: #f8f9fa; padding: 8px; border-radius: 4px; line-height: 1.3;">';
                html += JSON.stringify(persistentFields, null, 2);
                html += '</pre>';
                html += '</div>';
                
                // Display Volatile Context
                if (Object.keys(volatileFields).length > 0) {
                    html += '<div>';
                    html += '<div style="font-weight: 600; color: #495057; margin-bottom: 4px; font-size: 11px;">Volatile Context:</div>';
                    html += '<pre style="margin: 0; font-size: 10px; font-family: monospace; background: #f8f9fa; padding: 8px; border-radius: 4px; line-height: 1.3;">';
                    html += JSON.stringify(volatileFields, null, 2);
                    html += '</pre>';
                    html += '</div>';
                } else {
                    html += '<div>';
                    html += '<div style="font-weight: 600; color: #495057; margin-bottom: 4px; font-size: 11px;">Volatile Context:</div>';
                    html += '<pre style="margin: 0; font-size: 10px; font-family: monospace; background: #f8f9fa; padding: 8px; border-radius: 4px; line-height: 1.3;">{}</pre>';
                    html += '</div>';
                }
            } else {
                // Still show empty sections
                html += '<div style="margin-bottom: 8px;">';
                html += '<div style="font-weight: 600; color: #495057; margin-bottom: 4px; font-size: 11px;">Persistent Context:</div>';
                html += '<pre style="margin: 0; font-size: 10px; font-family: monospace; background: #f8f9fa; padding: 8px; border-radius: 4px; line-height: 1.3;">{}</pre>';
                html += '</div>';
                html += '<div>';
                html += '<div style="font-weight: 600; color: #495057; margin-bottom: 4px; font-size: 11px;">Volatile Context:</div>';
                html += '<pre style="margin: 0; font-size: 10px; font-family: monospace; background: #f8f9fa; padding: 8px; border-radius: 4px; line-height: 1.3;">{}</pre>';
                html += '</div>';
            }
            
            return html;
        }
        
        function updateTransitionCount() {
            const countEl = document.getElementById('transitionCount');
            if (countEl) {
                countEl.textContent = transitionCounter;
            }
        }
        
        function sendEvent() {
            if (!ws || ws.readyState !== WebSocket.OPEN) {
                alert('WebSocket is not connected. Please wait for connection...');
                return;
            }
            
            const eventType = document.getElementById('eventType').value;
            const payloadText = document.getElementById('eventPayload').value;
            
            let payload = {};
            try {
                payload = JSON.parse(payloadText);
            } catch (error) {
                alert('Invalid JSON payload. Please check your syntax.');
                return;
            }
            
            const message = {
                type: 'EVENT',
                machineId: 'CallMachine',
                eventType: eventType,
                payload: payload
            };
            
            ws.send(JSON.stringify(message));
            console.log('Sent event:', message);
            
            // Visual feedback
            const btn = document.querySelector('.send-event-btn');
            const originalText = btn.textContent;
            btn.textContent = '✅ Sent!';
            btn.style.background = '#28a745';
            setTimeout(() => {
                btn.textContent = originalText;
                btn.style.background = '';
            }, 1000);
        }
        
        function togglePause() {
            isPaused = !isPaused;
            const btn = document.getElementById('pauseBtn');
            if (btn) {
                btn.textContent = isPaused ? '▶️ Resume Feed' : '⏸️ Pause Feed';
            }
        }
        
        function clearTransitions() {
            liveTransitions = [];
            transitionCounter = 0;
            updateLiveHistory();
            updateTransitionCount();
        }
        
        function exportSession() {
            const dataStr = JSON.stringify(liveTransitions, null, 2);
            const dataUri = 'data:application/json;charset=utf-8,'+ encodeURIComponent(dataStr);
            
            const exportFileDefaultName = 'live-session-' + new Date().toISOString() + '.json';
            
            const linkElement = document.createElement('a');
            linkElement.setAttribute('href', dataUri);
            linkElement.setAttribute('download', exportFileDefaultName);
            linkElement.click();
        }
    </script>
</body>
</html>
        """);
        
        return page.toString().replace("${webSocketPort}", String.valueOf(webSocketPort));
    }
    
    // Data classes (same as EnhancedMonitoringServer)
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
        if (dbConnection != null) {
            try {
                dbConnection.close();
            } catch (SQLException e) {
                // Ignore
            }
        }
    }
}