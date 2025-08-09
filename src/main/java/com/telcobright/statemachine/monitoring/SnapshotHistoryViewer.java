package com.telcobright.statemachine.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTML viewer generator for state machine snapshot history.
 * Creates interactive HTML files for visualizing state transitions and context changes.
 */
public class SnapshotHistoryViewer {
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    
    private final Map<String, List<SnapshotData>> machineSnapshots = new ConcurrentHashMap<>();
    private final SnapshotSerializationUtils serializationUtils = new SnapshotSerializationUtils();
    
    /**
     * Add a snapshot to the viewer's history
     */
    public void addSnapshot(String machineId, String machineType, Long version,
                           String stateBefore, String stateAfter, String eventType,
                           String contextBeforeJson, String contextAfterJson,
                           String contextBeforeHash, String contextAfterHash,
                           long transitionDuration, LocalDateTime timestamp,
                           String runId, String correlationId,
                           String eventPayloadJson, String eventParametersJson,
                           Boolean machineOnlineStatus, Boolean stateOfflineStatus,
                           String registryStatus) {
        
        SnapshotData snapshot = new SnapshotData(
            machineId, machineType, version, stateBefore, stateAfter, eventType,
            contextBeforeJson, contextAfterJson, contextBeforeHash, contextAfterHash,
            transitionDuration, timestamp, runId, correlationId,
            eventPayloadJson, eventParametersJson, machineOnlineStatus, stateOfflineStatus, registryStatus
        );
        
        machineSnapshots.computeIfAbsent(machineId, k -> new ArrayList<>()).add(snapshot);
    }
    
    /**
     * Generate HTML viewer for a specific machine's history
     */
    public void generateHtmlViewer(String machineId, String outputPath) throws IOException {
        List<SnapshotData> snapshots = machineSnapshots.get(machineId);
        if (snapshots == null || snapshots.isEmpty()) {
            System.out.println("No snapshots found for machine: " + machineId);
            return;
        }
        
        String html = generateHtml(machineId, snapshots);
        
        File outputFile = new File(outputPath);
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(html);
        }
        
