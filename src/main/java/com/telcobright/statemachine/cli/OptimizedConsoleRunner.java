package com.telcobright.statemachine.cli;

import com.googlecode.lanterna.*;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import com.telcobright.statemachine.*;
import com.telcobright.statemachine.events.EventTypeRegistry;
import com.telcobright.statemachineexamples.callmachine.CallContext;
import com.telcobright.statemachineexamples.callmachine.CallState;
import com.telcobright.statemachineexamples.callmachine.events.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Optimized Console Runner with true in-place updates
 * Only updates values that actually change
 */
public class OptimizedConsoleRunner {
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final String MACHINE_ID = "optimized-" + System.currentTimeMillis();
    
    private final StateMachineRegistry registry = new StateMachineRegistry();
    private GenericStateMachine<CallContext, Void> machine;
    private CallContext context;
    
    // Lanterna components
    private Terminal terminal;
    private Screen screen;
    private TextGraphics textGraphics;
    
    // Auto-refresh
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private volatile boolean running = true;
    
    // History tracking
    private final LinkedList<String> eventHistory = new LinkedList<>();
    private final LinkedList<String> stateHistory = new LinkedList<>();
    private final AtomicInteger eventCounter = new AtomicInteger(0);
    private String lastEventName = "None";
    private String lastTransition = "None";
    private String userMessage = "";
    
    // Track previous values to minimize updates
    private String prevState = "";
    private int prevEventCount = 0;
    private int prevStateCount = 0;
    private String prevLastChange = "";
    private boolean prevRecording = false;
    private String prevCallStatus = "";
    private int prevRingCount = -1;
    private boolean prevOnline = false;
    
    private static final int MAX_HISTORY = 7;
    private boolean initialDraw = true;
    
    public static void main(String[] args) {
        try {
            new OptimizedConsoleRunner().run();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void run() throws Exception {
        try {
            // Initialize terminal and screen
            terminal = new DefaultTerminalFactory().createTerminal();
            screen = new TerminalScreen(terminal);
            screen.startScreen();
            screen.setCursorPosition(null); // Hide cursor
            textGraphics = screen.newTextGraphics();
            
            // Initialize state machine
            registerEventTypes();
            initializeStateMachine();
            
            // Draw static UI only once
            drawStaticUI();
            
            // Initial values update
            updateDynamicValues();
            screen.refresh();
            
            // Start auto-refresh for dynamic values only
            startAutoRefresh();
            
            // Handle input
            handleInput();
            
        } finally {
            cleanup();
        }
    }
    
    private void drawStaticUI() {
        // Clear screen once
        textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);
        textGraphics.fillRectangle(new TerminalPosition(0, 0), screen.getTerminalSize(), ' ');
        
        // Draw static title bar
        textGraphics.setForegroundColor(TextColor.ANSI.CYAN);
        textGraphics.putString(2, 0, "═".repeat(96));
        textGraphics.setForegroundColor(TextColor.ANSI.CYAN_BRIGHT);
        textGraphics.putString(32, 0, " INTERACTIVE STATE MACHINE MONITOR ");
        
        // Draw static boxes (borders and labels only)
        drawStaticBox(2, 1, 47, 8, "MACHINE INFO", TextColor.ANSI.YELLOW);
        drawStaticBox(2, 50, 47, 8, "CALL CONTEXT", TextColor.ANSI.YELLOW);
        drawStaticBox(11, 1, 47, 10, "EVENT HISTORY", TextColor.ANSI.CYAN);
        drawStaticBox(11, 50, 47, 10, "STATE TRANSITIONS", TextColor.ANSI.CYAN);
        
        // Draw static labels in Machine Info
        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
        textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);
        textGraphics.putString(3, 4, "Machine ID:");
        textGraphics.putString(3, 5, "Events:");
        textGraphics.putString(3, 6, "Changes:");
        textGraphics.putString(3, 7, "Started:");
        textGraphics.putString(3, 8, "Last Change:");
        textGraphics.putString(3, 9, "Recording:");
        
        // Draw static labels in Call Context
        textGraphics.putString(52, 4, "Call ID:");
        textGraphics.putString(52, 5, "From:");
        textGraphics.putString(52, 6, "To:");
        textGraphics.putString(52, 7, "Direction:");
        textGraphics.putString(52, 8, "Status:");
        textGraphics.putString(52, 9, "Ring Count:");
        
        // Draw static menu
        drawStaticMenu();
        
        // Static parts of status bar
        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
        textGraphics.putString(2, 28, "Press (1-4) for events, Q to quit");
        
        // Draw static values that don't change
        textGraphics.setForegroundColor(TextColor.ANSI.GREEN);
        textGraphics.putString(15, 4, MACHINE_ID);
        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
        textGraphics.putString(19, 7, context.getStartTime().format(TIME_FORMAT));
        textGraphics.putString(61, 4, context.getCallId());
        textGraphics.putString(58, 5, context.getFromNumber());
        textGraphics.putString(56, 6, context.getToNumber());
        textGraphics.putString(63, 7, context.getCallDirection());
    }
    
