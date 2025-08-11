package com.telcobright.statemachine.cli;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.events.EventTypeRegistry;
import com.telcobright.statemachineexamples.callmachine.CallContext;
import com.telcobright.statemachineexamples.callmachine.CallState;
import com.telcobright.statemachineexamples.callmachine.events.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Console-based State Machine Runner with real-time monitoring
 * Organized layout with rectangular sections for better visibility
 */
public class ConsoleStateRunner {
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final String MACHINE_ID = "console-" + System.currentTimeMillis();
    
    private final StateMachineRegistry registry = new StateMachineRegistry();
    private GenericStateMachine<CallContext, Void> machine;
    private CallContext context;
    
    // History tracking
    private final List<String> eventHistory = new ArrayList<>();
    private final List<String> stateHistory = new ArrayList<>();
    private final AtomicInteger eventCounter = new AtomicInteger(0);
    private String lastEventName = "None";
    private String lastTransition = "None";
    
    // Auto-refresh scheduler
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private volatile boolean needsRefresh = false;
    
    // Console colors
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String PURPLE = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";
    private static final String WHITE = "\u001B[37m";
    private static final String BOLD = "\u001B[1m";
    private static final String BG_GREEN = "\u001B[42m";
    private static final String BG_RED = "\u001B[41m";
    private static final String BG_YELLOW = "\u001B[43m";
    private static final String BG_BLUE = "\u001B[44m";
    private static final String BLACK = "\u001B[30m";
    
    // Box drawing characters
    private static final String TL = "┌"; // Top-left corner
    private static final String TR = "┐"; // Top-right corner
    private static final String BL = "└"; // Bottom-left corner
    private static final String BR = "┘"; // Bottom-right corner
    private static final String H = "─";  // Horizontal line
    private static final String V = "│";  // Vertical line
    private static final String T_DOWN = "┬"; // T-junction down
    private static final String T_UP = "┴"; // T-junction up
    private static final String T_RIGHT = "├"; // T-junction right
    private static final String T_LEFT = "┤"; // T-junction left
    private static final String CROSS = "┼"; // Cross junction
    
