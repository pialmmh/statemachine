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
 * Fixed Layout Console Runner with proper in-place updates
 */
public class FixedLayoutRunner {
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final String MACHINE_ID = "fixed-" + System.currentTimeMillis();
    
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
    
    // Fixed positions for dynamic content
    private static final int TIME_ROW = 2, TIME_COL = 60;
    private static final int EVENT_COUNT_ROW = 8, EVENT_COUNT_COL = 12;
    private static final int STATE_COUNT_ROW = 9, STATE_COUNT_COL = 12;
    private static final int RECORDING_ROW = 11, RECORDING_COL = 15;
    private static final int CALL_STATUS_ROW = 10, CALL_STATUS_COL = 53;
    private static final int RING_COUNT_ROW = 11, RING_COUNT_COL = 57;
    private static final int STATE_STATUS_ROW = 26, STATE_STATUS_COL = 8;
    private static final int REGISTRY_STATUS_ROW = 26, REGISTRY_STATUS_COL = 31;
    private static final int LAST_EVENT_ROW = 26, LAST_EVENT_COL = 46;
    
    public static void main(String[] args) {
        try {
            new FixedLayoutRunner().run();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void run() throws Exception {
        try {
            // Hide cursor and clear screen
            System.out.print(HIDE_CURSOR + CLEAR + HOME);
            
            // Initialize state machine first
            registerEventTypes();
            initializeStateMachine();
            
            // Draw complete static UI
            drawCompleteUI();
            
            // Start refresh timer
            startRefreshTimer();
            
            // Handle input
            handleInput();
            
        } finally {
            cleanup();
        }
    }
    
    private void drawCompleteUI() {
        // Draw everything once
        String[] ui = new String[] {
            CYAN + "═".repeat(80) + RESET,
            CYAN + "                       STATE MACHINE MONITOR                    Time: " + RESET,
            "",
            "┌──────────────────────────────────────┬─────────────────────────────────────┐",
            "│" + YELLOW + "          MACHINE INFO                " + RESET + "│" + YELLOW + "         CALL CONTEXT                " + RESET + "│",
            "├──────────────────────────────────────┼─────────────────────────────────────┤",
            "│ Machine: " + GREEN + MACHINE_ID.substring(6) + RESET + "     │ From: " + context.getFromNumber() + "                     │",
            "│ Events:               │ To: " + context.getToNumber() + "                       │",
            "│ Changes:              │ Direction: " + context.getCallDirection() + "                      │",
            "│ Started: " + context.getStartTime().format(TIME_FORMAT) + "             │ Status:                             │",
            "│ Recording:            │ Ring Count:                         │",
            "└──────────────────────────────────────┴─────────────────────────────────────┘",
            "",
            "┌──────────────────────────────────────┬─────────────────────────────────────┐",
            "│" + CYAN + "         EVENT HISTORY                " + RESET + "│" + CYAN + "       STATE TRANSITIONS             " + RESET + "│",
            "├──────────────────────────────────────┼─────────────────────────────────────┤",
            "│                                      │                                     │",
            "│                                      │                                     │",
            "│                                      │                                     │",
            "│                                      │                                     │",
            "│                                      │                                     │",
            "│                                      │                                     │",
            "└──────────────────────────────────────┴─────────────────────────────────────┘",
            "",
            "COMMANDS: " + GREEN + "[1]" + RESET + " CALL  " + GREEN + "[2]" + RESET + " ANSWER  " + GREEN + "[3]" + RESET + " HANGUP  " + GREEN + "[4]" + RESET + " PROGRESS  " + RED + "[Q]" + RESET + " QUIT",
            "STATE:           REGISTRY:           LAST:",
            "",
            "Enter command: "
        };
        
        // Print the entire UI
        for (int i = 0; i < ui.length; i++) {
            System.out.print(ESC + (i + 1) + ";1H" + ui[i]);
        }
        
        // Initial dynamic values
        updateAllDynamicValues();
    }
    
    private void updateAllDynamicValues() {
        updateTimestamp();
        updateEventCount();
        updateStateCount();
        updateRecording();
        updateCallStatus();
        updateRingCount();
        updateStateStatus();
        updateRegistryStatus();
        updateLastEvent();
        updateHistories();
        System.out.flush();
    }
    
    private void updateTimestamp() {
        moveTo(TIME_ROW, TIME_COL);
        System.out.print(YELLOW + LocalDateTime.now().format(TIME_FORMAT) + RESET);
    }
    
    private void updateEventCount() {
        moveTo(EVENT_COUNT_ROW, EVENT_COUNT_COL);
        System.out.print(WHITE + String.format("%-6d", eventCounter.get()) + RESET);
    }
    
    private void updateStateCount() {
        moveTo(STATE_COUNT_ROW, STATE_COUNT_COL);
        System.out.print(WHITE + String.format("%-6d", stateHistory.size()) + RESET);
    }
    
    private void updateRecording() {
        moveTo(RECORDING_ROW, RECORDING_COL);
        boolean recording = context.isRecordingEnabled();
        System.out.print(recording ? GREEN + "ON " + RESET : RED + "OFF" + RESET);
    }
    
    private void updateCallStatus() {
        moveTo(CALL_STATUS_ROW, CALL_STATUS_COL);
        System.out.print(WHITE + String.format("%-20s", context.getCallStatus()) + RESET);
    }
    
    private void updateRingCount() {
        moveTo(RING_COUNT_ROW, RING_COUNT_COL);
        System.out.print(WHITE + String.format("%-3d", context.getRingCount()) + RESET);
    }
    
    private void updateStateStatus() {
        moveTo(STATE_STATUS_ROW, STATE_STATUS_COL);
        String state = machine.getCurrentState();
        String bg = getStateBackground(state);
        System.out.print(bg + String.format("%-8s", state) + RESET);
    }
    
    private void updateRegistryStatus() {
        moveTo(REGISTRY_STATUS_ROW, REGISTRY_STATUS_COL);
        boolean online = registry.isRegistered(MACHINE_ID);
        String bg = online ? BG_GREEN : BG_RED;
        System.out.print(bg + (online ? "ONLINE " : "OFFLINE") + RESET);
    }
    
    private void updateLastEvent() {
        moveTo(LAST_EVENT_ROW, LAST_EVENT_COL);
        System.out.print(YELLOW + String.format("%-12s", lastEventName) + RESET);
    }
    
    private void updateHistories() {
        // Update event history
        int eventStart = Math.max(0, eventHistory.size() - 6);
        for (int i = 0; i < 6; i++) {
            moveTo(17 + i, 3);
            if (eventStart + i < eventHistory.size()) {
                String event = eventHistory.get(eventStart + i);
                System.out.print(String.format("%-36s", event));
            } else {
                System.out.print(" ".repeat(36));
            }
        }
        
        // Update state history
        int stateStart = Math.max(0, stateHistory.size() - 6);
        for (int i = 0; i < 6; i++) {
            moveTo(17 + i, 42);
            if (stateStart + i < stateHistory.size()) {
                String state = stateHistory.get(stateStart + i);
                System.out.print(String.format("%-35s", state));
            } else {
                System.out.print(" ".repeat(35));
            }
        }
    }
    
    private String getStateBackground(String state) {
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
    
    private void startRefreshTimer() {
        scheduler.scheduleAtFixedRate(() -> {
            if (running) {
                updateTimestamp();
                updateCallStatus();
                updateRingCount();
                System.out.flush();
            }
        }, 2, 2, TimeUnit.SECONDS);
    }
    
    private void handleInput() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (running) {
                if (reader.ready()) {
                    String input = reader.readLine().trim().toLowerCase();
                    
                    // Clear input line
                    moveTo(29, 16);
                    System.out.print(" ".repeat(20));
                    
                    switch (input) {
                        case "1":
                            sendIncomingCall();
                            break;
                        case "2":
                            sendAnswer();
                            break;
                        case "3":
                            sendHangup();
                            break;
                        case "4":
                            sendSessionProgress();
                            break;
                        case "q":
                            running = false;
                            break;
                    }
                    
                    if (running) {
                        updateAllDynamicValues();
                        moveTo(29, 16);
                    }
                } else {
                    Thread.sleep(100);
                }
            }
        } catch (Exception e) {
            running = false;
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