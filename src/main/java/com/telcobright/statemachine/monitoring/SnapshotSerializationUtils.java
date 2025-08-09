package com.telcobright.statemachine.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telcobright.statemachine.events.StateMachineEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Utility class for serializing and hashing context objects for snapshots.
 * Handles JSON serialization, Base64 encoding, and SHA-256 hashing.
 */
public class SnapshotSerializationUtils {
    
    private static final ObjectMapper DEFAULT_OBJECT_MAPPER = new ObjectMapper();
    
    private final ObjectMapper objectMapper;
    private final ContextRedactor contextRedactor;
    
    public SnapshotSerializationUtils() {
        this(DEFAULT_OBJECT_MAPPER, ContextRedactor.defaultRedactor());
    }
    
    public SnapshotSerializationUtils(ObjectMapper objectMapper, ContextRedactor contextRedactor) {
        this.objectMapper = objectMapper;
        this.contextRedactor = contextRedactor;
    }
    
    /**
     * Serialize context to JSON string, optionally redacting sensitive fields
     * 
     * @param context The context object to serialize
     * @param redact Whether to redact sensitive fields
     * @return JSON string representation
     */
    public String serializeToJson(Object context, boolean redact) {
        if (context == null) {
            return null;
        }
        
        try {
            JsonNode jsonNode = objectMapper.valueToTree(context);
            
            if (redact) {
                jsonNode = contextRedactor.redact(jsonNode);
            }
            
            return objectMapper.writeValueAsString(jsonNode);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize context to JSON: " + e.getMessage(), e);
        }
    }
    
    /**
     * Serialize context to Base64-encoded JSON string
     * 
     * @param context The context object to serialize
     * @param redact Whether to redact sensitive fields
     * @return Base64-encoded JSON string
     */
    public String serializeToBase64Json(Object context, boolean redact) {
        String json = serializeToJson(context, redact);
        if (json == null) {
            return null;
        }
        
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Serialize event to JSON string with full payload
     * 
     * @param event The event to serialize
     * @return JSON string representation of the event with full payload
     */
    public String serializeEventToJson(StateMachineEvent event) {
        if (event == null) {
            return null;
        }
        
        try {
            // Serialize the complete event object to capture all fields and payload
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event to JSON: " + e.getMessage(), e);
        }
    }
    
    /**
     * Serialize event parameters to JSON string
     * 
     * @param event The event to extract parameters from
     * @return JSON string of event parameters
     */
    public String serializeEventParametersToJson(StateMachineEvent event) {
        if (event == null) {
            return null;
        }
        
        try {
            // Create a detailed representation with reflection-based field extraction
            EventParameters eventParams = extractEventParameters(event);
            return objectMapper.writeValueAsString(eventParams);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event parameters: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extract event parameters using reflection
     */
    private EventParameters extractEventParameters(StateMachineEvent event) {
        EventParameters params = new EventParameters(
            event.getEventType(),
            event.getClass().getSimpleName(),
            System.currentTimeMillis()
        );
        
        // Use reflection to extract all fields from the event
        try {
            java.lang.reflect.Field[] fields = event.getClass().getDeclaredFields();
            java.util.Map<String, Object> fieldMap = new java.util.HashMap<>();
            
            for (java.lang.reflect.Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(event);
                fieldMap.put(field.getName(), value);
            }
            
            params.setParameters(fieldMap);
        } catch (Exception e) {
            // If reflection fails, just use basic info
            System.err.println("Warning: Could not extract event parameters via reflection: " + e.getMessage());
        }
        
        return params;
    }
    
    /**
     * Compute SHA-256 hash of the JSON representation of context
     * 
     * @param context The context object to hash
     * @param redact Whether to redact sensitive fields before hashing
     * @return SHA-256 hash as hex string
     */
    public String computeContextHash(Object context, boolean redact) {
        if (context == null) {
            return null;
        }
        
        try {
            String json = serializeToJson(context, redact);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute context hash: " + e.getMessage(), e);
        }
    }
    
    /**
     * Decode Base64-encoded JSON string back to JSON
     * 
     * @param base64Json Base64-encoded JSON string
     * @return Decoded JSON string
     */
    public String decodeBase64Json(String base64Json) {
        if (base64Json == null) {
            return null;
        }
        
        byte[] decodedBytes = Base64.getDecoder().decode(base64Json);
        return new String(decodedBytes, StandardCharsets.UTF_8);
    }
    
    /**
     * Serialize context to JSON with redaction fields
     */
    public String serializeContextToJson(Object context, java.util.Set<String> redactionFields) {
        return serializeToJson(context, !redactionFields.isEmpty());
    }
    
    /**
     * Encode string to Base64
     */
    public String encodeToBase64(String str) {
        if (str == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(str.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Generate SHA-256 hash of a string
     */
    public String generateHash(String str) {
        if (str == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(str.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate hash: " + e.getMessage(), e);
        }
    }
    
    /**
     * Simple event snapshot representation for JSON serialization
     */
    private static class EventSnapshot {
        public final String eventType;
        public final String eventClass;
        public final long timestamp;
        
        public EventSnapshot(String eventType, String eventClass, long timestamp) {
            this.eventType = eventType;
            this.eventClass = eventClass;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * Event parameters representation for JSON serialization
     */
    private static class EventParameters {
        public final String eventType;
        public final String eventClass;
        public final long timestamp;
        private java.util.Map<String, Object> parameters;
        
        public EventParameters(String eventType, String eventClass, long timestamp) {
            this.eventType = eventType;
            this.eventClass = eventClass;
            this.timestamp = timestamp;
        }
        
        public java.util.Map<String, Object> getParameters() {
            return parameters;
        }
        
        public void setParameters(java.util.Map<String, Object> parameters) {
            this.parameters = parameters;
        }
    }
}