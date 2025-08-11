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
 * Flicker-free Console Runner using Lanterna's proper screen buffering
 * Professional terminal UI with smooth updates
 */
public class LanternaConsoleRunner {
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final String MACHINE_ID = "lanterna-" + System.currentTimeMillis();
    
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
    
    private static final int MAX_HISTORY = 7;
    
    public static void main(String[] args) {
        try {
            new LanternaConsoleRunner().run();
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
            
            // Draw initial UI
            drawUI();
            screen.refresh();
            
            // Start auto-refresh
            startAutoRefresh();
            
            // Handle input
            handleInput();
            
        } finally {
            cleanup();
        }
    }
    
    private void drawUI() {
        // Clear screen with background
        textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);
        textGraphics.fillRectangle(new TerminalPosition(0, 0), screen.getTerminalSize(), ' ');
        
        // Title
        drawTitle();
        
        // Machine Info Box (left)
        drawMachineInfo();
        
        // Call Context Box (right)
        drawCallContext();
        
        // Event History Box (left)
        drawEventHistory();
        
        // State History Box (right)
        drawStateHistory();
        
        // Send Events Menu
        drawMenu();
        
        // Status Bar
        drawStatusBar();
        
        // User message/prompt
        drawPrompt();
    }
    
    private void drawTitle() {
        int row = 0;
        String title = "INTERACTIVE STATE MACHINE MONITOR";
        String timestamp = "[" + LocalDateTime.now().format(TIME_FORMAT) + "]";
        
        textGraphics.setForegroundColor(TextColor.ANSI.CYAN);
        textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);
        textGraphics.putString(2, row, "═".repeat(96));
        
        textGraphics.setForegroundColor(TextColor.ANSI.CYAN_BRIGHT);
        textGraphics.putString(32, row, " " + title + " ");
        
        textGraphics.setForegroundColor(TextColor.ANSI.YELLOW);
        textGraphics.putString(70, row, timestamp);
    }
    
    private void drawMachineInfo() {
        int startRow = 2;
        int startCol = 1;
        
        // Box border
        drawBox(startRow, startCol, 47, 8, "MACHINE INFO", TextColor.ANSI.YELLOW);
        
        // Content
        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
        textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);
        
        textGraphics.putString(startCol + 2, startRow + 2, "Machine ID: ");
        textGraphics.setForegroundColor(TextColor.ANSI.GREEN);
        textGraphics.putString(startCol + 14, startRow + 2, MACHINE_ID);
        
        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
        textGraphics.putString(startCol + 2, startRow + 3, "Events: " + eventCounter.get());
        textGraphics.putString(startCol + 2, startRow + 4, "Changes: " + stateHistory.size());
        textGraphics.putString(startCol + 2, startRow + 5, "Started: " + context.getStartTime().format(TIME_FORMAT));
        
        String lastChange = context.getLastStateChange() != null ? 
                           context.getLastStateChange().format(TIME_FORMAT) : "N/A";
        textGraphics.putString(startCol + 2, startRow + 6, "Last Change: " + lastChange);
        
        textGraphics.putString(startCol + 2, startRow + 7, "Recording: ");
        textGraphics.setForegroundColor(context.isRecordingEnabled() ? TextColor.ANSI.GREEN : TextColor.ANSI.RED);
        textGraphics.putString(startCol + 13, startRow + 7, context.isRecordingEnabled() ? "ON" : "OFF");
    }
    
    private void drawCallContext() {
        int startRow = 2;
        int startCol = 50;
        
        // Box border
        drawBox(startRow, startCol, 47, 8, "CALL CONTEXT", TextColor.ANSI.YELLOW);
        
        // Content
        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
        textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);
        
        textGraphics.putString(startCol + 2, startRow + 2, "Call ID: " + context.getCallId());
        textGraphics.putString(startCol + 2, startRow + 3, "From: " + context.getFromNumber());
        textGraphics.putString(startCol + 2, startRow + 4, "To: " + context.getToNumber());
        textGraphics.putString(startCol + 2, startRow + 5, "Direction: " + context.getCallDirection());
        textGraphics.putString(startCol + 2, startRow + 6, "Status: " + context.getCallStatus());
        textGraphics.putString(startCol + 2, startRow + 7, "Ring Count: " + context.getRingCount());
    }
    
    private void drawEventHistory() {
        int startRow = 11;
        int startCol = 1;
        
        // Box border
        drawBox(startRow, startCol, 47, 10, "EVENT HISTORY", TextColor.ANSI.CYAN);
        
        // Content
        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
        textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);
        
        if (eventHistory.isEmpty()) {
            textGraphics.setForegroundColor(TextColor.ANSI.CYAN);
            textGraphics.putString(startCol + 2, startRow + 2, "(No events yet)");
        } else {
            int row = startRow + 2;
            for (String event : eventHistory) {
                // Clear the line first
                textGraphics.putString(startCol + 2, row, " ".repeat(43));
                textGraphics.putString(startCol + 2, row, event);
                row++;
                if (row >= startRow + 9) break;
            }
        }
    }
    
    private void drawStateHistory() {
        int startRow = 11;
        int startCol = 50;
        
        // Box border
        drawBox(startRow, startCol, 47, 10, "STATE TRANSITIONS", TextColor.ANSI.CYAN);
        
        // Content
        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
        textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);
        
        if (stateHistory.isEmpty()) {
            textGraphics.setForegroundColor(TextColor.ANSI.CYAN);
            textGraphics.putString(startCol + 2, startRow + 2, "(No transitions yet)");
        } else {
            int row = startRow + 2;
            for (String transition : stateHistory) {
                // Clear the line first
                textGraphics.putString(startCol + 2, row, " ".repeat(43));
                textGraphics.putString(startCol + 2, row, transition);
                row++;
                if (row >= startRow + 9) break;
            }
        }
    }
    
    private void drawMenu() {
        int row = 22;
        
        // Menu box
        textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
        textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);
        textGraphics.putString(1, row, "┌" + "─".repeat(95) + "┐");
        textGraphics.putString(1, row + 1, "│");
        textGraphics.putString(97, row + 1, "│");
        textGraphics.putString(1, row + 2, "└" + "─".repeat(95) + "┘");
        
        // Menu items
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
    
    private void drawStatusBar() {
        int row = 26;
        String state = machine.getCurrentState();
        boolean isOnline = registry.isRegistered(MACHINE_ID);
        
        // Clear the line
        textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);
        textGraphics.putString(0, row, " ".repeat(100));
        
        // State with background
        TextColor stateBg = getStateBackground(state);
        textGraphics.setBackgroundColor(stateBg);
        textGraphics.setForegroundColor(TextColor.ANSI.BLACK);
        textGraphics.putString(2, row, " STATE: " + state + " ");
        
        // Registry status
        textGraphics.setBackgroundColor(isOnline ? TextColor.ANSI.GREEN : TextColor.ANSI.RED);
        textGraphics.setForegroundColor(isOnline ? TextColor.ANSI.BLACK : TextColor.ANSI.WHITE);
        textGraphics.putString(20, row, " REGISTRY: " + (isOnline ? "ONLINE" : "OFFLINE") + " ");
        
        // Last event and transition
        textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);
        textGraphics.setForegroundColor(TextColor.ANSI.YELLOW);
        textGraphics.putString(40, row, "Last Event: " + lastEventName);
        
        textGraphics.setForegroundColor(TextColor.ANSI.CYAN);
        textGraphics.putString(65, row, "Last: " + lastTransition);
    }
    
    private void drawPrompt() {
        int row = 28;
        textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);
        textGraphics.setForegroundColor(TextColor.ANSI.CYAN);
        textGraphics.putString(2, row, "Enter event (1-4) or Q to quit: ");
        
        // User message
        if (!userMessage.isEmpty()) {
            textGraphics.setForegroundColor(TextColor.ANSI.GREEN);
            textGraphics.putString(35, row, userMessage + "    ");
        }
    }
    
    private void drawBox(int row, int col, int width, int height, String title, TextColor titleColor) {
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
                    drawUI();
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
                
                drawUI();
                screen.refresh();
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