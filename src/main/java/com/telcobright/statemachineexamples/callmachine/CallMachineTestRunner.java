package com.telcobright.statemachineexamples.callmachine;

import com.telcobright.statemachineexamples.callmachine.events.Answer;
import com.telcobright.statemachineexamples.callmachine.events.Hangup;
import com.telcobright.statemachineexamples.callmachine.events.IncomingCall;
import com.telcobright.statemachineexamples.callmachine.events.SessionProgress;
import com.telcobright.statemachine.GenericStateMachine;

/**
 * Test runner for CallMachine - demonstrates call flow scenarios
 */
public class CallMachineTestRunner {
    
    public static void main(String[] args) {
        try {
            // Create a call context
            CallContext callContext = new CallContext("CALL-001", "+1234567890", "+0987654321");
            
            // Create the call machine
            CallMachine machine = CallMachine.create("call-machine-001");
            
            // Set the call context
            machine.setContext(callContext);
            
            // Run the test scenario
            runCallFlowTest(machine, callContext);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Run a complete call flow test scenario
     */
    private static void runCallFlowTest(GenericStateMachine machine, CallContext callContext) {
        System.out.println("=== Call Machine Demo ===");
        System.out.println("Initial state: " + machine.getCurrentState());
        
        // Incoming call - start ringing
        machine.fire(new IncomingCall());
        System.out.println("After INCOMING_CALL: " + machine.getCurrentState());
        
        // Session progress during ringing (in-band event)
        machine.fire(new SessionProgress("183", 183));
        System.out.println("After SESSION_PROGRESS (stays RINGING): " + machine.getCurrentState());
        
        // Answer call
        machine.fire(new Answer());
        System.out.println("After ANSWER: " + machine.getCurrentState());
        
        // End call
        callContext.endCall();
        machine.fire(new Hangup());
        System.out.println("After HANGUP: " + machine.getCurrentState());
        
        System.out.println("=== Call Flow Test Completed ===");
    }
}