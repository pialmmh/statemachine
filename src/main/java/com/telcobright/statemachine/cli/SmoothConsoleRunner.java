package com.telcobright.statemachine.cli;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.events.EventTypeRegistry;
import com.telcobright.statemachineexamples.callmachine.CallContext;
import com.telcobright.statemachineexamples.callmachine.CallState;
import com.telcobright.statemachineexamples.callmachine.events.*;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Smooth Console State Runner with flicker-free updates
 * Uses JLine3 for professional terminal handling
 */
public class SmoothConsoleRunner {
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final String MACHINE_ID = "smooth-" + System.currentTimeMillis();
    
    private final StateMachineRegistry registry = new StateMachineRegistry();
    private GenericStateMachine<CallContext, Void> machine;
    private CallContext context;
    
    // Terminal handling
    private Terminal terminal;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final AtomicBoolean running = new AtomicBoolean(true);
    
    // History tracking with fixed sizes for stable display
    private final LinkedList<String> eventHistory = new LinkedList<>();
    private final LinkedList<String> stateHistory = new LinkedList<>();
    private final AtomicInteger eventCounter = new AtomicInteger(0);
    private volatile String lastEventName = "None";
    private volatile String lastTransition = "None";
    
    // Display dimensions
    private static final int MAX_HISTORY_LINES = 8;
    private static final int BOX_WIDTH = 48;
    private static final int TOTAL_WIDTH = 100;
    
    // ANSI codes for cursor control
    private static final String ESC = "\033[";
    private static final String SAVE_CURSOR = ESC + "s";
    private static final String RESTORE_CURSOR = ESC + "u";
    private static final String CLEAR_LINE = ESC + "2K";
    private static final String MOVE_TO = ESC + "%d;%dH"; // row;col
    
