package com.telcobright.statemachine.debugger.stresstest;

/**
 * Simplified events for testing
 */
public class SimpleEvents {
    
    public static class InitCall {
        public final String caller;
        public final String callee;
        
        public InitCall(String caller, String callee) {
            this.caller = caller;
            this.callee = callee;
        }
    }
    
    public static class Ring {
        // Ring event - no additional data needed
    }
    
    public static class Answer {
        // Answer event - no additional data needed  
    }
    
    public static class Hold {
        // Put call on hold
    }
    
    public static class Resume {
        // Resume from hold
    }
    
    public static class Disconnect {
        // End the call
    }
    
    public static class GoOffline {
        // Machine goes offline
    }
    
    public static class ComeOnline {
        // Machine comes back online
    }
}