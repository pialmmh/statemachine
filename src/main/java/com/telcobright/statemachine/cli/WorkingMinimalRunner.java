package com.telcobright.statemachine.cli;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.events.EventTypeRegistry;
import com.telcobright.statemachineexamples.callmachine.CallContext;
import com.telcobright.statemachineexamples.callmachine.CallState;
import com.telcobright.statemachineexamples.callmachine.events.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Working Minimal Update Console Runner
 * True in-place updates with proper initialization order
 */
public class WorkingMinimalRunner {
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final String MACHINE_ID = "minimal-" + System.currentTimeMillis();
    
    private final StateMachineRegistry registry = new StateMachineRegistry();
    private GenericStateMachine<CallContext, Void> machine;
    private CallContext context;
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private volatile boolean running = true;
    
    // History tracking
    private final LinkedList<String> eventHistory = new LinkedList<>();
    private final LinkedList<String> stateHistory = new LinkedList<>();
    private final AtomicInteger eventCounter = new AtomicInteger(0);
    private String lastEventName = "None";
    private String lastTransition = "None";
    
    // Previous values for minimal updates
    private String prevTimestamp = "";
    private int prevEventCount = -1;
    private int prevStateCount = -1;
    private String prevState = "";
    private boolean prevOnline = false;
    
    // ANSI codes
    private static final String ESC = "\033[";
    private static final String CURSOR_TO = ESC + "%d;%dH";
    private static final String HIDE_CURSOR = ESC + "?25l";
    private static final String SHOW_CURSOR = ESC + "?25h";
    private static final String CLEAR_SCREEN = ESC + "2J" + ESC + "H";
    
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
    
