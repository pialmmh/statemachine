package com.telcobright.statemachine.humantests;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.telcobright.statemachine.test.TestDatabaseManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class HumanTestBase {
    protected TestDatabaseManager db;
    protected ObjectMapper mapper;
    protected String testRunId;

    @BeforeAll
    public void setupDb() throws Exception {
        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        db = new TestDatabaseManager();
        testRunId = "HUMAN_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
        // Clean any leftovers for this run id (no-op on first use)
        try {
            db.cleanTestData(testRunId);
        } catch (Exception ignore) {
            // Procedures may not exist yet before schema init within TestDatabaseManager
        }
    }

    @AfterAll
    public void teardownDb() throws Exception {
        if (db != null) {
            db.printTestSummary(testRunId);
            db.close();
        }
    }

    protected String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"json-serialization-failed\"}";
        }
    }
}
