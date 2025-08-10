package com.telcobright.statemachine.test;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.StateMachineRegistry;
import com.telcobright.statemachine.events.TimeoutEvent;
import com.telcobright.statemachine.events.EventTypeRegistry;
import com.telcobright.statemachineexamples.callmachine.InteractiveCallMachine;
import com.telcobright.statemachineexamples.callmachine.entity.CallEntity;
import com.telcobright.statemachineexamples.callmachine.CallContext;
import com.telcobright.statemachineexamples.callmachine.CallState;
import com.telcobright.statemachineexamples.callmachine.events.IncomingCall;
import com.telcobright.statemachineexamples.callmachine.events.Answer;
import com.telcobright.statemachineexamples.callmachine.events.Hangup;
import com.telcobright.statemachineexamples.callmachine.events.SessionProgress;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;

/**
 * Interactive CallMachine Demo with Keypress Events and Timeouts
 * 
 * This demo allows you to manually send events to a CallMachine by pressing Enter.
 * Each state has a 30-second timeout - if no event is sent, it will timeout automatically.
 * 
 * Usage:
 * 1. Start the demo
 * 2. Follow the prompts to send events
 * 3. Press Enter to send each event or wait 30 seconds for timeout
 */
public class InteractiveCallMachineDemo {
    
    private static final int TIMEOUT_SECONDS = 30;
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static GenericStateMachine<CallEntity, CallContext> machine;
    private static CallContext context;
    private static Scanner scanner = new Scanner(System.in);
    private static boolean demoActive = true;
    private static ScheduledFuture<?> currentTimeout;
    
    public static void main(String[] args) throws Exception {
        printHeader();
        startDemo();
    }
    
    private static void printHeader() {
        System.out.println("📞 Interactive CallMachine Demo");
        System.out.println("==============================");
        System.out.println("🎯 Send events by pressing Enter or wait " + TIMEOUT_SECONDS + " seconds for timeout");
        System.out.println("🔧 Monitoring available at: http://localhost:8091");
        System.out.println();
    }
    
    private static void startDemo() throws Exception {
        String callId = "interactive-call-" + System.currentTimeMillis();
        
        System.out.println("📱 Creating interactive call scenario");
        System.out.println("   Call ID: " + callId);
        System.out.println("   Timeout: " + TIMEOUT_SECONDS + " seconds per state");
        System.out.println();
        
        // Register event types to match their getEventType() values
        EventTypeRegistry.register(IncomingCall.class, "INCOMING_CALL");
        EventTypeRegistry.register(SessionProgress.class, "SESSION_PROGRESS");
        EventTypeRegistry.register(Answer.class, "ANSWER");
        EventTypeRegistry.register(Hangup.class, "HANGUP");
        
        // Create registry and machine with timeout support
        StateMachineRegistry registry = new StateMachineRegistry();
        machine = InteractiveCallMachine.create(callId);
        registry.register(callId, machine);
        
        // Create the CallEntity (persisted entity)
        CallEntity callEntity = new CallEntity(callId, CallState.IDLE.name(), "+1-555-CALLER", "+1-555-RECEIVER");
        machine.setPersistingEntity(callEntity);
        
        // Set initial context (volatile context)
        context = new CallContext();
        context.setCallId(callId);
        context.setFromNumber("+1-555-CALLER");
        context.setToNumber("+1-555-RECEIVER");
        machine.setContext(context);
        
        // Start the machine
        machine.start();
        
        // Main demo loop
        while (demoActive && !machine.isComplete()) {
            String currentState = machine.getCurrentState();
            
            if (CallState.IDLE.name().equals(currentState)) {
                handleIdleState();
            } else if (CallState.RINGING.name().equals(currentState)) {
                handleRingingState();
            } else if (CallState.CONNECTED.name().equals(currentState)) {
                handleConnectedState();
            } else {
                System.out.println("⚠️ Unknown state: " + currentState);
                demoActive = false;
            }
        }
        
        // Cleanup
        if (currentTimeout != null) {
            currentTimeout.cancel(false);
        }
        registry.shutdown();
        scheduler.shutdown();
        
        System.out.println();
        System.out.println("✅ Interactive call demo completed!");
        System.out.println("📊 Final state: " + machine.getCurrentState());
        System.out.println("🔍 View complete history at: http://localhost:8091");
    }
    
