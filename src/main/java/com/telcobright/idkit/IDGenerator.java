package com.telcobright.idkit;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class IDGenerator {

    private static final DateTimeFormatter MYSQL_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId BD_ZONE = ZoneId.of("Asia/Dhaka");

    /**
     * Generate 64-bit ID from Instant by encoding milliseconds since epoch.
     */
    public static long generate(Instant instant) {
        return instant.toEpochMilli();
    }

    /**
     * Extract timestamp (Instant) from 64-bit ID.
     */
    public static String  extractTimestamp(long id) {
        Instant instant = Instant.ofEpochMilli(id);
        return instant.atZone(BD_ZONE).format(MYSQL_FORMATTER);
    }
    public static LocalDateTime extractTimestampLocal(long id) {
        Instant instant = Instant.ofEpochMilli(id);
        return instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
