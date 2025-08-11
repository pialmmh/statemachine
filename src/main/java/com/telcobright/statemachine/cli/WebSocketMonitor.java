package com.telcobright.statemachine.cli;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * WebSocket-based State Machine Monitor
 */
public class WebSocketMonitor extends WebSocketClient {
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final String DEFAULT_URL = "ws://localhost:9999";
    
    // ANSI codes
    private static final String ESC = "\033[";
    private static final String CLEAR = ESC + "2J";
    private static final String HOME = ESC + "H";
    private static final String HIDE_CURSOR = ESC + "?25l";
    private static final String SHOW_CURSOR = ESC + "?25h";
    
    // Colors
    private static final String RESET = ESC + "0m";
    private static final String GREEN = ESC + "32m";
    private static final String YELLOW = ESC + "33m";
    private static final String CYAN = ESC + "36m";
    private static final String WHITE = ESC + "37m";
    private static final String RED = ESC + "31m";
    private static final String BG_GREEN = ESC + "42;30m";
    private static final String BG_YELLOW = ESC + "43;30m";
    private static final String BG_BLUE = ESC + "44;37m";
    private static final String BG_RED = ESC + "41;37m";
    
    private final Gson gson = new Gson();
    private volatile boolean running = true;
    
    // Current state
    private String machineId = "Not connected";
    private String currentState = "UNKNOWN";
    private String callStatus = "";
    private int ringCount = 0;
    private String fromNumber = "";
    private String toNumber = "";
    private String connectionStatus = "DISCONNECTED";
    private final List<String> eventHistory = new ArrayList<>();
    private final AtomicInteger eventCount = new AtomicInteger(0);
    
    public WebSocketMonitor(URI serverUri) {
        super(serverUri);
    }
    
    @Override
    public void onOpen(ServerHandshake handshake) {
        connectionStatus = "CONNECTED";
        System.out.println(GREEN + "Connected to WebSocket server" + RESET);
        
        // Request initial state
        JsonObject request = new JsonObject();
        request.addProperty("action", "GET_STATE");
        send(gson.toJson(request));
        
        drawUI();
    }
    
    @Override
    public void onMessage(String message) {
        try {
            JsonObject event = gson.fromJson(message, JsonObject.class);
            String type = event.get("type").getAsString();
            
            switch (type) {
                case "CURRENT_STATE":
                case "PERIODIC_UPDATE":
                    updateFromJson(event);
                    break;
                case "STATE_CHANGE":
                    handleStateChange(event);
                    break;
                case "REGISTRY_CREATE":
                    addEvent("Registry created: " + event.get("machineId").getAsString());
                    break;
                case "REGISTRY_REMOVE":
                    addEvent("Registry removed: " + event.get("machineId").getAsString());
                    break;
            }
            
            updateDisplay();
        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
        }
    }
    
    @Override
    public void onClose(int code, String reason, boolean remote) {
        connectionStatus = "DISCONNECTED";
        System.out.println(RED + "\nDisconnected from server: " + reason + RESET);
        running = false;
    }
    
    @Override
    public void onError(Exception ex) {
        System.err.println(RED + "WebSocket error: " + ex.getMessage() + RESET);
    }
    
    private void updateFromJson(JsonObject event) {
        if (event.has("machineId")) {
            machineId = event.get("machineId").getAsString();
        }
        if (event.has("currentState")) {
            currentState = event.get("currentState").getAsString();
        }
        
        if (event.has("context")) {
            JsonObject context = event.getAsJsonObject("context");
            if (context.has("callStatus")) {
                callStatus = context.get("callStatus").getAsString();
            }
            if (context.has("ringCount")) {
                ringCount = context.get("ringCount").getAsInt();
            }
            if (context.has("fromNumber")) {
                fromNumber = context.get("fromNumber").getAsString();
            }
            if (context.has("toNumber")) {
                toNumber = context.get("toNumber").getAsString();
            }
        }
    }
    
    private void handleStateChange(JsonObject event) {
        String oldState = event.get("oldState").getAsString();
        String newState = event.get("newState").getAsString();
        addEvent(String.format("State: %s -> %s", oldState, newState));
        updateFromJson(event);
    }
    
    private void addEvent(String event) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        eventHistory.add(timestamp + " " + event);
        eventCount.incrementAndGet();
        
