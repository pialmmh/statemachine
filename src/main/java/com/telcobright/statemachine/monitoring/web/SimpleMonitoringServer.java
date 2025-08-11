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
    
    public static void main(String[] args) throws Exception {
        SimpleMonitoringServer monitor = new SimpleMonitoringServer();
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
            
        return """
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
                // Switch to live mode - would need WebSocket implementation
                alert('Live mode is under development. Please use snapshot mode for now.');
                setMode('snapshot');
            } else {
                loadRuns();
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
    </script>
</body>
</html>
        """;
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