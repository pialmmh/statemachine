package com.telcobright.statemachine.pool;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-performance StringBuilder pool for telecom processing
 * Pre-sized for SMS content (up to 700 chars) and phone number formatting
 */
public class StringBuilderPool {
    
    // Different pools for different use cases
    private final ConcurrentLinkedQueue<StringBuilder> smsBuilders = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<StringBuilder> phoneBuilders = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<StringBuilder> logBuilders = new ConcurrentLinkedQueue<>();
    
    // Statistics
    private final AtomicInteger smsBorrowed = new AtomicInteger(0);
    private final AtomicInteger smsReturned = new AtomicInteger(0);
    private final AtomicInteger phoneBorrowed = new AtomicInteger(0);
    private final AtomicInteger phoneReturned = new AtomicInteger(0);
    private final AtomicInteger logBorrowed = new AtomicInteger(0);
    private final AtomicInteger logReturned = new AtomicInteger(0);
    
    // Singleton instance with thread-local optimization
    private static volatile StringBuilderPool instance;
    private static final Object lock = new Object();
    
    // Thread-local pools for high-frequency operations
    private final ThreadLocal<StringBuilder> tlSmsBuilder = ThreadLocal.withInitial(() -> new StringBuilder(700));
    private final ThreadLocal<StringBuilder> tlPhoneBuilder = ThreadLocal.withInitial(() -> new StringBuilder(50));
    private final ThreadLocal<StringBuilder> tlLogBuilder = ThreadLocal.withInitial(() -> new StringBuilder(500));
    
    private StringBuilderPool() {
        prewarmPools();
    }
    
    public static StringBuilderPool getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new StringBuilderPool();
                }
            }
        }
        return instance;
    }
    
    private void prewarmPools() {
        // Pre-warm global pools
        for (int i = 0; i < 100; i++) {
            smsBuilders.offer(new StringBuilder(700));  // Max SMS length
            phoneBuilders.offer(new StringBuilder(50)); // Max phone number length
            logBuilders.offer(new StringBuilder(500));  // Typical log message length
        }
    }
    
    /**
     * Borrow StringBuilder for SMS content processing
     * Thread-local for maximum performance
     */
    public StringBuilder borrowSmsBuilder() {
        StringBuilder sb = tlSmsBuilder.get();
        sb.setLength(0); // Clear previous content
        return sb;
    }
    
    /**
     * Borrow StringBuilder for SMS content processing (global pool)
     * Use when thread-local is not sufficient
     */
    public StringBuilder borrowSmsBuilderGlobal() {
        StringBuilder sb = smsBuilders.poll();
        if (sb == null) {
            sb = new StringBuilder(700);
        }
        sb.setLength(0);
        smsBorrowed.incrementAndGet();
        return sb;
    }
    
    /**
     * Return SMS StringBuilder to global pool
     */
    public void returnSmsBuilder(StringBuilder sb) {
        if (sb != null && sb.capacity() >= 700) {
            sb.setLength(0);
            if (smsBuilders.size() < 200) { // Prevent pool from growing too large
                smsBuilders.offer(sb);
            }
            smsReturned.incrementAndGet();
        }
    }
    
    /**
     * Borrow StringBuilder for phone number formatting
     * Thread-local for maximum performance
     */
    public StringBuilder borrowPhoneBuilder() {
        StringBuilder sb = tlPhoneBuilder.get();
        sb.setLength(0);
        return sb;
    }
    
    /**
     * Borrow StringBuilder for phone number formatting (global pool)
     */
    public StringBuilder borrowPhoneBuilderGlobal() {
        StringBuilder sb = phoneBuilders.poll();
        if (sb == null) {
            sb = new StringBuilder(50);
        }
        sb.setLength(0);
        phoneBorrowed.incrementAndGet();
        return sb;
    }
    
    /**
     * Return phone StringBuilder to global pool
     */
    public void returnPhoneBuilder(StringBuilder sb) {
        if (sb != null && sb.capacity() >= 50) {
            sb.setLength(0);
            if (phoneBuilders.size() < 200) {
                phoneBuilders.offer(sb);
            }
            phoneReturned.incrementAndGet();
        }
    }
    
    /**
     * Borrow StringBuilder for log message formatting
     * Thread-local for maximum performance
     */
    public StringBuilder borrowLogBuilder() {
        StringBuilder sb = tlLogBuilder.get();
        sb.setLength(0);
        return sb;
    }
    
    /**
     * Borrow StringBuilder for log message formatting (global pool)
     */
    public StringBuilder borrowLogBuilderGlobal() {
        StringBuilder sb = logBuilders.poll();
        if (sb == null) {
            sb = new StringBuilder(500);
        }
        sb.setLength(0);
        logBorrowed.incrementAndGet();
        return sb;
    }
    
    /**
     * Return log StringBuilder to global pool
     */
    public void returnLogBuilder(StringBuilder sb) {
        if (sb != null && sb.capacity() >= 500) {
            sb.setLength(0);
            if (logBuilders.size() < 100) {
                logBuilders.offer(sb);
            }
            logReturned.incrementAndGet();
        }
    }
    
    /**
     * Get pool statistics
     */
    public String getStatistics() {
        return String.format(
            "StringBuilderPool: sms_pool=%d (borrowed=%d, returned=%d), phone_pool=%d (borrowed=%d, returned=%d), log_pool=%d (borrowed=%d, returned=%d)",
            smsBuilders.size(), smsBorrowed.get(), smsReturned.get(),
            phoneBuilders.size(), phoneBorrowed.get(), phoneReturned.get(),
            logBuilders.size(), logBorrowed.get(), logReturned.get()
        );
    }
    
    /**
     * Clear all pools - useful for testing
     */
    public void clearPools() {
        smsBuilders.clear();
        phoneBuilders.clear();
        logBuilders.clear();
        
        smsBorrowed.set(0);
        smsReturned.set(0);
        phoneBorrowed.set(0);
        phoneReturned.set(0);
        logBorrowed.set(0);
        logReturned.set(0);
        
        prewarmPools();
    }
}