        // Keep only last 10 events
        while (eventHistory.size() > 10) {
            eventHistory.remove(0);
        }
    }
    
    private void drawUI() {
        System.out.print(HIDE_CURSOR + CLEAR + HOME);
        
        // Title
        System.out.println(CYAN + "═".repeat(80) + RESET);
        System.out.println(CYAN + "                     WEBSOCKET STATE MACHINE MONITOR" + RESET);
        System.out.println(CYAN + "═".repeat(80) + RESET);
        System.out.println();
        
        // Connection info
        System.out.println("┌" + "─".repeat(78) + "┐");
        System.out.println("│ " + YELLOW + "CONNECTION INFO" + RESET + " ".repeat(61) + "│");
        System.out.println("├" + "─".repeat(78) + "┤");
        System.out.println("│ Server: " + getURI() + " ".repeat(80 - 11 - getURI().toString().length()) + "│");
        System.out.println("│ Status:                                                                      │");
        System.out.println("│ Machine ID:                                                                  │");
        System.out.println("└" + "─".repeat(78) + "┘");
        System.out.println();
        
        // State info
        System.out.println("┌" + "─".repeat(38) + "┬" + "─".repeat(39) + "┐");
        System.out.println("│ " + YELLOW + "STATE INFO" + RESET + " ".repeat(26) + "│ " + YELLOW + "CALL CONTEXT" + RESET + " ".repeat(25) + "│");
        System.out.println("├" + "─".repeat(38) + "┼" + "─".repeat(39) + "┤");
        System.out.println("│ Current State:                      │ From:                                 │");
        System.out.println("│ Call Status:                        │ To:                                   │");
        System.out.println("│ Events Count:                       │ Ring Count:                           │");
        System.out.println("└" + "─".repeat(38) + "┴" + "─".repeat(39) + "┘");
        System.out.println();
        
        // Event history
        System.out.println("┌" + "─".repeat(78) + "┐");
        System.out.println("│ " + CYAN + "EVENT HISTORY" + RESET + " ".repeat(63) + "│");
        System.out.println("├" + "─".repeat(78) + "┤");
        for (int i = 0; i < 10; i++) {
            System.out.println("│ " + " ".repeat(76) + "│");
        }
        System.out.println("└" + "─".repeat(78) + "┘");
        System.out.println();
        
        // Commands
        System.out.println("COMMANDS: " + GREEN + "[1]" + RESET + " CALL  " +
                          GREEN + "[2]" + RESET + " ANSWER  " +
                          GREEN + "[3]" + RESET + " HANGUP  " +
                          GREEN + "[4]" + RESET + " PROGRESS  " +
                          RED + "[Q]" + RESET + " QUIT");
        System.out.println();
        System.out.print("Enter command: ");
        System.out.flush();
    }
    
    private void updateDisplay() {
        // Update connection status
        moveTo(9, 11);
        String statusColor = connectionStatus.equals("CONNECTED") ? GREEN : RED;
        System.out.print(statusColor + String.format("%-20s", connectionStatus) + RESET);
        
        // Update machine ID
        moveTo(10, 14);
        System.out.print(WHITE + String.format("%-40s", machineId) + RESET);
        
        // Update current state
        moveTo(14, 18);
        String stateColor = getStateColor(currentState);
        System.out.print(stateColor + String.format("%-18s", currentState) + RESET);
        
        // Update call status
        moveTo(15, 16);
        System.out.print(WHITE + String.format("%-20s", callStatus) + RESET);
        
        // Update event count
        moveTo(16, 17);
        System.out.print(WHITE + String.format("%-10d", eventCount.get()) + RESET);
        
        // Update from/to numbers
        moveTo(14, 47);
        System.out.print(WHITE + String.format("%-30s", fromNumber) + RESET);
        
        moveTo(15, 45);
        System.out.print(WHITE + String.format("%-30s", toNumber) + RESET);
        
        // Update ring count
        moveTo(16, 54);
        System.out.print(WHITE + String.format("%-10d", ringCount) + RESET);
        
        // Update event history
        for (int i = 0; i < 10; i++) {
            moveTo(21 + i, 3);
            if (i < eventHistory.size()) {
                System.out.print(String.format("%-76s", eventHistory.get(i)));
            } else {
                System.out.print(" ".repeat(76));
            }
        }
        
        // Return cursor to command line
        moveTo(34, 16);
        System.out.flush();
    }
    
    private String getStateColor(String state) {
        switch (state) {
            case "IDLE": return BG_GREEN;
            case "RINGING": return BG_YELLOW;
            case "CONNECTED": return BG_BLUE;
            default: return BG_RED;
        }
    }
    
    private void moveTo(int row, int col) {
        System.out.print(ESC + row + ";" + col + "H");
    }
    
    private void sendCommand(String action) {
        JsonObject request = new JsonObject();
        request.addProperty("action", action);
        send(gson.toJson(request));
    }
    
    public void handleInput() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (running && isOpen()) {
                if (reader.ready()) {
                    String input = reader.readLine().trim().toLowerCase();
                    
                    // Clear input
                    moveTo(34, 16);
                    System.out.print(" ".repeat(20));
                    moveTo(34, 16);
                    
                    switch (input) {
                        case "1":
                            sendCommand("INCOMING_CALL");
                            addEvent("Sent: INCOMING_CALL");
                            break;
                        case "2":
                            sendCommand("ANSWER");
                            addEvent("Sent: ANSWER");
                            break;
                        case "3":
                            sendCommand("HANGUP");
                            addEvent("Sent: HANGUP");
                            break;
                        case "4":
                            sendCommand("SESSION_PROGRESS");
                            addEvent("Sent: SESSION_PROGRESS");
                            break;
                        case "q":
                            running = false;
                            break;
                    }
                    
                    updateDisplay();
                } else {
                    Thread.sleep(100);
                }
            }
        } catch (Exception e) {
            System.err.println("Error handling input: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        String url = DEFAULT_URL;
        if (args.length > 0) {
            url = args[0];
        }
        
        try {
            System.out.println("Connecting to: " + url);
            WebSocketMonitor client = new WebSocketMonitor(new URI(url));
            client.connect();
            
            // Wait for connection
            Thread.sleep(1000);
            
            // Handle input
            client.handleInput();
            
            // Cleanup
            client.close();
            System.out.print(SHOW_CURSOR);
            System.out.println("\n" + GREEN + "Goodbye!" + RESET);
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}