    private static void handleIdleState() {
        IncomingCall incomingCall = new IncomingCall("+1-555-CALLER");
        
        System.out.println("Next Event: INCOMING_CALL");
        System.out.println("📦 Payload: {");
        System.out.println("     \"callerNumber\": \"" + incomingCall.getCallerNumber() + "\",");
        System.out.println("     \"timestamp\": " + incomingCall.getTimestamp() + ",");
        System.out.println("     \"description\": \"" + incomingCall.getDescription() + "\"");
        System.out.println("   }");
        
        if (waitForUserInputOrTimeout()) {
            machine.fire(incomingCall);
        } else {
            demoActive = false;
        }
    }
    
    private static void handleRingingState() {
        // First, offer SessionProgress
        SessionProgress sessionProgress = new SessionProgress("EARLY_MEDIA", 100);
        
        System.out.println("Next Event: SESSION_PROGRESS");
        System.out.println("📦 Payload: {");
        System.out.println("     \"progressType\": \"" + sessionProgress.getProgressType() + "\",");
        System.out.println("     \"percentage\": " + sessionProgress.getPercentage() + ",");
        System.out.println("     \"description\": \"" + sessionProgress.getDescription() + "\"");
        System.out.println("   }");
        
        if (waitForUserInputOrTimeout()) {
            machine.fire(sessionProgress);
            
            // After session progress, offer Answer
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            Answer answer = new Answer();
            System.out.println("Next Event: ANSWER");
            System.out.println("📦 Payload: {");
            System.out.println("     \"timestamp\": " + answer.getTimestamp() + ",");
            System.out.println("     \"description\": \"" + answer.getDescription() + "\"");
            System.out.println("   }");
            
            if (waitForUserInputOrTimeout()) {
                machine.fire(answer);
            } else {
                Hangup timeoutHangup = new Hangup();
                machine.fire(timeoutHangup);
            }
        } else {
            Hangup timeoutHangup = new Hangup();
            machine.fire(timeoutHangup);
        }
    }
    
    private static void handleConnectedState() {
        Hangup hangup = new Hangup();
        
        System.out.println("Next Event: HANGUP");
        System.out.println("📦 Payload: {");
        System.out.println("     \"timestamp\": " + hangup.getTimestamp() + ",");
        System.out.println("     \"description\": \"" + hangup.getDescription() + "\"");
        System.out.println("   }");
        
        if (waitForUserInputOrTimeout()) {
            machine.fire(hangup);
        } else {
            machine.fire(hangup);
        }
    }
    
    private static boolean waitForUserInputOrTimeout() {
        final boolean[] userResponded = {false};
        final boolean[] timeoutOccurred = {false};
        final int[] remainingSeconds = {TIMEOUT_SECONDS};
        
        // Initial prompt
        System.out.print("Press Enter to send to Machine. Timeout in " + TIMEOUT_SECONDS + " sec");
        
        // Start countdown timer (updates every second) 
        ScheduledFuture<?> countdownTimer = scheduler.scheduleAtFixedRate(() -> {
            synchronized (userResponded) {
                if (!userResponded[0]) {
                    remainingSeconds[0]--;
                    // Clear current line and show updated countdown
                    System.out.print("\rPress Enter to send to Machine. Timeout in " + remainingSeconds[0] + " sec");
                    if (remainingSeconds[0] <= 0) {
                        // Timeout reached
                        timeoutOccurred[0] = true;
                        userResponded[0] = true;
                        userResponded.notify();
                    }
                }
            }
        }, 1, 1, java.util.concurrent.TimeUnit.SECONDS);
        
        // Start input reader
        scheduler.submit(() -> {
            try {
                scanner.nextLine(); // Wait for Enter
                synchronized (userResponded) {
                    if (!userResponded[0]) {
                        userResponded[0] = true;
                        userResponded.notify();
                    }
                }
            } catch (Exception e) {
                // Input interrupted
            }
        });
        
        // Wait for either user input or timeout
        synchronized (userResponded) {
            while (!userResponded[0]) {
                try {
                    userResponded.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        // Cancel timers
        if (countdownTimer != null) {
            countdownTimer.cancel(false);
        }
        
        // Clear the countdown line and move to next line
        System.out.println();
        
        return !timeoutOccurred[0];
    }
    
}