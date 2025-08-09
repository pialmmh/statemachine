package com.telcobright.statemachine.test;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.StateMachineRegistry;
import com.telcobright.statemachine.monitoring.DatabaseSnapshotRecorder;
import com.telcobright.statemachineexamples.callmachine.CallMachine;
import com.telcobright.statemachineexamples.callmachine.entity.CallEntity;
import com.telcobright.statemachineexamples.callmachine.CallContext;
import com.telcobright.statemachineexamples.callmachine.events.IncomingCall;
import com.telcobright.statemachineexamples.callmachine.events.Answer;
import com.telcobright.statemachineexamples.callmachine.events.Hangup;
import com.telcobright.statemachineexamples.callmachine.events.SessionProgress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Monitored CallMachine Demo using StateMachineRegistry
 * 
 * Demonstrates:
 * - Registry-mediated machine creation and event handling  
 * - Real CallMachine with IncomingCall, Answer, Hangup, SessionProgress events
 * - Complete monitoring through database snapshots
 */
public class MonitoredCallMachineDemo {
    
    public static void main(String[] args) throws Exception {
        System.out.println("📞 Starting Monitored CallMachine Demo");
        System.out.println("🏭 Using StateMachineRegistry and CallMachine");
        System.out.println("📊 Full monitoring enabled");
        System.out.println();
        
        // Create call scenario
        createMonitoredCallScenario();
        
        System.out.println();
        System.out.println("✅ Monitored call scenario completed!");
        System.out.println("📊 View monitoring at: http://localhost:8091");
    }
    
    private static void createMonitoredCallScenario() throws Exception {
        String runId = generateRunId("monitored-call");
        String callId = "call-" + System.currentTimeMillis();
        
        System.out.println("📞 Creating monitored call scenario");
        System.out.println("   Call ID: " + callId);
        System.out.println("   Run ID: " + runId);
        System.out.println();
        
        // 1. Create registry and machine
        System.out.println("1️⃣ Creating registry and call machine...");
        StateMachineRegistry registry = new StateMachineRegistry();
        GenericStateMachine<CallEntity, CallContext> machine = CallMachine.create(callId);
        registry.register(callId, machine);
        
        // Set initial context
        CallContext context = new CallContext();
        context.setCallId(callId);
        context.setFromNumber("+1-555-1234");
        context.setToNumber("+1-555-5678");
        machine.setContext(context);
        
        Thread.sleep(100);
        
        // 2. Send IncomingCall event
        System.out.println("2️⃣ Sending IncomingCall event...");
        IncomingCall incomingCall = new IncomingCall("+1-555-1234");
        machine.sendEvent(incomingCall);
        Thread.sleep(100);
        
        // 3. Send SessionProgress while ringing
        System.out.println("3️⃣ Sending SessionProgress event...");
        SessionProgress sessionProgress = new SessionProgress("EARLY_MEDIA", 100);
        machine.sendEvent(sessionProgress);
        Thread.sleep(100);
        
        // 4. Send Answer event
        System.out.println("4️⃣ Sending Answer event...");
        Answer answer = new Answer();
        machine.sendEvent(answer);
        Thread.sleep(100);
        
        // 5. Send Hangup event to complete call
        System.out.println("5️⃣ Sending Hangup event...");
        Hangup hangup = new Hangup();
        machine.sendEvent(hangup);
        
        System.out.println("📞 Call completed successfully!");
        System.out.println("   Current state: " + machine.getCurrentState());
        
        // Clean up registry
        registry.shutdown();
    }
    
    
    private static String generateRunId(String prefix) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        String timestamp = LocalDateTime.now().format(formatter);
        String randomSuffix = String.valueOf(System.nanoTime() % 100000);
        return prefix + "-" + timestamp + "-" + randomSuffix;
    }
}