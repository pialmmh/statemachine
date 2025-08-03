package com.telcobright.statemachineexamples.smsmachine.states.delivered;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachineexamples.smsmachine.SmsContext;

/**
 * OnEntry handler for DELIVERED state
 */
public class OnEntry {
    public static void handle(GenericStateMachine<SmsContext> machine, StateMachineEvent event) {
        SmsContext context = machine.getContext();
        
        if (context != null) {
            context.markDelivered();
            
            System.out.println("✅ SMS Delivered: " + context.getMessageId());
            System.out.println("   📱 " + context.getFromNumber() + " → " + context.getToNumber());
            System.out.println("   ⏱️ Delivery Time: " + context.getDeliveryTime().toSeconds() + "s");
            System.out.println("   🔄 Total Attempts: " + context.getAttemptCount());
            
            // Business metrics and logging
            if (context.isHighPriority()) {
                System.out.println("🚨 High priority message delivered successfully");
                context.addDeliveryEvent("HIGH priority message delivery confirmed");
            }
            
            if (context.getDeliveryTime().toSeconds() < 5) {
                System.out.println("⚡ Fast delivery achieved (< 5 seconds)");
                context.addDeliveryEvent("Fast delivery SLA met");
            }
            
            if (context.getAttemptCount() > 1) {
                System.out.println("💪 Delivered after " + context.getAttemptCount() + " attempts");
                context.addDeliveryEvent("Message delivered successfully after retry");
            }
            
        } else {
            System.out.println("✅ Entering DELIVERED state");
        }
    }
}