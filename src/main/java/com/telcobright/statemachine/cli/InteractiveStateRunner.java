package com.telcobright.statemachine.cli;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.TextInputDialog;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import com.telcobright.statemachine.*;
import com.telcobright.statemachine.events.EventTypeRegistry;
import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachine.monitoring.AbstractMachineSnapshot;
import com.telcobright.statemachineexamples.callmachine.CallContext;
import com.telcobright.statemachineexamples.callmachine.CallState;
import com.telcobright.statemachineexamples.callmachine.events.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Interactive CLI-based State Machine Runner with real-time monitoring
 * Provides a terminal UI for testing and monitoring state machines
 */
public class InteractiveStateRunner {
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final String MACHINE_ID = "cli-machine-" + System.currentTimeMillis();
    
    private final StateMachineRegistry registry = new StateMachineRegistry();
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    
    private GenericStateMachine<CallContext, CallVolumeContext> machine;
    private CallContext persistableContext;
    private CallVolumeContext volatileContext;
    
    // UI Components
    private MultiWindowTextGUI gui;
    private BasicWindow window;
    private Label stateLabel;
    private Label registryStatusLabel;
    private Label machineCountLabel;
    private Label lastEventLabel;
    private Label lastTransitionLabel;
    private TextBox persistableContextBox;
    private TextBox volatileContextBox;
    private TextBox eventHistoryBox;
    private TextBox stateHistoryBox;
    private Button sendEventButton;
    private ComboBox<String> eventTypeCombo;
    
    // Event history tracking
    private final List<String> eventHistory = new ArrayList<>();
    private final List<String> stateHistory = new ArrayList<>();
    private final AtomicInteger eventCounter = new AtomicInteger(0);
    
    // Volatile context for call volume tracking
    public static class CallVolumeContext {
        private int totalCalls = 0;
        private int answeredCalls = 0;
        private int missedCalls = 0;
        private int droppedCalls = 0;
        private long totalDuration = 0;
        private Map<String, Integer> callsByNumber = new ConcurrentHashMap<>();
        private LocalDateTime lastActivity = LocalDateTime.now();
        
        public void incrementTotalCalls() { totalCalls++; }
        public void incrementAnsweredCalls() { answeredCalls++; }
        public void incrementMissedCalls() { missedCalls++; }
        public void incrementDroppedCalls() { droppedCalls++; }
        public void addDuration(long seconds) { totalDuration += seconds; }
        public void recordCall(String number) {
            callsByNumber.merge(number, 1, Integer::sum);
            lastActivity = LocalDateTime.now();
        }
        
        @Override
        public String toString() {
            return String.format(
                "Total: %d | Answered: %d | Missed: %d\n" +
                "Dropped: %d | Duration: %ds\n" +
                "Unique Numbers: %d\n" +
                "Last Activity: %s",
                totalCalls, answeredCalls, missedCalls,
                droppedCalls, totalDuration,
                callsByNumber.size(),
                lastActivity.format(TIME_FORMAT)
            );
        }
    }
    