    public static void main(String[] args) {
        try {
            new SmoothConsoleRunner().run();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void run() throws Exception {
        try {
            // Initialize terminal
            terminal = TerminalBuilder.builder()
                .system(true)
                .jna(true)
                .build();
            
            // Initialize state machine
            registerEventTypes();
            initializeStateMachine();
            
            // Initial draw
            drawStaticLayout();
            
            // Start refresh thread
            startRefreshThread();
            
            // Handle input
            handleInput();
            
        } finally {
            cleanup();
        }
    }
    
    private void drawStaticLayout() {
        // Clear screen and draw the static parts of the UI
        terminal.puts(org.jline.utils.InfoCmp.Capability.clear_screen);
        terminal.flush();
        
        // Draw static borders and labels
        drawBorders();
        
        // Initial dynamic content
        updateDynamicContent();
    }
    
    private void drawBorders() {
        // Title bar - row 1
        moveCursor(1, 1);
        print(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold(), 
              "═".repeat(TOTAL_WIDTH));
        moveCursor(1, 32);
        print(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold(), 
              " INTERACTIVE STATE MACHINE MONITOR ");
        
        // Machine Info box - starts at row 3
        drawBox(3, 1, BOX_WIDTH, 9, "MACHINE INFO", AttributedStyle.YELLOW);
        
        // Call Context box - starts at row 3
        drawBox(3, 52, BOX_WIDTH, 9, "CALL CONTEXT", AttributedStyle.YELLOW);
        
        // Event History box - starts at row 13
        drawBox(13, 1, BOX_WIDTH, 11, "EVENT HISTORY", AttributedStyle.CYAN);
        
        // State Transitions box - starts at row 13  
        drawBox(13, 52, BOX_WIDTH, 11, "STATE TRANSITIONS", AttributedStyle.CYAN);
        
        // Send Events bar - row 25
        moveCursor(25, 1);
        print(AttributedStyle.DEFAULT, "┌" + "─".repeat(TOTAL_WIDTH - 2) + "┐");
        moveCursor(26, 1);
        print(AttributedStyle.DEFAULT, "│ ");
        print(AttributedStyle.BOLD, "SEND EVENTS: ");
        print(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN), "[1]");
        print(AttributedStyle.DEFAULT, " INCOMING_CALL  ");
        print(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN), "[2]");
        print(AttributedStyle.DEFAULT, " ANSWER  ");
        print(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN), "[3]");
        print(AttributedStyle.DEFAULT, " HANGUP  ");
        print(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN), "[4]");
        print(AttributedStyle.DEFAULT, " SESSION_PROGRESS  ");
        print(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED), "[Q]");
        print(AttributedStyle.DEFAULT, " Quit");
        moveCursor(26, TOTAL_WIDTH);
        print(AttributedStyle.DEFAULT, "│");
        moveCursor(27, 1);
        print(AttributedStyle.DEFAULT, "└" + "─".repeat(TOTAL_WIDTH - 2) + "┘");
        
        terminal.flush();
    }
    
    private void drawBox(int row, int col, int width, int height, String title, int titleColor) {
        // Top border with title
        moveCursor(row, col);
        print(AttributedStyle.DEFAULT, "┌" + "─".repeat(width - 2) + "┐");
        
        moveCursor(row + 1, col);
        print(AttributedStyle.DEFAULT, "│");
        int titleStart = col + (width - title.length()) / 2;
        moveCursor(row + 1, titleStart);
        print(AttributedStyle.DEFAULT.foreground(titleColor).bold(), title);
        moveCursor(row + 1, col + width - 1);
        print(AttributedStyle.DEFAULT, "│");
        
        moveCursor(row + 2, col);
        print(AttributedStyle.DEFAULT, "├" + "─".repeat(width - 2) + "┤");
        
        // Side borders
        for (int i = 3; i < height - 1; i++) {
            moveCursor(row + i, col);
            print(AttributedStyle.DEFAULT, "│");
            moveCursor(row + i, col + width - 1);
            print(AttributedStyle.DEFAULT, "│");
        }
        
        // Bottom border
        moveCursor(row + height - 1, col);
        print(AttributedStyle.DEFAULT, "└" + "─".repeat(width - 2) + "┘");
    }
    
    private void updateDynamicContent() {
        // Update timestamp in title
        moveCursor(1, 70);
        print(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW), 
              "[" + LocalDateTime.now().format(TIME_FORMAT) + "]");
        
        // Update Machine Info content
        updateMachineInfo();
        
        // Update Call Context content
        updateCallContext();
        
        // Update Event History
        updateEventHistory();
        
        // Update State History
        updateStateHistory();
        
        // Update Status Bar
        updateStatusBar();
        
        // Position cursor at prompt
        moveCursor(30, 1);
        print(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN), 
              "Enter event (1-4) or Q: ");
        
        terminal.flush();
    }
    
    private void updateMachineInfo() {
        int row = 6;
        int col = 3;
        
        clearAndPrint(row++, col, "Machine ID: ", MACHINE_ID, AttributedStyle.WHITE);
        clearAndPrint(row++, col, "Events: ", String.valueOf(eventCounter.get()), AttributedStyle.WHITE);
        clearAndPrint(row++, col, "Changes: ", String.valueOf(stateHistory.size()), AttributedStyle.WHITE);
        clearAndPrint(row++, col, "Started: ", context.getStartTime().format(TIME_FORMAT), AttributedStyle.WHITE);
        clearAndPrint(row++, col, "Last Change: ", 
                     context.getLastStateChange() != null ? 
                     context.getLastStateChange().format(TIME_FORMAT) : "N/A", 
                     AttributedStyle.WHITE);
        clearAndPrint(row, col, "Recording: ", 
                     context.isRecordingEnabled() ? "ON" : "OFF",
                     context.isRecordingEnabled() ? AttributedStyle.GREEN : AttributedStyle.RED);
    }
    
    private void updateCallContext() {
        int row = 6;
        int col = 54;
        
        clearAndPrint(row++, col, "Call ID: ", context.getCallId(), AttributedStyle.WHITE);
        clearAndPrint(row++, col, "From: ", context.getFromNumber(), AttributedStyle.WHITE);
        clearAndPrint(row++, col, "To: ", context.getToNumber(), AttributedStyle.WHITE);
        clearAndPrint(row++, col, "Direction: ", context.getCallDirection(), AttributedStyle.WHITE);
        clearAndPrint(row++, col, "Status: ", context.getCallStatus(), AttributedStyle.WHITE);
        clearAndPrint(row++, col, "Ring Count: ", String.valueOf(context.getRingCount()), AttributedStyle.WHITE);
        clearAndPrint(row, col, "Complete: ", context.isComplete() ? "Yes" : "No", AttributedStyle.WHITE);
    }
    
    private void updateEventHistory() {
        int row = 16;
        int col = 3;
        
        // Clear area first
        for (int i = 0; i < MAX_HISTORY_LINES; i++) {
            moveCursor(row + i, col);
            terminal.writer().print(CLEAR_LINE);
            moveCursor(row + i, col);
            terminal.writer().print(" ".repeat(BOX_WIDTH - 4));
        }
        
        // Display events
        int start = Math.max(0, eventHistory.size() - MAX_HISTORY_LINES);
        int line = 0;
        for (int i = start; i < eventHistory.size() && line < MAX_HISTORY_LINES; i++) {
            moveCursor(row + line++, col);
            print(AttributedStyle.DEFAULT, eventHistory.get(i));
        }
        
        if (eventHistory.isEmpty()) {
            moveCursor(row, col);
            print(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN), "(No events yet)");
        }
    }
    
    private void updateStateHistory() {
        int row = 16;
        int col = 54;
        
        // Clear area first
        for (int i = 0; i < MAX_HISTORY_LINES; i++) {
            moveCursor(row + i, col);
            terminal.writer().print(CLEAR_LINE);
            moveCursor(row + i, col);
            terminal.writer().print(" ".repeat(BOX_WIDTH - 4));
        }
        
        // Display transitions
        int start = Math.max(0, stateHistory.size() - MAX_HISTORY_LINES);
        int line = 0;
        for (int i = start; i < stateHistory.size() && line < MAX_HISTORY_LINES; i++) {
            moveCursor(row + line++, col);
            print(AttributedStyle.DEFAULT, stateHistory.get(i));
        }
        
        if (stateHistory.isEmpty()) {
            moveCursor(row, col);
            print(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN), "(No transitions yet)");
        }
    }
    
    private void updateStatusBar() {
        // Status bar at row 29
        moveCursor(29, 1);
        terminal.writer().print(CLEAR_LINE);
        
        String state = machine.getCurrentState();
        boolean isOnline = registry.isRegistered(MACHINE_ID);
        
        // Build status line
        AttributedStringBuilder sb = new AttributedStringBuilder();
        
        // State with background
        sb.style(getStateStyle(state));
        sb.append(" STATE: " + state + " ");
        
        sb.style(AttributedStyle.DEFAULT);
        sb.append("  ");
        
        // Registry status with background
        sb.style(isOnline ? 
                AttributedStyle.DEFAULT.background(AttributedStyle.GREEN).foreground(AttributedStyle.BLACK).bold() :
                AttributedStyle.DEFAULT.background(AttributedStyle.RED).foreground(AttributedStyle.WHITE).bold());
        sb.append(" REGISTRY: " + (isOnline ? "ONLINE" : "OFFLINE") + " ");
        
        sb.style(AttributedStyle.DEFAULT);
        sb.append("  ");
        
        // Last event
        sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
        sb.append("Last Event: " + lastEventName);
        
        sb.append("  ");
        
        // Last transition
        sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN));
        sb.append("Last Transition: " + lastTransition);
        
        moveCursor(29, 1);
        terminal.writer().print(sb.toAnsi());
    }
    
    private AttributedStyle getStateStyle(String state) {
        switch (state) {
            case "IDLE":
                return AttributedStyle.DEFAULT.background(AttributedStyle.GREEN).foreground(AttributedStyle.BLACK).bold();
            case "RINGING":
                return AttributedStyle.DEFAULT.background(AttributedStyle.YELLOW).foreground(AttributedStyle.BLACK).bold();
            case "CONNECTED":
                return AttributedStyle.DEFAULT.background(AttributedStyle.BLUE).foreground(AttributedStyle.WHITE).bold();
            default:
                return AttributedStyle.DEFAULT.background(AttributedStyle.RED).foreground(AttributedStyle.WHITE).bold();
        }
    }
    
    private void clearAndPrint(int row, int col, String label, String value, int valueColor) {
        moveCursor(row, col);
        terminal.writer().print(CLEAR_LINE);
        moveCursor(row, col);
        print(AttributedStyle.DEFAULT.bold(), label);
        print(AttributedStyle.DEFAULT.foreground(valueColor), value);
    }
    
    private void moveCursor(int row, int col) {
        terminal.writer().printf(MOVE_TO, row, col);
    }
    
    private void print(AttributedStyle style, String text) {
        terminal.writer().print(new AttributedString(text, style).toAnsi());
    }
    
    private void startRefreshThread() {
        scheduler.scheduleAtFixedRate(() -> {
            if (running.get()) {
                try {
                    // Only update dynamic content, not the whole screen
                    updateDynamicContent();
                } catch (Exception e) {
                    // Ignore update errors
                }
            }
        }, 2, 2, TimeUnit.SECONDS);
    }
    
    private void handleInput() throws IOException {
        while (running.get()) {
            int c = terminal.reader().read(100); // 100ms timeout
            
            if (c == -1 || c == -2) continue; // Timeout or no input
            
            char ch = (char) c;
            switch (ch) {
                case '1':
                    sendIncomingCall();
                    break;
                case '2':
                    sendAnswer();
                    break;
                case '3':
                    sendHangup();
                    break;
                case '4':
                    sendSessionProgress();
                    break;
                case 'q':
                case 'Q':
                    running.set(false);
                    break;
            }
            
            // Clear input line
            moveCursor(30, 26);
            terminal.writer().print("    ");
            moveCursor(30, 26);
            terminal.flush();
        }
    }
    
    private void registerEventTypes() {
        EventTypeRegistry.register(IncomingCall.class, "INCOMING_CALL");
        EventTypeRegistry.register(Answer.class, "ANSWER");
        EventTypeRegistry.register(Hangup.class, "HANGUP");
        EventTypeRegistry.register(SessionProgress.class, "SESSION_PROGRESS");
    }
    
    private void initializeStateMachine() {
        context = new CallContext(MACHINE_ID, "+1-555-0001", "+1-555-0002");
        
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
        
        machine.setPersistingEntity(context);
        registry.register(MACHINE_ID, machine);
        machine.start();
        
        // Record initial state
        addStateHistory("Initial -> " + machine.getCurrentState());
    }
    
    private void sendIncomingCall() {
        String previousState = machine.getCurrentState();
        machine.fire(new IncomingCall("+1-555-9999"));
        recordEvent("INCOMING_CALL", previousState);
    }
    
    private void sendAnswer() {
        String previousState = machine.getCurrentState();
        machine.fire(new Answer());
        context.setConnectTime(LocalDateTime.now());
        recordEvent("ANSWER", previousState);
    }
    
    private void sendHangup() {
        String previousState = machine.getCurrentState();
        machine.fire(new Hangup());
        recordEvent("HANGUP", previousState);
    }
    
    private void sendSessionProgress() {
        String previousState = machine.getCurrentState();
        machine.fire(new SessionProgress("v=0", context.getRingCount() + 1));
        recordEvent("SESSION_PROGRESS", previousState);
    }
    
    private void recordEvent(String eventName, String previousState) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        addEventHistory(timestamp + " " + eventName);
        eventCounter.incrementAndGet();
        lastEventName = eventName;
        
        String currentState = machine.getCurrentState();
        if (!previousState.equals(currentState)) {
            String transition = previousState + " -> " + currentState;
            addStateHistory(timestamp + " " + transition);
            lastTransition = transition;
        }
    }
    
    private void addEventHistory(String event) {
        eventHistory.add(event);
        while (eventHistory.size() > MAX_HISTORY_LINES) {
            eventHistory.removeFirst();
        }
    }
    
    private void addStateHistory(String transition) {
        stateHistory.add(transition);
        while (stateHistory.size() > MAX_HISTORY_LINES) {
            stateHistory.removeFirst();
        }
    }
    
    private void cleanup() {
        running.set(false);
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        
        if (terminal != null) {
            try {
                terminal.puts(org.jline.utils.InfoCmp.Capability.clear_screen);
                terminal.flush();
                terminal.close();
            } catch (Exception e) {
                // Ignore
            }
        }
        
        registry.removeMachine(MACHINE_ID);
    }
}