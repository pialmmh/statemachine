package com.telcobright.statemachineexamples.smsmachine.states.failed;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachineexamples.smsmachine.SmsContext;

/**
 * OnEntry handler for FAILED state
 */
public class OnEntry {
    public static void handle(GenericStateMachine<SmsContext> machine, StateMachineEvent event) {
        SmsContext context = machine.getContext();
        
        if (context != null) {
            // Determine failure reason based on context
            String failureReason = "Maximum delivery attempts exceeded";
            if (event != null && event.getEventType().contains("SendFailed")) {
                failureReason = "Gateway delivery failure";
            }
            
            context.markFailed(failureReason);
            
            System.out.println("❌ SMS Failed: " + context.getMessageId());
            System.out.println("   📱 " + context.getFromNumber() + " → " + context.getToNumber());
            System.out.println("   💥 Reason: " + context.getFailureReason());
            System.out.println("   🔄 Attempts Made: " + context.getAttemptCount() + "/" + context.getMaxRetries());
            
            // Business logic for failed messages
            if (context.isHighPriority()) {
                System.out.println("🚨 HIGH PRIORITY MESSAGE FAILED - Manual intervention required");
                context.addDeliveryEvent("ALERT: High priority message delivery failed - escalation needed");
            }
            
            if (context.getAttemptCount() >= context.getMaxRetries()) {
                System.out.println("⛔ All delivery attempts exhausted");
                context.addDeliveryEvent("Final delivery attempt failed - message abandoned");
            }
            
            // Log final delivery attempt timeline
            if (context.getDeliveryEvents().size() > 1) {
                System.out.println("📊 Delivery attempt summary available in context");
            }
            
        } else {
            System.out.println("❌ Entering FAILED state");
        }
    }
}