    public static void main(String[] args) {
        try {
            new ConsoleStateRunner().run();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void run() throws Exception {
        // Initialize
        registerEventTypes();
        initializeStateMachine();
        
        // Start auto-refresh thread
        startAutoRefresh();
        
        // Interactive loop
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        boolean running = true;
        
        displayDashboard();
        
        while (running) {
            // Check if refresh needed
            if (needsRefresh) {
                displayDashboard();
                needsRefresh = false;
            }
            
            // Non-blocking input check
            if (reader.ready()) {
                String input = reader.readLine().trim().toLowerCase();
                
                switch (input) {
                    case "1":
                        sendIncomingCall(reader);
                        displayDashboard();
                        break;
                    case "2":
                        sendAnswer();
                        displayDashboard();
                        break;
                    case "3":
                        sendHangup();
                        displayDashboard();
                        break;
                    case "4":
                        sendSessionProgress(reader);
                        displayDashboard();
                        break;
                    case "q":
                    case "quit":
                    case "exit":
                        running = false;
                        break;
                    default:
                        displayDashboard();
                        break;
                }
            } else {
                Thread.sleep(100); // Small delay to prevent CPU spinning
            }
        }
        
        cleanup();
        clearScreen();
        System.out.println(GREEN + "\nGoodbye!" + RESET);
    }
    
    private void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }
    
    private void displayDashboard() {
        clearScreen();
        
        // Calculate dynamic widths
        int termWidth = 100; // Assume 100 char width
        int leftWidth = 48;
        int rightWidth = 48;
        
        // Title Bar
        printTitleBar(termWidth);
        
        // Top row: Machine Info | Call Context
        System.out.println();
        printTopRow(leftWidth, rightWidth);
        
        // Middle row: Event History | State History
        System.out.println();
        printMiddleRow(leftWidth, rightWidth);
        
        // Menu bar
        System.out.println();
        printMenuBar(termWidth);
        
        // Status bar at bottom (highlighted)
        System.out.println();
        printStatusBar(termWidth);
        
        // Simple prompt
        System.out.print("\n" + CYAN + "Enter event number (1-4) or Q to quit: " + RESET);
        System.out.flush();
    }
    
    private void printTitleBar(int width) {
        String title = " INTERACTIVE STATE MACHINE MONITOR ";
        String timestamp = " [" + LocalDateTime.now().format(TIME_FORMAT) + "] ";
        int totalLen = title.length() + timestamp.length();
        int padding = (width - totalLen) / 2;
        System.out.println(CYAN + "═".repeat(padding) + BOLD + title + RESET + 
                          YELLOW + timestamp + RESET + 
                          CYAN + "═".repeat(width - padding - totalLen) + RESET);
    }
    
    private void printTopRow(int leftWidth, int rightWidth) {
        // Machine Info Box
        String[] machineInfo = getMachineInfo();
        
        // Call Context Box
        String[] callContext = getCallContext();
        
        // Print boxes side by side
        printTwoBoxes("MACHINE INFO", machineInfo, "CALL CONTEXT", callContext, leftWidth, rightWidth);
    }
    
    private void printMiddleRow(int leftWidth, int rightWidth) {
        // Event History Box
        String[] events = getRecentEvents(8);
        
        // State History Box
        String[] states = getRecentStates(8);
        
        // Print boxes side by side
        printTwoBoxes("EVENT HISTORY", events, "STATE TRANSITIONS", states, leftWidth, rightWidth);
    }
    
    private void printMenuBar(int width) {
        System.out.println(TL + H.repeat(width - 2) + TR);
        System.out.print(V + BOLD + " SEND EVENTS: " + RESET);
        System.out.print(GREEN + "[1]" + RESET + " INCOMING_CALL  ");
        System.out.print(GREEN + "[2]" + RESET + " ANSWER  ");
        System.out.print(GREEN + "[3]" + RESET + " HANGUP  ");
        System.out.print(GREEN + "[4]" + RESET + " SESSION_PROGRESS  ");
        System.out.print(RED + "[Q]" + RESET + " Quit");
        System.out.print(CYAN + "  (Auto-refresh: 2s)" + RESET);
        System.out.println(padRight("", width - 85) + V);
        System.out.println(BL + H.repeat(width - 2) + BR);
    }
    
    private void printStatusBar(int width) {
        String state = machine.getCurrentState();
        boolean isOnline = registry.isRegistered(MACHINE_ID);
        String registryStatus = isOnline ? "ONLINE" : "OFFLINE";
        
        // Build status line with highlighting
        String stateDisplay = getStateBackground(state) + BLACK + BOLD + " STATE: " + state + " " + RESET;
        String registryDisplay = (isOnline ? BG_GREEN : BG_RED) + BLACK + BOLD + " REGISTRY: " + registryStatus + " " + RESET;
        String eventDisplay = YELLOW + " Last Event: " + lastEventName + " " + RESET;
        String transitionDisplay = CYAN + " Last Transition: " + lastTransition + " " + RESET;
        
        // Print status bar with highlights
        System.out.println(BOLD + WHITE + "╔" + "═".repeat(width - 2) + "╗" + RESET);
        System.out.print(BOLD + WHITE + "║ " + RESET);
        System.out.print(stateDisplay);
        System.out.print("  ");
        System.out.print(registryDisplay);
        System.out.print("  ");
        System.out.print(eventDisplay);
        System.out.print("  ");
        System.out.print(transitionDisplay);
        
        // Calculate remaining space and pad
        int remaining = width - 4 - stripAnsi(stateDisplay) - stripAnsi(registryDisplay) 
                       - stripAnsi(eventDisplay) - stripAnsi(transitionDisplay) - 6;
        if (remaining > 0) {
            System.out.print(" ".repeat(remaining));
        }
        System.out.println(BOLD + WHITE + " ║" + RESET);
        System.out.println(BOLD + WHITE + "╚" + "═".repeat(width - 2) + "╝" + RESET);
    }
    
    private void printTwoBoxes(String title1, String[] content1, String title2, String[] content2, 
                                int width1, int width2) {
        // Headers
        System.out.print(TL + H.repeat(width1 - 2) + TR);
        System.out.print("  ");
        System.out.println(TL + H.repeat(width2 - 2) + TR);
        
        System.out.print(V + YELLOW + BOLD + center(title1, width1 - 2) + RESET + V);
        System.out.print("  ");
        System.out.println(V + YELLOW + BOLD + center(title2, width2 - 2) + RESET + V);
        
        System.out.print(T_RIGHT + H.repeat(width1 - 2) + T_LEFT);
        System.out.print("  ");
        System.out.println(T_RIGHT + H.repeat(width2 - 2) + T_LEFT);
        
        // Content
        int maxLines = Math.max(content1.length, content2.length);
        for (int i = 0; i < maxLines; i++) {
            String line1 = i < content1.length ? content1[i] : "";
            String line2 = i < content2.length ? content2[i] : "";
            
            System.out.print(V + padRight(line1, width1 - 2) + V);
            System.out.print("  ");
            System.out.println(V + padRight(line2, width2 - 2) + V);
        }
        
        // Footer
        System.out.print(BL + H.repeat(width1 - 2) + BR);
        System.out.print("  ");
        System.out.println(BL + H.repeat(width2 - 2) + BR);
    }
    
    private String[] getMachineInfo() {
        return new String[] {
            " " + WHITE + "Machine ID:" + RESET + " " + MACHINE_ID,
            " " + WHITE + "Events Fired:" + RESET + " " + eventCounter.get(),
            " " + WHITE + "State Changes:" + RESET + " " + stateHistory.size(),
            " " + WHITE + "Started:" + RESET + " " + context.getStartTime().format(TIME_FORMAT),
            " " + WHITE + "Last Change:" + RESET + " " + 
                (context.getLastStateChange() != null ? context.getLastStateChange().format(TIME_FORMAT) : "N/A"),
            " " + WHITE + "Recording:" + RESET + " " + 
                (context.isRecordingEnabled() ? GREEN + "ENABLED" + RESET : RED + "DISABLED" + RESET)
        };
    }
    
    private String[] getCallContext() {
        return new String[] {
            " " + WHITE + "Call ID:" + RESET + " " + context.getCallId(),
            " " + WHITE + "From:" + RESET + " " + context.getFromNumber(),
            " " + WHITE + "To:" + RESET + " " + context.getToNumber(),
            " " + WHITE + "Direction:" + RESET + " " + context.getCallDirection(),
            " " + WHITE + "Status:" + RESET + " " + context.getCallStatus(),
            " " + WHITE + "Ring Count:" + RESET + " " + context.getRingCount(),
            " " + WHITE + "Complete:" + RESET + " " + (context.isComplete() ? "Yes" : "No")
        };
    }
    
    private String[] getRecentEvents(int maxLines) {
        List<String> result = new ArrayList<>();
        int start = Math.max(0, eventHistory.size() - maxLines);
        for (int i = start; i < eventHistory.size(); i++) {
            result.add(" " + eventHistory.get(i));
        }
        if (result.isEmpty()) {
            result.add(" " + CYAN + "(No events yet)" + RESET);
        }
        return result.toArray(new String[0]);
    }
    
    private String[] getRecentStates(int maxLines) {
        List<String> result = new ArrayList<>();
        int start = Math.max(0, stateHistory.size() - maxLines);
        for (int i = start; i < stateHistory.size(); i++) {
            result.add(" " + stateHistory.get(i));
        }
        if (result.isEmpty()) {
            result.add(" " + CYAN + "(No transitions yet)" + RESET);
        }
        return result.toArray(new String[0]);
    }
    
    private String getStateBackground(String state) {
        switch (state) {
            case "IDLE": return BG_GREEN;
            case "RINGING": return BG_YELLOW;
            case "CONNECTED": return BG_BLUE;
            default: return BG_RED;
        }
    }
    
    private String getStateColor(String state) {
        switch (state) {
            case "IDLE": return GREEN;
            case "RINGING": return YELLOW;
            case "CONNECTED": return BLUE;
            default: return WHITE;
        }
    }
    
    private String center(String text, int width) {
        int textLen = stripAnsi(text);
        int padding = (width - textLen) / 2;
        int remainder = width - textLen - padding * 2;
        return " ".repeat(padding) + text + " ".repeat(padding + remainder);
    }
    
    private String padRight(String text, int width) {
        int textLength = stripAnsi(text);
        if (textLength >= width) {
            return text.substring(0, Math.min(text.length(), width));
        }
        return text + " ".repeat(width - textLength);
    }
    
    private int stripAnsi(String text) {
        return text.replaceAll("\u001B\\[[;\\d]*m", "").length();
    }
    
    private void registerEventTypes() {
        EventTypeRegistry.register(IncomingCall.class, "INCOMING_CALL");
        EventTypeRegistry.register(Answer.class, "ANSWER");
        EventTypeRegistry.register(Hangup.class, "HANGUP");
        EventTypeRegistry.register(SessionProgress.class, "SESSION_PROGRESS");
    }
    
    private void initializeStateMachine() {
        // Create context
        context = new CallContext(MACHINE_ID, "+1-555-0001", "+1-555-0002");
        
        // Build state machine
        machine = FluentStateMachineBuilder.<CallContext, Void>create(MACHINE_ID)
            .initialState(CallState.IDLE)
            
            .state(CallState.IDLE)
                .on(IncomingCall.class).to(CallState.RINGING)
                .done()
                
            .state(CallState.RINGING)
                .timeout(30, com.telcobright.statemachine.timeout.TimeUnit.SECONDS, CallState.IDLE.name())
                .on(Answer.class).to(CallState.CONNECTED)
                .on(Hangup.class).to(CallState.IDLE)
                .stay(SessionProgress.class, (m, e) -> {
                    context.setRingCount(context.getRingCount() + 1);
                })
                .done()
                
            .state(CallState.CONNECTED)
                .on(Hangup.class).to(CallState.IDLE)
                .done()
                
            .build();
        
        // Set context and register
        machine.setPersistingEntity(context);
        registry.register(MACHINE_ID, machine);
        machine.start();
        
        // Record initial state
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        stateHistory.add(timestamp + " Initial -> " + machine.getCurrentState());
    }
    
    private void sendIncomingCall(BufferedReader reader) throws Exception {
        System.out.print("Enter caller number [+1-555-9999]: ");
        String number = reader.readLine();
        if (number.isEmpty()) number = "+1-555-9999";
        
        String previousState = machine.getCurrentState();
        IncomingCall event = new IncomingCall(number);
        machine.fire(event);
        
        recordEvent("INCOMING_CALL", previousState);
        lastEventName = "INCOMING_CALL";
    }
    
    private void sendAnswer() {
        String previousState = machine.getCurrentState();
        machine.fire(new Answer());
        context.setConnectTime(LocalDateTime.now());
        
        recordEvent("ANSWER", previousState);
        lastEventName = "ANSWER";
    }
    
    private void sendHangup() {
        String previousState = machine.getCurrentState();
        machine.fire(new Hangup());
        
        recordEvent("HANGUP", previousState);
        lastEventName = "HANGUP";
    }
    
    private void sendSessionProgress(BufferedReader reader) throws Exception {
        System.out.print("Enter SDP [v=0]: ");
        String sdp = reader.readLine();
        if (sdp.isEmpty()) sdp = "v=0";
        
        String previousState = machine.getCurrentState();
        machine.fire(new SessionProgress(sdp, context.getRingCount() + 1));
        
        recordEvent("SESSION_PROGRESS", previousState);
        lastEventName = "SESSION_PROGRESS";
    }
    
    private void recordEvent(String eventName, String previousState) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        eventHistory.add(timestamp + " " + eventName);
        eventCounter.incrementAndGet();
        
        String currentState = machine.getCurrentState();
        if (!previousState.equals(currentState)) {
            String transition = previousState + " -> " + currentState;
            stateHistory.add(timestamp + " " + transition);
            lastTransition = transition;
        }
    }
    
    private void startAutoRefresh() {
        scheduler.scheduleAtFixedRate(() -> {
            needsRefresh = true;
        }, 2, 2, TimeUnit.SECONDS);
    }
    
    private void cleanup() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        registry.removeMachine(MACHINE_ID);
    }
}