        System.out.println("📊 HTML viewer generated: " + outputFile.getAbsolutePath());
    }
    
    /**
     * Generate HTML viewer for all machines
     */
    public void generateCombinedHtmlViewer(String outputPath) throws IOException {
        String html = generateCombinedHtml(machineSnapshots);
        
        File outputFile = new File(outputPath);
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(html);
        }
        
        System.out.println("📊 Combined HTML viewer generated: " + outputFile.getAbsolutePath());
    }
    
    private String generateHtml(String machineId, List<SnapshotData> snapshots) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>State Machine History - ").append(machineId).append("</title>\n");
        html.append(getStyles());
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"container\">\n");
        html.append("        <h1>State Machine Transition History</h1>\n");
        html.append("        <div class=\"machine-info\">\n");
        html.append("            <h2>Machine: ").append(machineId).append("</h2>\n");
        html.append("            <p>Total Transitions: ").append(snapshots.size()).append("</p>\n");
        if (!snapshots.isEmpty()) {
            SnapshotData first = snapshots.get(0);
            html.append("            <p>Type: ").append(first.machineType).append("</p>\n");
            html.append("            <p>Run ID: ").append(first.runId != null ? first.runId : "N/A").append("</p>\n");
        }
        html.append("        </div>\n\n");
        
        // State diagram
        html.append("        <div class=\"state-diagram\">\n");
        html.append("            <h3>State Flow</h3>\n");
        html.append("            <div class=\"flow\">\n");
        for (int i = 0; i < snapshots.size(); i++) {
            SnapshotData snapshot = snapshots.get(i);
            if (i == 0) {
                html.append("                <span class=\"state\">").append(snapshot.stateBefore).append("</span>\n");
            }
            html.append("                <span class=\"arrow\">→</span>\n");
            html.append("                <span class=\"event\">[").append(snapshot.eventType).append("]</span>\n");
            html.append("                <span class=\"arrow\">→</span>\n");
            html.append("                <span class=\"state").append(i == snapshots.size() - 1 ? " final" : "")
                .append("\">").append(snapshot.stateAfter).append("</span>\n");
        }
        html.append("            </div>\n");
        html.append("        </div>\n\n");
        
        // Timeline
        html.append("        <div class=\"timeline\">\n");
        html.append("            <h3>Transition Timeline</h3>\n");
        for (SnapshotData snapshot : snapshots) {
            html.append(generateTimelineEntry(snapshot));
        }
        html.append("        </div>\n");
        
        html.append("    </div>\n");
        html.append(getScripts());
        html.append("</body>\n");
        html.append("</html>\n");
        
        return html.toString();
    }
    
    private String generateCombinedHtml(Map<String, List<SnapshotData>> allSnapshots) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>All State Machines History</title>\n");
        html.append(getStyles());
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"container\">\n");
        html.append("        <h1>All State Machines History</h1>\n");
        html.append("        <div class=\"summary\">\n");
        html.append("            <p>Total Machines: ").append(allSnapshots.size()).append("</p>\n");
        html.append("        </div>\n\n");
        
        // Machine selector
        html.append("        <div class=\"machine-selector\">\n");
        html.append("            <label>Select Machine: </label>\n");
        html.append("            <select id=\"machineSelect\" onchange=\"showMachine(this.value)\">\n");
        for (String machineId : allSnapshots.keySet()) {
            html.append("                <option value=\"").append(machineId).append("\">")
                .append(machineId).append(" (").append(allSnapshots.get(machineId).size())
                .append(" transitions)</option>\n");
        }
        html.append("            </select>\n");
        html.append("        </div>\n\n");
        
        // Machine content divs
        for (Map.Entry<String, List<SnapshotData>> entry : allSnapshots.entrySet()) {
            String machineId = entry.getKey();
            List<SnapshotData> snapshots = entry.getValue();
            
            html.append("        <div class=\"machine-content\" id=\"machine-").append(machineId)
                .append("\" style=\"display: ").append(machineId.equals(allSnapshots.keySet().iterator().next()) ? "block" : "none").append("\">\n");
            
            // State diagram for this machine
            html.append("            <div class=\"state-diagram\">\n");
            html.append("                <h3>State Flow</h3>\n");
            html.append("                <div class=\"flow\">\n");
            for (int i = 0; i < snapshots.size(); i++) {
                SnapshotData snapshot = snapshots.get(i);
                if (i == 0) {
                    html.append("                    <span class=\"state\">").append(snapshot.stateBefore).append("</span>\n");
                }
                html.append("                    <span class=\"arrow\">→</span>\n");
                html.append("                    <span class=\"event\">[").append(snapshot.eventType).append("]</span>\n");
                html.append("                    <span class=\"arrow\">→</span>\n");
                html.append("                    <span class=\"state").append(i == snapshots.size() - 1 ? " final" : "")
                    .append("\">").append(snapshot.stateAfter).append("</span>\n");
            }
            html.append("                </div>\n");
            html.append("            </div>\n\n");
            
            // Timeline for this machine
            html.append("            <div class=\"timeline\">\n");
            html.append("                <h3>Transition Timeline</h3>\n");
            for (SnapshotData snapshot : snapshots) {
                html.append(generateTimelineEntry(snapshot));
            }
            html.append("            </div>\n");
            html.append("        </div>\n");
        }
        
        html.append("    </div>\n");
        html.append(getScripts());
        html.append("</body>\n");
        html.append("</html>\n");
        
        return html.toString();
    }
    
    private String generateTimelineEntry(SnapshotData snapshot) {
        StringBuilder entry = new StringBuilder();
        entry.append("            <div class=\"timeline-entry\">\n");
        entry.append("                <div class=\"timeline-header\" onclick=\"toggleDetails('snapshot-").append(snapshot.version).append("')\">\n");
        entry.append("                    <span class=\"version\">v").append(snapshot.version).append("</span>\n");
        entry.append("                    <span class=\"transition\">").append(snapshot.stateBefore)
             .append(" → ").append(snapshot.stateAfter).append("</span>\n");
        entry.append("                    <span class=\"event-badge\">").append(snapshot.eventType).append("</span>\n");
        entry.append("                    <span class=\"duration\">").append(snapshot.transitionDuration).append("ms</span>\n");
        
        // Add status indicators
        if (snapshot.machineOnlineStatus != null) {
            String statusClass = snapshot.machineOnlineStatus ? "status-online" : "status-offline";
            String statusText = snapshot.machineOnlineStatus ? "ONLINE" : "OFFLINE";
            entry.append("                    <span class=\"machine-status ").append(statusClass).append("\">").append(statusText).append("</span>\n");
        }
        
        if (snapshot.stateOfflineStatus != null && snapshot.stateOfflineStatus) {
            entry.append("                    <span class=\"state-status status-offline\">STATE OFFLINE</span>\n");
        }
        
        entry.append("                    <span class=\"timestamp\">").append(snapshot.timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("</span>\n");
        entry.append("                </div>\n");
        
        entry.append("                <div class=\"timeline-details\" id=\"snapshot-").append(snapshot.version).append("\" style=\"display: none;\">\n");
        
        // Status Information
        entry.append("                    <div class=\"status-section\">\n");
        entry.append("                        <h4>Status Information:</h4>\n");
        entry.append("                        <div class=\"status-grid\">\n");
        if (snapshot.machineOnlineStatus != null) {
            entry.append("                            <div class=\"status-item\">Machine Status: <span class=\"")
                .append(snapshot.machineOnlineStatus ? "status-online" : "status-offline")
                .append("\">").append(snapshot.machineOnlineStatus ? "ONLINE" : "OFFLINE").append("</span></div>\n");
        }
        if (snapshot.stateOfflineStatus != null) {
            entry.append("                            <div class=\"status-item\">State Mode: <span class=\"")
                .append(snapshot.stateOfflineStatus ? "status-offline" : "status-online")
                .append("\">").append(snapshot.stateOfflineStatus ? "OFFLINE" : "ONLINE").append("</span></div>\n");
        }
        if (snapshot.registryStatus != null) {
            entry.append("                            <div class=\"status-item\">Registry: <span class=\"registry-status\">").append(snapshot.registryStatus).append("</span></div>\n");
        }
        entry.append("                        </div>\n");
        entry.append("                    </div>\n");
        
        // Event Details
        if (snapshot.eventPayloadJson != null || snapshot.eventParametersJson != null) {
            entry.append("                    <div class=\"event-section\">\n");
            entry.append("                        <h4>Event Details:</h4>\n");
            if (snapshot.eventPayloadJson != null) {
                entry.append("                        <h5>Full Event Payload:</h5>\n");
                entry.append("                        <pre class=\"json-display\">").append(formatJson(snapshot.eventPayloadJson)).append("</pre>\n");
            }
            if (snapshot.eventParametersJson != null) {
                entry.append("                        <h5>Event Parameters:</h5>\n");
                entry.append("                        <pre class=\"json-display\">").append(formatJson(snapshot.eventParametersJson)).append("</pre>\n");
            }
            entry.append("                    </div>\n");
        }
        
        // Context before
        if (snapshot.contextBeforeJson != null) {
            entry.append("                    <div class=\"context-section\">\n");
            entry.append("                        <h4>Context Before:</h4>\n");
            String decodedBefore = serializationUtils.decodeBase64Json(snapshot.contextBeforeJson);
            entry.append("                        <pre class=\"json-display\">").append(formatJson(decodedBefore)).append("</pre>\n");
            if (snapshot.contextBeforeHash != null) {
                entry.append("                        <p class=\"hash\">Hash: ").append(snapshot.contextBeforeHash.substring(0, 16)).append("...</p>\n");
            }
            entry.append("                    </div>\n");
        }
        
        // Context after
        if (snapshot.contextAfterJson != null) {
            entry.append("                    <div class=\"context-section\">\n");
            entry.append("                        <h4>Context After:</h4>\n");
            String decodedAfter = serializationUtils.decodeBase64Json(snapshot.contextAfterJson);
            entry.append("                        <pre class=\"json-display\">").append(formatJson(decodedAfter)).append("</pre>\n");
            if (snapshot.contextAfterHash != null) {
                entry.append("                        <p class=\"hash\">Hash: ").append(snapshot.contextAfterHash.substring(0, 16)).append("...</p>\n");
            }
            entry.append("                    </div>\n");
        }
        
        // Show diff if both contexts exist
        if (snapshot.contextBeforeJson != null && snapshot.contextAfterJson != null) {
            entry.append("                    <div class=\"diff-section\">\n");
            entry.append("                        <h4>Context Changes:</h4>\n");
            entry.append("                        <div class=\"diff-display\" id=\"diff-").append(snapshot.version).append("\"></div>\n");
            entry.append("                    </div>\n");
        }
        
        entry.append("                </div>\n");
        entry.append("            </div>\n");
        
        return entry.toString();
    }
    
    private String formatJson(String json) {
        try {
            Object jsonObject = OBJECT_MAPPER.readValue(json, Object.class);
            return OBJECT_MAPPER.writeValueAsString(jsonObject);
        } catch (Exception e) {
            return json;
        }
    }
    
    private String getStyles() {
        return """
            <style>
                * {
                    margin: 0;
                    padding: 0;
                    box-sizing: border-box;
                }
                
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    min-height: 100vh;
                    padding: 20px;
                }
                
                .container {
                    max-width: 1200px;
                    margin: 0 auto;
                    background: white;
                    border-radius: 16px;
                    padding: 30px;
                    box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                }
                
                h1 {
                    color: #333;
                    margin-bottom: 20px;
                    font-size: 2em;
                }
                
                h2 {
                    color: #555;
                    margin-bottom: 15px;
                    font-size: 1.5em;
                }
                
                h3 {
                    color: #666;
                    margin-bottom: 15px;
                    font-size: 1.2em;
                }
                
                .machine-info {
                    background: #f8f9fa;
                    padding: 20px;
                    border-radius: 8px;
                    margin-bottom: 30px;
                }
                
                .machine-info p {
                    color: #666;
                    margin: 5px 0;
                }
                
                .machine-selector {
                    margin-bottom: 30px;
                    padding: 15px;
                    background: #f0f0f0;
                    border-radius: 8px;
                }
                
                .machine-selector select {
                    padding: 8px 15px;
                    font-size: 16px;
                    border: 2px solid #667eea;
                    border-radius: 4px;
                    background: white;
                    cursor: pointer;
                }
                
                .state-diagram {
                    background: #f0f8ff;
                    padding: 20px;
                    border-radius: 8px;
                    margin-bottom: 30px;
                    overflow-x: auto;
                }
                
                .flow {
                    display: flex;
                    align-items: center;
                    gap: 10px;
                    padding: 15px;
                    white-space: nowrap;
                }
                
                .state {
                    background: #667eea;
                    color: white;
                    padding: 10px 20px;
                    border-radius: 20px;
                    font-weight: bold;
                }
                
                .state.final {
                    background: #28a745;
                }
                
                .event {
                    background: #ffc107;
                    color: #333;
                    padding: 5px 10px;
                    border-radius: 4px;
                    font-size: 0.9em;
                }
                
                .arrow {
                    color: #999;
                    font-size: 1.5em;
                }
                
                .timeline {
                    margin-top: 30px;
                }
                
                .timeline-entry {
                    margin-bottom: 20px;
                    border: 1px solid #e0e0e0;
                    border-radius: 8px;
                    overflow: hidden;
                }
                
                .timeline-header {
                    display: flex;
                    align-items: center;
                    gap: 15px;
                    padding: 15px;
                    background: #f8f9fa;
                    cursor: pointer;
                    transition: background 0.3s;
                }
                
                .timeline-header:hover {
                    background: #e9ecef;
                }
                
                .version {
                    background: #6c757d;
                    color: white;
                    padding: 4px 8px;
                    border-radius: 4px;
                    font-size: 0.9em;
                }
                
                .transition {
                    font-weight: 600;
                    color: #333;
                }
                
                .event-badge {
                    background: #17a2b8;
                    color: white;
                    padding: 4px 12px;
                    border-radius: 12px;
                    font-size: 0.85em;
                }
                
                .duration {
                    color: #28a745;
                    font-size: 0.9em;
                    margin-left: auto;
                }
                
                .timestamp {
                    color: #999;
                    font-size: 0.85em;
                }
                
                .timeline-details {
                    padding: 20px;
                    background: white;
                    border-top: 1px solid #e0e0e0;
                }
                
                .context-section {
                    margin-bottom: 20px;
                }
                
                .context-section h4 {
                    color: #555;
                    margin-bottom: 10px;
                }
                
                .json-display {
                    background: #f6f8fa;
                    padding: 15px;
                    border-radius: 6px;
                    font-family: 'Courier New', monospace;
                    font-size: 0.9em;
                    overflow-x: auto;
                    max-height: 300px;
                    overflow-y: auto;
                }
                
                .hash {
                    color: #999;
                    font-size: 0.85em;
                    margin-top: 5px;
                    font-family: monospace;
                }
                
                .diff-section {
                    margin-top: 20px;
                    padding-top: 20px;
                    border-top: 1px solid #e0e0e0;
                }
                
                .diff-display {
                    background: #f6f8fa;
                    padding: 15px;
                    border-radius: 6px;
                    font-family: monospace;
                    font-size: 0.9em;
                }
                
                .diff-add {
                    background: #d4edda;
                    color: #155724;
                    padding: 2px 4px;
                    border-radius: 2px;
                }
                
                .diff-remove {
                    background: #f8d7da;
                    color: #721c24;
                    padding: 2px 4px;
                    border-radius: 2px;
                }
                
                .summary {
                    background: #e8f4f8;
                    padding: 15px;
                    border-radius: 8px;
                    margin-bottom: 20px;
                }
                
                .machine-status, .state-status {
                    padding: 2px 8px;
                    border-radius: 4px;
                    font-size: 0.8em;
                    font-weight: bold;
                }
                
                .status-online {
                    background: #28a745;
                    color: white;
                }
                
                .status-offline {
                    background: #dc3545;
                    color: white;
                }
                
                .registry-status {
                    background: #6c757d;
                    color: white;
                    padding: 2px 8px;
                    border-radius: 4px;
                    font-size: 0.8em;
                }
                
                .status-section {
                    margin-bottom: 20px;
                    padding: 15px;
                    background: #f8f9fa;
                    border-radius: 6px;
                }
                
                .status-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                    gap: 10px;
                    margin-top: 10px;
                }
                
                .status-item {
                    font-size: 0.9em;
                    color: #333;
                }
                
                .event-section {
                    margin-bottom: 20px;
                    padding: 15px;
                    background: #fff3cd;
                    border-radius: 6px;
                }
                
                .event-section h5 {
                    color: #856404;
                    margin: 10px 0 5px 0;
                    font-size: 1em;
                }
            </style>
            """;
    }
    
    private String getScripts() {
        return """
            <script>
                function toggleDetails(id) {
                    const element = document.getElementById(id);
                    if (element) {
                        element.style.display = element.style.display === 'none' ? 'block' : 'none';
                    }
                }
                
                function showMachine(machineId) {
                    // Hide all machine contents
                    const allContents = document.querySelectorAll('.machine-content');
                    allContents.forEach(content => {
                        content.style.display = 'none';
                    });
                    
                    // Show selected machine content
                    const selected = document.getElementById('machine-' + machineId);
                    if (selected) {
                        selected.style.display = 'block';
                    }
                }
                
                // Initialize first machine as visible
                document.addEventListener('DOMContentLoaded', function() {
                    const firstMachine = document.querySelector('.machine-content');
                    if (firstMachine) {
                        firstMachine.style.display = 'block';
                    }
                });
            </script>
            """;
    }
    
    /**
     * Internal snapshot data structure
     */
    private static class SnapshotData {
        final String machineId;
        final String machineType;
        final Long version;
        final String stateBefore;
        final String stateAfter;
        final String eventType;
        final String contextBeforeJson;
        final String contextAfterJson;
        final String contextBeforeHash;
        final String contextAfterHash;
        final long transitionDuration;
        final LocalDateTime timestamp;
        final String runId;
        final String correlationId;
        final String eventPayloadJson;
        final String eventParametersJson;
        final Boolean machineOnlineStatus;
        final Boolean stateOfflineStatus;
        final String registryStatus;
        
        SnapshotData(String machineId, String machineType, Long version,
                    String stateBefore, String stateAfter, String eventType,
                    String contextBeforeJson, String contextAfterJson,
                    String contextBeforeHash, String contextAfterHash,
                    long transitionDuration, LocalDateTime timestamp,
                    String runId, String correlationId,
                    String eventPayloadJson, String eventParametersJson,
                    Boolean machineOnlineStatus, Boolean stateOfflineStatus,
                    String registryStatus) {
            this.machineId = machineId;
            this.machineType = machineType;
            this.version = version;
            this.stateBefore = stateBefore;
            this.stateAfter = stateAfter;
            this.eventType = eventType;
            this.contextBeforeJson = contextBeforeJson;
            this.contextAfterJson = contextAfterJson;
            this.contextBeforeHash = contextBeforeHash;
            this.contextAfterHash = contextAfterHash;
            this.transitionDuration = transitionDuration;
            this.timestamp = timestamp;
            this.runId = runId;
            this.correlationId = correlationId;
            this.eventPayloadJson = eventPayloadJson;
            this.eventParametersJson = eventParametersJson;
            this.machineOnlineStatus = machineOnlineStatus;
            this.stateOfflineStatus = stateOfflineStatus;
            this.registryStatus = registryStatus;
        }
    }
}