    private void drawStaticBox(int row, int col, int width, int height, String title, TextColor titleColor) {
        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
        textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);
        
        // Top border
        textGraphics.putString(col, row, "┌" + "─".repeat(width - 2) + "┐");
        
        // Title
        int titlePos = col + (width - title.length()) / 2;
        textGraphics.setForegroundColor(titleColor);
        textGraphics.putString(titlePos, row, " " + title + " ");
        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
        
        // Sides
        for (int i = 1; i < height; i++) {
            textGraphics.putString(col, row + i, "│");
            textGraphics.putString(col + width - 1, row + i, "│");
        }
        
        // Bottom border
        textGraphics.putString(col, row + height, "└" + "─".repeat(width - 2) + "┘");
    }
    
    private void drawStaticMenu() {
        int row = 22;
        
        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
        textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);
        textGraphics.putString(1, row, "┌" + "─".repeat(95) + "┐");
        textGraphics.putString(1, row + 1, "│");
        textGraphics.putString(97, row + 1, "│");
        textGraphics.putString(1, row + 2, "└" + "─".repeat(95) + "┘");
        
        textGraphics.putString(3, row + 1, "SEND EVENTS: ");
        
        textGraphics.setForegroundColor(TextColor.ANSI.GREEN);
        textGraphics.putString(16, row + 1, "[1]");
        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
        textGraphics.putString(19, row + 1, " INCOMING_CALL  ");
        
        textGraphics.setForegroundColor(TextColor.ANSI.GREEN);
        textGraphics.putString(35, row + 1, "[2]");
        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
        textGraphics.putString(38, row + 1, " ANSWER  ");
        
        textGraphics.setForegroundColor(TextColor.ANSI.GREEN);
        textGraphics.putString(47, row + 1, "[3]");
        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
        textGraphics.putString(50, row + 1, " HANGUP  ");
        
        textGraphics.setForegroundColor(TextColor.ANSI.GREEN);
        textGraphics.putString(59, row + 1, "[4]");
        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
        textGraphics.putString(62, row + 1, " SESSION_PROGRESS  ");
        
        textGraphics.setForegroundColor(TextColor.ANSI.RED);
        textGraphics.putString(81, row + 1, "[Q]");
        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
        textGraphics.putString(84, row + 1, " Quit");
    }
    
    private void updateDynamicValues() {
        // Update timestamp only
        textGraphics.setForegroundColor(TextColor.ANSI.YELLOW);
        textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);
        textGraphics.putString(70, 0, "[" + LocalDateTime.now().format(TIME_FORMAT) + "]");
        
        // Update event count if changed
        int currentEventCount = eventCounter.get();
        if (currentEventCount != prevEventCount) {
            textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
            textGraphics.putString(11, 5, String.format("%-10d", currentEventCount));
            prevEventCount = currentEventCount;
        }
        
        // Update state change count if changed
        int currentStateCount = stateHistory.size();
        if (currentStateCount != prevStateCount) {
            textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
            textGraphics.putString(12, 6, String.format("%-10d", currentStateCount));
            prevStateCount = currentStateCount;
        }
        
        // Update last change time if changed
        String currentLastChange = context.getLastStateChange() != null ? 
                                  context.getLastStateChange().format(TIME_FORMAT) : "N/A";
        if (!currentLastChange.equals(prevLastChange)) {
            textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
            textGraphics.putString(16, 8, currentLastChange + "    ");
            prevLastChange = currentLastChange;
        }
        
        // Update recording status if changed
        boolean currentRecording = context.isRecordingEnabled();
        if (currentRecording != prevRecording) {
            textGraphics.setForegroundColor(currentRecording ? TextColor.ANSI.GREEN : TextColor.ANSI.RED);
            textGraphics.putString(14, 9, currentRecording ? "ON " : "OFF");
            prevRecording = currentRecording;
        }
        
        // Update call status if changed
        String currentCallStatus = context.getCallStatus();
        if (!currentCallStatus.equals(prevCallStatus)) {
            textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
            textGraphics.putString(60, 8, currentCallStatus + "          ");
            prevCallStatus = currentCallStatus;
        }
        
        // Update ring count if changed
        int currentRingCount = context.getRingCount();
        if (currentRingCount != prevRingCount) {
            textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
            textGraphics.putString(64, 9, String.format("%-5d", currentRingCount));
            prevRingCount = currentRingCount;
        }
        
        // Update histories (only if changed)
        updateEventHistoryDisplay();
        updateStateHistoryDisplay();
        
        // Update status bar (state and registry)
        updateStatusBar();
        
        // Update user message if any
        if (!userMessage.isEmpty()) {
            textGraphics.setForegroundColor(TextColor.ANSI.GREEN);
            textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);
            textGraphics.putString(40, 28, userMessage + "          ");
        }
    }
    
    private void updateEventHistoryDisplay() {
        int row = 13;
        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
        textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);
        
        // Clear and update only if there are events
        if (!eventHistory.isEmpty()) {
            int i = 0;
            for (String event : eventHistory) {
                textGraphics.putString(3, row + i, event + "                    ");
                i++;
                if (i >= MAX_HISTORY) break;
            }
            // Clear remaining lines if needed
            while (i < MAX_HISTORY) {
                textGraphics.putString(3, row + i, "                                          ");
                i++;
            }
        } else if (initialDraw) {
            textGraphics.setForegroundColor(TextColor.ANSI.CYAN);
            textGraphics.putString(3, row, "(No events yet)");
        }
    }
    
    private void updateStateHistoryDisplay() {
        int row = 13;
        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
        textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);
        
        // Clear and update only if there are transitions
        if (!stateHistory.isEmpty()) {
            int i = 0;
            for (String transition : stateHistory) {
                textGraphics.putString(52, row + i, transition + "                    ");
                i++;
                if (i >= MAX_HISTORY) break;
            }
            // Clear remaining lines if needed
            while (i < MAX_HISTORY) {
                textGraphics.putString(52, row + i, "                                          ");
                i++;
            }
        } else if (initialDraw) {
            textGraphics.setForegroundColor(TextColor.ANSI.CYAN);
            textGraphics.putString(52, row, "(No transitions yet)");
        }
    }
    
    private void updateStatusBar() {
        String currentState = machine.getCurrentState();
        boolean currentOnline = registry.isRegistered(MACHINE_ID);
        
        // Update state if changed
        if (!currentState.equals(prevState)) {
            // Clear previous state display
            textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);
            textGraphics.putString(2, 26, "                ");
            
            // Draw new state
            TextColor stateBg = getStateBackground(currentState);
            textGraphics.setBackgroundColor(stateBg);
            textGraphics.setForegroundColor(TextColor.ANSI.BLACK);
            textGraphics.putString(2, 26, " STATE: " + currentState + " ");
            prevState = currentState;
        }
        
        // Update registry status if changed
        if (currentOnline != prevOnline || initialDraw) {
            textGraphics.setBackgroundColor(currentOnline ? TextColor.ANSI.GREEN : TextColor.ANSI.RED);
            textGraphics.setForegroundColor(currentOnline ? TextColor.ANSI.BLACK : TextColor.ANSI.WHITE);
            textGraphics.putString(20, 26, " REGISTRY: " + (currentOnline ? "ONLINE" : "OFFLINE") + " ");
            prevOnline = currentOnline;
        }
        
        // Update last event and transition
        textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);
        textGraphics.setForegroundColor(TextColor.ANSI.YELLOW);
        textGraphics.putString(40, 26, "Event: " + lastEventName + "          ");
        
        textGraphics.setForegroundColor(TextColor.ANSI.CYAN);
        textGraphics.putString(65, 26, "Trans: " + lastTransition + "          ");
        
        initialDraw = false;
    }
    
    private TextColor getStateBackground(String state) {
        switch (state) {
            case "IDLE": return TextColor.ANSI.GREEN;
            case "RINGING": return TextColor.ANSI.YELLOW;
            case "CONNECTED": return TextColor.ANSI.BLUE;
            default: return TextColor.ANSI.RED;
        }
    }
    
    private void startAutoRefresh() {
        scheduler.scheduleAtFixedRate(() -> {
            if (running) {
                try {
                    updateDynamicValues();
                    screen.refresh();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }, 2, 2, TimeUnit.SECONDS);
    }
    
    private void handleInput() throws IOException {
        while (running) {
            KeyStroke keyStroke = screen.pollInput();
            
            if (keyStroke == null) {
                sleep(50);
                continue;
            }
            
            if (keyStroke.getKeyType() == KeyType.Character) {
                char c = keyStroke.getCharacter();
                userMessage = "";
                
                switch (c) {
                    case '1':
                        sendIncomingCall();
                        userMessage = "Sent INCOMING_CALL";
                        break;
                    case '2':
                        sendAnswer();
                        userMessage = "Sent ANSWER";
                        break;
                    case '3':
                        sendHangup();
                        userMessage = "Sent HANGUP";
                        break;
                    case '4':
                        sendSessionProgress();
                        userMessage = "Sent SESSION_PROGRESS";
                        break;
                    case 'q':
                    case 'Q':
                        running = false;
                        break;
                }
                
                updateDynamicValues();
                screen.refresh();
                
                // Clear message after a moment
                scheduler.schedule(() -> {
                    userMessage = "";
                    try {
                        updateDynamicValues();
                        screen.refresh();
                    } catch (IOException e) {
                        // Ignore
                    }
                }, 2, TimeUnit.SECONDS);
            }
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
        while (eventHistory.size() > MAX_HISTORY) {
            eventHistory.removeFirst();
        }
    }
    
    private void addStateHistory(String transition) {
        stateHistory.add(transition);
        while (stateHistory.size() > MAX_HISTORY) {
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
        
        if (screen != null) {
            try {
                screen.stopScreen();
            } catch (IOException e) {
                // Ignore
            }
        }
        
        if (terminal != null) {
            try {
                terminal.close();
            } catch (IOException e) {
                // Ignore
            }
        }
        
        registry.removeMachine(MACHINE_ID);
    }
    
    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}