    public static void main(String[] args) {
        try {
            new InteractiveStateRunner().run();
        } catch (Exception e) {
            System.err.println("Error running Interactive State Runner: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void run() throws IOException {
        // Register event types
        registerEventTypes();
        
        // Initialize state machine
        initializeStateMachine();
        
        // Create terminal and screen
        Terminal terminal = new DefaultTerminalFactory().createTerminal();
        Screen screen = new TerminalScreen(terminal);
        screen.startScreen();
        
        // Create GUI
        gui = new MultiWindowTextGUI(screen, new DefaultWindowManager(), new EmptySpace(TextColor.ANSI.BLACK));
        
        // Create main window
        createMainWindow();
        
        // Start update timer
        startUpdateTimer();
        
        // Run GUI
        gui.addWindowAndWait(window);
        
        // Cleanup
        cleanup();
        screen.stopScreen();
    }
    
    private void registerEventTypes() {
        EventTypeRegistry.register(IncomingCall.class, "INCOMING_CALL");
        EventTypeRegistry.register(Answer.class, "ANSWER");
        EventTypeRegistry.register(Hangup.class, "HANGUP");
        EventTypeRegistry.register(SessionProgress.class, "SESSION_PROGRESS");
    }
    
    private void initializeStateMachine() {
        // Create contexts
        persistableContext = new CallContext(MACHINE_ID, "+1-555-0001", "+1-555-0002");
        volatileContext = new CallVolumeContext();
        
        // Build state machine with simple transitions
        machine = FluentStateMachineBuilder.<CallContext, CallVolumeContext>create(MACHINE_ID)
            .initialState(CallState.IDLE)
            
            .state(CallState.IDLE)
                .on(IncomingCall.class).to(CallState.RINGING)
                .done()
                
            .state(CallState.RINGING)
                .timeout(30, com.telcobright.statemachine.timeout.TimeUnit.SECONDS, CallState.IDLE.name())
                .on(Answer.class).to(CallState.CONNECTED)
                .on(Hangup.class).to(CallState.IDLE)
                .stay(SessionProgress.class, (machine, event) -> {})
                .done()
                
            .state(CallState.CONNECTED)
                .on(Hangup.class).to(CallState.IDLE)
                .done()
                
            .build();
        
        // Set contexts
        machine.setPersistingEntity(persistableContext);
        // Volatile context is not directly supported - store it separately
        
        // Register machine
        registry.register(MACHINE_ID, machine);
        
        // Start machine
        machine.start();
        
        // Record initial state
        stateHistory.add(String.format("[%s] Initial -> %s", 
            LocalDateTime.now().format(TIME_FORMAT), 
            machine.getCurrentState()));
            
        // Increment call volume
        volatileContext.incrementTotalCalls();
        volatileContext.recordCall(persistableContext.getFromNumber());
    }
    
    private void createMainWindow() {
        window = new BasicWindow("Interactive State Machine Runner");
        window.setHints(Arrays.asList(Window.Hint.FULL_SCREEN));
        
        Panel mainPanel = new Panel();
        mainPanel.setLayoutManager(new BorderLayout());
        
        // Top panel - Machine status
        Panel topPanel = new Panel(new GridLayout(2));
        topPanel.addComponent(new Label("═".repeat(80)).setForegroundColor(TextColor.ANSI.CYAN));
        
        Panel statusPanel = new Panel(new GridLayout(4));
        statusPanel.addComponent(new Label("Machine ID:").setForegroundColor(TextColor.ANSI.YELLOW));
        statusPanel.addComponent(new Label(MACHINE_ID));
        
        statusPanel.addComponent(new Label("Current State:").setForegroundColor(TextColor.ANSI.YELLOW));
        stateLabel = new Label(machine.getCurrentState());
        stateLabel.setForegroundColor(TextColor.ANSI.GREEN);
        statusPanel.addComponent(stateLabel);
        
        statusPanel.addComponent(new Label("Registry Status:").setForegroundColor(TextColor.ANSI.YELLOW));
        registryStatusLabel = new Label(getRegistryStatus());
        statusPanel.addComponent(registryStatusLabel);
        
        statusPanel.addComponent(new Label("Machine Count:").setForegroundColor(TextColor.ANSI.YELLOW));
        machineCountLabel = new Label("1");
        statusPanel.addComponent(machineCountLabel);
        
        topPanel.addComponent(statusPanel);
        topPanel.addComponent(new Label("═".repeat(80)).setForegroundColor(TextColor.ANSI.CYAN));
        
        // Middle panel - Context displays
        Panel middlePanel = new Panel(new GridLayout(2));
        
        // Persistable context
        Panel persistablePanel = new Panel(new BorderLayout());
        persistablePanel.addComponent(new Label("PERSISTABLE CONTEXT").setForegroundColor(TextColor.ANSI.MAGENTA), BorderLayout.Location.TOP);
        persistableContextBox = new TextBox(new TerminalSize(38, 8));
        persistableContextBox.setReadOnly(true);
        persistableContextBox.setText(formatPersistableContext());
        persistablePanel.addComponent(persistableContextBox, BorderLayout.Location.CENTER);
        
        // Volatile context  
        Panel volatilePanel = new Panel(new BorderLayout());
        volatilePanel.addComponent(new Label("VOLATILE CONTEXT").setForegroundColor(TextColor.ANSI.MAGENTA), BorderLayout.Location.TOP);
        volatileContextBox = new TextBox(new TerminalSize(38, 8));
        volatileContextBox.setReadOnly(true);
        volatileContextBox.setText(volatileContext.toString());
        volatilePanel.addComponent(volatileContextBox, BorderLayout.Location.CENTER);
        
        middlePanel.addComponent(persistablePanel);
        middlePanel.addComponent(volatilePanel);
        
        // History panels
        Panel historyPanel = new Panel(new GridLayout(2));
        
        // Event history
        Panel eventPanel = new Panel(new BorderLayout());
        eventPanel.addComponent(new Label("EVENT HISTORY").setForegroundColor(TextColor.ANSI.CYAN), BorderLayout.Location.TOP);
        eventHistoryBox = new TextBox(new TerminalSize(38, 10));
        eventHistoryBox.setReadOnly(true);
        eventPanel.addComponent(eventHistoryBox, BorderLayout.Location.CENTER);
        
        // State history
        Panel statePanel = new Panel(new BorderLayout());
        statePanel.addComponent(new Label("STATE TRANSITIONS").setForegroundColor(TextColor.ANSI.CYAN), BorderLayout.Location.TOP);
        stateHistoryBox = new TextBox(new TerminalSize(38, 10));
        stateHistoryBox.setReadOnly(true);
        statePanel.addComponent(stateHistoryBox, BorderLayout.Location.CENTER);
        
        historyPanel.addComponent(eventPanel);
        historyPanel.addComponent(statePanel);
        
        // Bottom panel - Event controls
        Panel bottomPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        bottomPanel.addComponent(new Label("Send Event:"));
        
        eventTypeCombo = new ComboBox<String>();
        eventTypeCombo.addItem("INCOMING_CALL");
        eventTypeCombo.addItem("ANSWER");
        eventTypeCombo.addItem("HANGUP");
        eventTypeCombo.addItem("SESSION_PROGRESS");
        bottomPanel.addComponent(eventTypeCombo);
        
        sendEventButton = new Button("Send [Enter]", this::sendEvent);
        bottomPanel.addComponent(sendEventButton);
        
        Button refreshButton = new Button("Refresh [R]", this::refresh);
        bottomPanel.addComponent(refreshButton);
        
        Button exitButton = new Button("Exit [Q]", () -> window.close());
        bottomPanel.addComponent(exitButton);
        
        // Last event/transition labels
        Panel infoPanel = new Panel(new GridLayout(2));
        infoPanel.addComponent(new Label("Last Event:").setForegroundColor(TextColor.ANSI.YELLOW));
        lastEventLabel = new Label("None");
        infoPanel.addComponent(lastEventLabel);
        
        infoPanel.addComponent(new Label("Last Transition:").setForegroundColor(TextColor.ANSI.YELLOW));
        lastTransitionLabel = new Label("None");
        infoPanel.addComponent(lastTransitionLabel);
        
        // Assemble main panel
        mainPanel.addComponent(topPanel, BorderLayout.Location.TOP);
        mainPanel.addComponent(middlePanel, BorderLayout.Location.CENTER);
        mainPanel.addComponent(historyPanel, BorderLayout.Location.CENTER);
        mainPanel.addComponent(infoPanel, BorderLayout.Location.CENTER);
        mainPanel.addComponent(new Separator(Direction.HORIZONTAL), BorderLayout.Location.CENTER);
        mainPanel.addComponent(bottomPanel, BorderLayout.Location.BOTTOM);
        
        window.setComponent(mainPanel);
    }
    
    private void sendEvent() {
        String eventType = eventTypeCombo.getSelectedItem();
        String previousState = machine.getCurrentState();
        
        try {
            boolean stateChanged = false;
            String eventDetails = "";
            
            switch (eventType) {
                case "INCOMING_CALL":
                    String callerNumber = TextInputDialog.showDialog(gui, "Incoming Call", "Enter caller number:", "+1-555-9999");
                    if (callerNumber != null) {
                        IncomingCall call = new IncomingCall(callerNumber);
                        machine.fire(call);
                        eventDetails = " from " + callerNumber;
                        volatileContext.incrementTotalCalls();
                        volatileContext.recordCall(callerNumber);
                    }
                    break;
                    
                case "ANSWER":
                    machine.fire(new Answer());
                    volatileContext.incrementAnsweredCalls();
                    persistableContext.setConnectTime(LocalDateTime.now());
                    break;
                    
                case "HANGUP":
                    machine.fire(new Hangup());
                    if ("RINGING".equals(previousState)) {
                        volatileContext.incrementMissedCalls();
                    } else if ("CONNECTED".equals(previousState)) {
                        // Calculate call duration
                        if (persistableContext.getConnectTime() != null) {
                            long duration = java.time.Duration.between(
                                persistableContext.getConnectTime(), 
                                LocalDateTime.now()
                            ).getSeconds();
                            volatileContext.addDuration(duration);
                        }
                    }
                    break;
                    
                case "SESSION_PROGRESS":
                    // SessionProgress requires parameters
                    String sdp = TextInputDialog.showDialog(gui, "Session Progress", "Enter SDP (or press OK for default):", "v=0");
                    if (sdp != null) {
                        machine.fire(new SessionProgress(sdp, persistableContext.getRingCount() + 1));
                        persistableContext.setRingCount(persistableContext.getRingCount() + 1);
                        eventDetails = " (Ring #" + persistableContext.getRingCount() + ")";
                    }
                    break;
            }
            
            String currentState = machine.getCurrentState();
            stateChanged = !previousState.equals(currentState);
            
            // Record event
            String timestamp = LocalDateTime.now().format(TIME_FORMAT);
            String eventEntry = String.format("[%s] %s%s", timestamp, eventType, eventDetails);
            eventHistory.add(eventEntry);
            eventCounter.incrementAndGet();
            
            // Record state change if occurred
            if (stateChanged) {
                String transition = String.format("[%s] %s -> %s", 
                    timestamp, previousState, currentState);
                stateHistory.add(transition);
                lastTransitionLabel.setText(String.format("%s -> %s", previousState, currentState));
            }
            
            lastEventLabel.setText(eventType + eventDetails);
            
            // Update UI
            updateUI();
            
            // Show success message
            if (stateChanged) {
                MessageDialog.showMessageDialog(gui, "Event Sent", 
                    String.format("Event: %s\nTransition: %s -> %s", 
                        eventType, previousState, currentState));
            } else {
                MessageDialog.showMessageDialog(gui, "Event Sent", 
                    String.format("Event: %s\nState unchanged: %s", 
                        eventType, currentState));
            }
            
        } catch (Exception e) {
            MessageDialog.showMessageDialog(gui, "Error", 
                "Failed to send event: " + e.getMessage());
        }
    }
    
    private void refresh() {
        updateUI();
    }
    
    private void updateUI() {
        // Update state
        stateLabel.setText(machine.getCurrentState());
        
        // Update registry status
        registryStatusLabel.setText(getRegistryStatus());
        
        // Update contexts
        persistableContextBox.setText(formatPersistableContext());
        volatileContextBox.setText(volatileContext.toString());
        
        // Update histories
        updateEventHistory();
        updateStateHistory();
        
        // Update event count
        machineCountLabel.setText("Events: " + eventCounter.get());
    }
    
    private void updateEventHistory() {
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, eventHistory.size() - 9);
        for (int i = start; i < eventHistory.size(); i++) {
            sb.append(eventHistory.get(i)).append("\n");
        }
        eventHistoryBox.setText(sb.toString());
    }
    
    private void updateStateHistory() {
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, stateHistory.size() - 9);
        for (int i = start; i < stateHistory.size(); i++) {
            sb.append(stateHistory.get(i)).append("\n");
        }
        stateHistoryBox.setText(sb.toString());
    }
    
