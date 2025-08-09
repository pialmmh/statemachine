package com.telcobright.statemachine.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Interface for redacting sensitive fields from context before serializing to snapshots.
 * Provides security and privacy protection for sensitive data.
 */
public interface ContextRedactor {
    
    /**
     * Redact sensitive fields from the JSON representation of context
     * 
     * @param original The original JSON node
     * @return A redacted copy with sensitive fields masked
     */
    JsonNode redact(JsonNode original);
    
    /**
     * Default implementation that redacts common sensitive fields
     */
    static ContextRedactor defaultRedactor() {
        return new DefaultContextRedactor();
    }
    
    /**
     * No-op redactor that doesn't redact anything
     */
    static ContextRedactor noRedaction() {
        return original -> original;
    }
    
    /**
     * Create a custom redactor for specific field names
     */
    static ContextRedactor forFields(String... fieldNames) {
        return new DefaultContextRedactor(new HashSet<>(Arrays.asList(fieldNames)));
    }
}

/**
 * Default implementation that redacts common sensitive fields
 */
class DefaultContextRedactor implements ContextRedactor {
    
    private static final String REDACTED_VALUE = "[REDACTED]";
    
    private final Set<String> sensitiveFields;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public DefaultContextRedactor() {
        this.sensitiveFields = new HashSet<>(Arrays.asList(
                "password", "passwd", "pwd",
                "secret", "token", "key", "apiKey", "api_key",
                "msisdn", "phoneNumber", "phone_number", "mobile", "cellphone",
                "email", "emailAddress", "email_address",
                "ssn", "socialSecurityNumber", "social_security_number",
                "creditCard", "credit_card", "cardNumber", "card_number",
                "pin", "pinCode", "pin_code",
                "otp", "oneTimePassword", "one_time_password",
                "address", "homeAddress", "home_address",
                "personalId", "personal_id", "nationalId", "national_id"
        ));
    }
    
    public DefaultContextRedactor(Set<String> sensitiveFields) {
        this.sensitiveFields = new HashSet<>(sensitiveFields);
    }
    
    @Override
    public JsonNode redact(JsonNode original) {
        if (original == null || original.isNull()) {
            return original;
        }
        
        try {
            // Create a deep copy to avoid modifying the original
            JsonNode copy = objectMapper.readTree(original.toString());
            redactNode(copy);
            return copy;
        } catch (Exception e) {
            // If redaction fails, return the original (logging would be appropriate here)
            System.err.println("Warning: Failed to redact sensitive fields: " + e.getMessage());
            return original;
        }
    }
    
    private void redactNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            
            // Check each field and redact if sensitive
            objectNode.fieldNames().forEachRemaining(fieldName -> {
                if (isSensitiveField(fieldName)) {
                    objectNode.set(fieldName, new TextNode(REDACTED_VALUE));
                } else {
                    // Recursively redact nested objects
                    JsonNode childNode = objectNode.get(fieldName);
                    redactNode(childNode);
                }
            });
        } else if (node.isArray()) {
            // Recursively redact array elements
            for (JsonNode arrayElement : node) {
                redactNode(arrayElement);
            }
        }
    }
    
    private boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        
        String lowerFieldName = fieldName.toLowerCase();
        return sensitiveFields.stream().anyMatch(lowerFieldName::contains);
    }
}