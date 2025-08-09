package com.telcobright.statemachineexamples.smsmachine.states.queued;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachineexamples.smsmachine.SmsContext;

/**
 * OnEntry handler for QUEUED state
 */
public class OnEntry {
    public static void handle(GenericStateMachine<?, ?> machine, StateMachineEvent event) {
        SmsContext context = (SmsContext) machine.getContext();
        
        if (context != null) {
            context.setMessageStatus("QUEUED");
            context.addDeliveryEvent("Message entered QUEUED state");
            
            System.out.println("📥 SMS Queued: " + context.getMessageId());
            System.out.println("   📱 " + context.getFromNumber() + " → " + context.getToNumber());
            System.out.println("   📝 Message: " + context.getMessageText());
            System.out.println("   ⚡ Priority: " + context.getPriority());
            
            // Business logic based on context
            if (context.isHighPriority()) {
                System.out.println("🚨 High priority message - expedited processing");
                context.addDeliveryEvent("High priority message flagged for expedited processing");
            }
            
            if (context.isLongMessage()) {
                System.out.println("📄 Long message detected (" + context.getMessageText().length() + " chars)");
                context.addDeliveryEvent("Long message - may require segmentation");
            }
            
        } else {
            System.out.println("📥 Entering QUEUED state");
        }
    }
}