    private String formatPersistableContext() {
        return String.format(
            "Call ID: %s\n" +
            "From: %s\n" +
            "To: %s\n" +
            "Direction: %s\n" +
            "Status: %s\n" +
            "Ring Count: %d\n" +
            "Recording: %s\n" +
            "Complete: %s",
            persistableContext.getCallId(),
            persistableContext.getFromNumber(),
            persistableContext.getToNumber(),
            persistableContext.getCallDirection(),
            persistableContext.getCallStatus(),
            persistableContext.getRingCount(),
            persistableContext.isRecordingEnabled() ? "Yes" : "No",
            persistableContext.isComplete() ? "Yes" : "No"
        );
    }
    
    private String getRegistryStatus() {
        // Check if machine is still registered
        try {
            boolean isRegistered = registry.isRegistered(MACHINE_ID);
            return isRegistered ? "ONLINE" : "OFFLINE";
        } catch (Exception e) {
            return "ONLINE"; // Assume online if we can't check
        }
    }
    
    private void startUpdateTimer() {
        // Auto-refresh UI every second
        executor.scheduleAtFixedRate(() -> {
            if (gui != null) {
                gui.getGUIThread().invokeLater(this::updateUI);
            }
        }, 1, 1, java.util.concurrent.TimeUnit.SECONDS);
    }
    
    private void cleanup() {
        executor.shutdown();
        registry.removeMachine(MACHINE_ID);
    }
}