    public static void main(String[] args) {
        try {
            new WorkingMinimalRunner().run();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void run() throws Exception {
        try {
            // Initialize state machine FIRST
            registerEventTypes();
            initializeStateMachine();
            
            // Now draw static UI
            System.out.print(HIDE_CURSOR);
            drawStaticLayout();
            
            // Initial dynamic content
            updateAllValues();
            
            // Start refresh timer
            startRefreshTimer();
            
            // Handle input
            handleInput();
            
        } finally {
            cleanup();
        }
    }
    
    private void drawStaticLayout() {
        System.out.print(CLEAR_SCREEN);
        
        // Title bar
        System.out.println(CYAN + "═".repeat(80) + RESET);
        moveTo(1, 25);
        System.out.print(CYAN + " STATE MACHINE MONITOR " + RESET);
        
        // Machine info section
        moveTo(3, 1);
        System.out.println("┌" + "─".repeat(38) + "┐");
        moveTo(4, 1);
        System.out.print("│" + YELLOW + "          MACHINE INFO          " + RESET + "│");
        moveTo(5, 1);
        System.out.println("├" + "─".repeat(38) + "┤");
        moveTo(6, 1); System.out.print("│ Machine ID: " + GREEN + MACHINE_ID.substring(8) + RESET);
        moveTo(6, 40); System.out.println("│");
        moveTo(7, 1); System.out.print("│ Events:                        │");
        moveTo(8, 1); System.out.print("│ Changes:                       │");
        moveTo(9, 1); System.out.print("│ Started: " + context.getStartTime().format(TIME_FORMAT));
        moveTo(9, 40); System.out.println("│");
        moveTo(10, 1); System.out.print("│ Recording:                     │");
        moveTo(11, 1);
        System.out.println("└" + "─".repeat(38) + "┘");
        
        // Call context section
        moveTo(3, 42);
        System.out.println("┌" + "─".repeat(37) + "┐");
        moveTo(4, 42);
        System.out.print("│" + YELLOW + "         CALL CONTEXT          " + RESET + "│");
        moveTo(5, 42);
        System.out.println("├" + "─".repeat(37) + "┤");
        moveTo(6, 42); System.out.print("│ From: " + context.getFromNumber());
        moveTo(6, 80); System.out.println("│");
        moveTo(7, 42); System.out.print("│ To: " + context.getToNumber());
        moveTo(7, 80); System.out.println("│");
        moveTo(8, 42); System.out.print("│ Direction: " + context.getCallDirection());
        moveTo(8, 80); System.out.println("│");
        moveTo(9, 42); System.out.print("│ Status:                        │");
        moveTo(10, 42); System.out.print("│ Ring Count:                    │");
        moveTo(11, 42);
        System.out.println("└" + "─".repeat(37) + "┘");
        
        // Event history
        moveTo(13, 1);
        System.out.println("┌" + "─".repeat(38) + "┐");
        moveTo(14, 1);
        System.out.print("│" + CYAN + "         EVENT HISTORY         " + RESET + "│");
        moveTo(15, 1);
        System.out.println("├" + "─".repeat(38) + "┤");
        for (int i = 0; i < 6; i++) {
            moveTo(16 + i, 1);
            System.out.print("│                                      │");
        }
        moveTo(22, 1);
        System.out.println("└" + "─".repeat(38) + "┘");
        
        // State history
        moveTo(13, 42);
        System.out.println("┌" + "─".repeat(37) + "┐");
        moveTo(14, 42);
        System.out.print("│" + CYAN + "       STATE TRANSITIONS       " + RESET + "│");
        moveTo(15, 42);
        System.out.println("├" + "─".repeat(37) + "┤");
        for (int i = 0; i < 6; i++) {
            moveTo(16 + i, 42);
            System.out.print("│                                     │");
        }
        moveTo(22, 42);
        System.out.println("└" + "─".repeat(37) + "┘");
        
        // Menu
        moveTo(24, 1);
        System.out.println("EVENTS: " + GREEN + "[1]" + RESET + " CALL " + 
                          GREEN + "[2]" + RESET + " ANSWER " + 
                          GREEN + "[3]" + RESET + " HANGUP " + 
                          GREEN + "[4]" + RESET + " PROGRESS " + 
                          RED + "[Q]" + RESET + " QUIT");
        
        // Status area
        moveTo(26, 1);
        System.out.print("STATE: ");
        moveTo(26, 20);
        System.out.print("REGISTRY: ");
        moveTo(26, 40);
        System.out.print("LAST: ");
        
        System.out.flush();
    }
    
    private void updateAllValues() {
        updateTimestamp();
        updateEventCount();
        updateStateCount();
        updateCallStatus();
        updateRingCount();
        updateRecordingStatus();
        updateEventHistory();
        updateStateHistory();
        updateStatusBar();
        System.out.flush();
    }
    
    private void updateTimestamp() {
        String current = LocalDateTime.now().format(TIME_FORMAT);
        if (!current.equals(prevTimestamp)) {
            moveTo(1, 50);
            System.out.print(YELLOW + "[" + current + "]" + RESET);
            prevTimestamp = current;
        }
    }
    
    private void updateEventCount() {
        int current = eventCounter.get();
        if (current != prevEventCount) {
            moveTo(7, 10);
            System.out.print(WHITE + String.format("%-8d", current) + RESET);
            prevEventCount = current;
        }
    }
    
    private void updateStateCount() {
        int current = stateHistory.size();
        if (current != prevStateCount) {
            moveTo(8, 11);
            System.out.print(WHITE + String.format("%-8d", current) + RESET);
            prevStateCount = current;
        }
    }
    
    private void updateCallStatus() {
        moveTo(9, 51);
        System.out.print(WHITE + String.format("%-20s", context.getCallStatus()) + RESET);
    }
    
    private void updateRingCount() {
        moveTo(10, 55);
        System.out.print(WHITE + String.format("%-5d", context.getRingCount()) + RESET);
    }
    
    private void updateRecordingStatus() {
        moveTo(10, 13);
        boolean recording = context.isRecordingEnabled();
        System.out.print((recording ? GREEN + "ON " : RED + "OFF") + RESET);
    }
    
    private void updateEventHistory() {
        for (int i = 0; i < 6; i++) {
            moveTo(16 + i, 3);
            if (i < eventHistory.size()) {
                int index = Math.max(0, eventHistory.size() - 6) + i;
                String event = eventHistory.get(index);
                System.out.print(WHITE + String.format("%-34s", event) + RESET);
            } else {
                System.out.print(" ".repeat(34));
            }
        }
    }
    
    private void updateStateHistory() {
        for (int i = 0; i < 6; i++) {
            moveTo(16 + i, 44);
            if (i < stateHistory.size()) {
                int index = Math.max(0, stateHistory.size() - 6) + i;
                String state = stateHistory.get(index);
                System.out.print(WHITE + String.format("%-33s", state) + RESET);
            } else {
                System.out.print(" ".repeat(33));
            }
        }
    }
    
    private void updateStatusBar() {
        String currentState = machine.getCurrentState();
        boolean currentOnline = registry.isRegistered(MACHINE_ID);
        
        // Update state if changed
        if (!currentState.equals(prevState)) {
            moveTo(26, 8);
            String bg = getStateBackground(currentState);
            System.out.print(bg + currentState + RESET + "      ");
            prevState = currentState;
        }
        
        // Update registry if changed
        if (currentOnline != prevOnline) {
            moveTo(26, 30);
            String bg = currentOnline ? BG_GREEN : BG_RED;
            System.out.print(bg + (currentOnline ? "ONLINE" : "OFFLINE") + RESET + "   ");
            prevOnline = currentOnline;
        }
        
        // Update last event
        moveTo(26, 46);
        System.out.print(YELLOW + lastEventName + RESET + " -> " + CYAN + lastTransition + RESET);
    }
    
    private String getStateBackground(String state) {
        switch (state) {
            case "IDLE": return BG_GREEN;
            case "RINGING": return BG_YELLOW;
            case "CONNECTED": return BG_BLUE;
            default: return BG_RED;
        }
    }
    
    private void startRefreshTimer() {
        scheduler.scheduleAtFixedRate(() -> {
            if (running) {
                updateTimestamp();
                updateCallStatus();
                updateRingCount();
                updateRecordingStatus();
                System.out.flush();
            }
        }, 1, 1, TimeUnit.SECONDS);
    }
    
    private void handleInput() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            moveTo(28, 1);
            System.out.print(CYAN + "Enter command: " + RESET);
            System.out.flush();
            
            while (running) {
                if (reader.ready()) {
                    String input = reader.readLine().trim();
                    
                    // Clear input line
                    moveTo(28, 16);
                    System.out.print(" ".repeat(20));
                    
                    switch (input) {
                        case "1":
                            sendIncomingCall();
                            showMessage("✓ INCOMING_CALL sent");
                            break;
                        case "2":
                            sendAnswer();
                            showMessage("✓ ANSWER sent");
                            break;
                        case "3":
                            sendHangup();
                            showMessage("✓ HANGUP sent");
                            break;
                        case "4":
                            sendSessionProgress();
                            showMessage("✓ SESSION_PROGRESS sent");
                            break;
                        case "q":
                        case "Q":
                            running = false;
                            break;
                    }
                    
                    if (running) {
                        updateAllValues();
                        moveTo(28, 1);
                        System.out.print(CYAN + "Enter command: " + RESET);
                        System.out.flush();
                    }
                } else {
                    Thread.sleep(100);
                }
            }
        } catch (Exception e) {
            running = false;
        }
    }
    
    private void showMessage(String message) {
        moveTo(27, 1);
        System.out.print(GREEN + message + RESET + "                    ");
        System.out.flush();
        
        // Clear after 2 seconds
        scheduler.schedule(() -> {
            moveTo(27, 1);
            System.out.print(" ".repeat(30));
            System.out.flush();
        }, 2, TimeUnit.SECONDS);
    }
    
    private void moveTo(int row, int col) {
        System.out.printf(CURSOR_TO, row, col);
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
        while (eventHistory.size() > 6) {
            eventHistory.removeFirst();
        }
    }
    
    private void addStateHistory(String transition) {
        stateHistory.add(transition);
        while (stateHistory.size() > 6) {
            stateHistory.removeFirst();
        }
    }
    
    private void cleanup() {
        running = false;
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        
        System.out.print(SHOW_CURSOR);
        moveTo(30, 1);
        System.out.println(GREEN + "\nGoodbye!" + RESET);
        
        registry.removeMachine(MACHINE_ID);
    }
}