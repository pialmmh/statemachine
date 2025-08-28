package com.telcobright.statemachine.pool;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance string pool for telecom processing
 * Optimizes memory usage for common phone numbers, area codes, and patterns
 */
public class TelecomStringPool {
    
    // Common telecom prefixes and patterns
    private static final Set<String> COMMON_COUNTRY_CODES = Set.of(
        "+1", "+44", "+91", "+86", "+81", "+49", "+33", "+39", "+34", "+7", "+55", "+52", "+61", "+27", "+20"
    );
    
    private static final Set<String> COMMON_AREA_CODES = Set.of(
        "555", "800", "888", "877", "866", "855", "844", "833", "822", "900", "976", "345", "242", "246", "264", "268", "284", "340", "441", "473", "649", "664", "721", "758", "767", "784", "787", "809", "829", "849", "868", "869", "876", "939"
    );
    
    private static final Set<String> COMMON_SIP_DOMAINS = Set.of(
        "sip.example.com", "sip.telco.com", "pbx.company.com", "voip.provider.net", 
        "asterisk.local", "freeswitch.local", "kamailio.local", "opensips.local"
    );
    
    // String intern cache for frequently used strings
    private final ConcurrentHashMap<String, String> internCache = new ConcurrentHashMap<>(10000);
    
    // Statistics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    
    // Singleton instance
    private static volatile TelecomStringPool instance;
    private static final Object lock = new Object();
    
    private TelecomStringPool() {
        // Pre-populate with common patterns
        prePopulateCache();
    }
    
    public static TelecomStringPool getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new TelecomStringPool();
                }
            }
        }
        return instance;
    }
    
    private void prePopulateCache() {
        // Pre-intern common patterns
        COMMON_COUNTRY_CODES.forEach(code -> internCache.put(code, code.intern()));
        COMMON_AREA_CODES.forEach(code -> internCache.put(code, code.intern()));
        COMMON_SIP_DOMAINS.forEach(domain -> internCache.put(domain, domain.intern()));
        
        // Common state names
        String[] states = {"IDLE", "RINGING", "CONNECTED", "DISCONNECTED", "BUSY", "FAILED", "TIMEOUT"};
        for (String state : states) {
            internCache.put(state, state.intern());
        }
        
        // Common event names
        String[] events = {"IncomingCall", "Answer", "Hangup", "Timeout", "Reject", "Forward"};
        for (String event : events) {
            internCache.put(event, event.intern());
        }
    }
    
    /**
     * Intern string if it matches common telecom patterns
     */
    public String intern(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        
        totalRequests.incrementAndGet();
        
        // Check cache first
        String cached = internCache.get(str);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }
        
        // Check if string matches patterns worth interning
        if (shouldIntern(str)) {
            String interned = str.intern();
            internCache.put(str, interned);
            cacheMisses.incrementAndGet();
            return interned;
        }
        
        return str;
    }
    
    /**
     * Determine if string should be interned based on telecom patterns
     */
    private boolean shouldIntern(String str) {
        if (str.length() < 3 || str.length() > 50) {
            return false; // Too short or too long
        }
        
        // Phone number patterns
        if (str.startsWith("+") && str.length() <= 15) {
            return true; // International phone number
        }
        
        // Check if starts with common country code
        for (String code : COMMON_COUNTRY_CODES) {
            if (str.startsWith(code)) {
                return true;
            }
        }
        
        // Check if contains common area code
        for (String areaCode : COMMON_AREA_CODES) {
            if (str.contains(areaCode)) {
                return true;
            }
        }
        
        // SIP URI patterns
        if (str.contains("@") && (str.startsWith("sip:") || str.startsWith("tel:"))) {
            return true;
        }
        
        // State and event names (typically short and repeated)
        if (str.length() <= 20 && str.matches("^[A-Z_]+$")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Optimize phone number format - remove common formatting
     */
    public String optimizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return phoneNumber;
        }
        
        // Remove common formatting characters
        String cleaned = phoneNumber.replaceAll("[\\s\\-\\(\\)\\.\\+]", "");
        
        // If starts with country code, intern the whole number
        if (cleaned.length() >= 10 && cleaned.length() <= 15) {
            return intern(cleaned);
        }
        
        return phoneNumber;
    }
    
    /**
     * Get cache statistics
     */
    public String getStatistics() {
        long total = totalRequests.get();
        long hits = cacheHits.get();
        double hitRatio = total > 0 ? (hits * 100.0) / total : 0.0;
        
        return String.format(
            "TelecomStringPool: cache_size=%d, hits=%d, misses=%d, requests=%d, hit_ratio=%.2f%%",
            internCache.size(), hits, cacheMisses.get(), total, hitRatio
        );
    }
    
    /**
     * Clear cache - useful for testing
     */
    public void clearCache() {
        internCache.clear();
        cacheHits.set(0);
        cacheMisses.set(0);
        totalRequests.set(0);
        prePopulateCache();
    }
    
    /**
     * Get cache size
     */
    public int getCacheSize() {
        return internCache.size();
    }
}