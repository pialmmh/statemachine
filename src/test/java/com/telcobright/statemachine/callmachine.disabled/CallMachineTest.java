package com.telcobright.statemachine.callmachine;

/**
 * Production Call Machine Test
 * 
 * Demonstrates the production-ready call machine implementation
 */
public class CallMachineTest {
    
    public static void main(String[] args) {
        System.out.println("📞 PRODUCTION CALL MACHINE TEST");
        System.out.println("================================");
        System.out.println();
        
        // Create call machine system
        CallMachine callMachine = new CallMachine("production-call-system", 1000, 5000);
        
        System.out.println("✅ Call machine system initialized");
        System.out.println("   • Target: 1000 calls/second");
        System.out.println("   • Capacity: 5000 concurrent calls");
        System.out.println();
        
        // Test call flow
        String callId = "call-" + System.currentTimeMillis();
        String fromNumber = "+1-555-1234";
        String toNumber = "+1-555-5678";
        
        System.out.println("📞 Processing call: " + callId);
        System.out.println("   From: " + fromNumber);
        System.out.println("   To: " + toNumber);
        System.out.println();
        
        // Step 1: Incoming call (auto-creates machine)
        System.out.println("1. Incoming call...");
        boolean result = callMachine.processIncomingCall(callId, fromNumber, toNumber);
        System.out.println("   Result: " + (result ? "✅ Accepted" : "❌ Rejected"));
        System.out.println("   State: " + callMachine.getCallState(callId));
        
        // Step 2: Admission success
        System.out.println("\n2. Admission check...");
        result = callMachine.acceptCall(callId);
        System.out.println("   Result: " + (result ? "✅ Admitted" : "❌ Failed"));
        System.out.println("   State: " + callMachine.getCallState(callId));
        
        // Step 3: Ring
        System.out.println("\n3. Ringing...");
        result = callMachine.ringCall(callId);
        System.out.println("   Result: " + (result ? "✅ Ringing" : "❌ Failed"));
        System.out.println("   State: " + callMachine.getCallState(callId));
        
        // Step 4: Answer
        System.out.println("\n4. Answering...");
        result = callMachine.answerCall(callId);
        System.out.println("   Result: " + (result ? "✅ Connected" : "❌ Failed"));
        System.out.println("   State: " + callMachine.getCallState(callId));
        
        // Simulate call duration
        try {
            System.out.println("\n📞 Call in progress...");
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // Ignore
        }
        
        // Step 5: Hangup
        System.out.println("\n5. Hanging up...");
        result = callMachine.hangupCall(callId);
        System.out.println("   Result: " + (result ? "✅ Ended" : "❌ Failed"));
        System.out.println("   State: " + callMachine.getCallState(callId));
        
        // Show statistics
        System.out.println("\n📊 STATISTICS");
        System.out.println("━━━━━━━━━━━━━━");
        System.out.println("Active calls: " + callMachine.getActiveCallCount());
        System.out.println("Performance: " + callMachine.getPerformanceStats());
        
        // Test auto-creation with non-existent call
        System.out.println("\n🔬 Testing auto-creation feature...");
        String newCallId = "auto-call-" + System.currentTimeMillis();
        System.out.println("Sending INCOMING_CALL to non-existent machine: " + newCallId);
        result = callMachine.processIncomingCall(newCallId, "+1-555-9999", "+1-555-0000");
        System.out.println("   Result: " + (result ? "✅ Auto-created and accepted" : "❌ Failed"));
        System.out.println("   State: " + callMachine.getCallState(newCallId));
        
        // Cleanup
        System.out.println("\n🔄 Shutting down...");
        callMachine.shutdown();
        
        System.out.println("\n✅ TEST COMPLETED SUCCESSFULLY!");
    }
}