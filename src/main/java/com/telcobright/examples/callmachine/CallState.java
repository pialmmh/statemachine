package com.telcobright.examples.callmachine;

/**
 * Call State Machine States
 * 
 * Production-ready call states following ESL (Event Socket Library) specification
 */
public enum CallState {
    /**
     * Initial state - performs business logic validation
     * (authentication, balance check, rate limiting, etc.)
     */
    ADMISSION,
    
    /**
     * Call is being attempted to destination
     */
    TRYING,
    
    /**
     * Call is ringing at destination
     */
    RINGING,
    
    /**
     * Call is connected and active
     */
    CONNECTED,
    
    /**
     * Final state - call has ended
     */
    HUNGUP
}