package com.telcobright.statemachineexamples.smsmachine.states.sending;

import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachine.events.StateMachineEvent;
import com.telcobright.statemachineexamples.smsmachine.SmsContext;

/**
 * OnEntry handler for SENDING state
 */
public class OnEntry {
    public static void handle(GenericStateMachine<SmsContext> machine, StateMachineEvent event) {
        SmsContext context = machine.getContext();
        
        if (context != null) {
            context.startSending();
            
            System.out.println("📤 SMS Sending: " + context.getMessageId());
            System.out.println("   📱 " + context.getFromNumber() + " → " + context.getToNumber());
            System.out.println("   📝 Message: " + context.getMessageText());
            System.out.println("   🔄 Attempt: " + context.getAttemptCount() + "/" + context.getMaxRetries());
            
            // Business logic based on context
            if (context.isHighPriority()) {
                System.out.println("🚨 High priority message - using priority gateway");
                context.addDeliveryEvent("Using high priority SMS gateway for delivery");
            }
            
            if (context.isLongMessage()) {
                System.out.println("📄 Long message - will be segmented for delivery");
                context.addDeliveryEvent("Message segmentation required for delivery");
            }
            
            // Special handling for retry attempts
            if (context.getAttemptCount() > 1) {
                System.out.println("♻️ Retry attempt #" + context.getAttemptCount());
                context.addDeliveryEvent("Retry delivery attempt initiated");
            }
            
        } else {
            System.out.println("📤 Entering SENDING state");
        }
    }
}