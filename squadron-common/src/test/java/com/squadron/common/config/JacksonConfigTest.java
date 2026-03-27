package com.squadron.common.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class JacksonConfigTest {

    private JacksonConfig jacksonConfig;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jacksonConfig = new JacksonConfig();
        objectMapper = jacksonConfig.objectMapper();
    }

    @Test
    void should_createObjectMapper_when_beanCalled() {
        assertNotNull(objectMapper);
    }

    @Test
    void should_disableWriteDatesAsTimestamps_when_configured() {
        assertFalse(objectMapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
    }

    @Test
    void should_disableFailOnUnknownProperties_when_configured() {
        assertFalse(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    @Test
    void should_serializeInstantAsString_when_javaTimeModuleRegistered() throws JsonProcessingException {
        Instant now = Instant.parse("2026-01-15T10:30:00Z");
        String json = objectMapper.writeValueAsString(now);

        assertNotNull(json);
        assertTrue(json.contains("2026-01-15"));
        assertFalse(json.matches("^\\d+$")); // Should not be a numeric timestamp
    }

    @Test
    void should_deserializeInstantFromString_when_javaTimeModuleRegistered() throws JsonProcessingException {
        String json = "\"2026-01-15T10:30:00Z\"";
        Instant instant = objectMapper.readValue(json, Instant.class);

        assertNotNull(instant);
        assertEquals(Instant.parse("2026-01-15T10:30:00Z"), instant);
    }

    @Test
    void should_ignoreUnknownProperties_when_deserializing() throws JsonProcessingException {
        String json = "{\"known\":\"value\",\"unknown\":\"ignored\"}";

        // Should not throw even though SimpleBean doesn't have "unknown" field
        SimpleBean bean = objectMapper.readValue(json, SimpleBean.class);
        assertNotNull(bean);
        assertEquals("value", bean.getKnown());
    }

    // Simple test bean for deserialization testing
    static class SimpleBean {
        private String known;

        public String getKnown() { return known; }
        public void setKnown(String known) { this.known = known; }
    }
}
