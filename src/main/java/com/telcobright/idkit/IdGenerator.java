package com.telcobright.idkit;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Stub class for IdGenerator from com.telcobright.idkit
 * This is a placeholder until the actual dependency is available
 */
public class IdGenerator {
    
    /**
     * Generate a unique ID with embedded timestamp
     * @return Long ID with embedded timestamp
     */
    public static long generateId() {
        // Simple implementation using current timestamp
        // In real implementation this would be more sophisticated
        return System.currentTimeMillis() + (int)(Math.random() * 1000);
    }
    
    /**
     * Extract timestamp from the generated ID
     * @param id The generated long ID
     * @return LocalDateTime extracted from the ID
     */
    public static LocalDateTime extractTimestampLocal(long id) {
        // Simple implementation - in real version this would extract embedded timestamp
        // For now, we'll approximate from the timestamp part
        long timestampMillis = id - (id % 1000); // Remove random part
        return LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestampMillis), 
            ZoneId.systemDefault()
        );
    }
}