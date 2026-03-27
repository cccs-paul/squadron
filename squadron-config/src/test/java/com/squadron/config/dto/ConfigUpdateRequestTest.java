package com.squadron.config.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigUpdateRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void should_buildRequest_when_usingBuilder() {
        ConfigUpdateRequest request = ConfigUpdateRequest.builder()
                .configKey("max.retries")
                .configValue("5")
                .description("Max retry count")
                .build();

        assertEquals("max.retries", request.getConfigKey());
        assertEquals("5", request.getConfigValue());
        assertEquals("Max retry count", request.getDescription());
    }

    @Test
    void should_useNoArgsConstructor() {
        ConfigUpdateRequest request = new ConfigUpdateRequest();
        assertNull(request.getConfigKey());
        assertNull(request.getConfigValue());
        assertNull(request.getDescription());
    }

    @Test
    void should_useAllArgsConstructor() {
        ConfigUpdateRequest request = new ConfigUpdateRequest("key", "value", "desc");

        assertEquals("key", request.getConfigKey());
        assertEquals("value", request.getConfigValue());
        assertEquals("desc", request.getDescription());
    }

    @Test
    void should_supportSetters() {
        ConfigUpdateRequest request = new ConfigUpdateRequest();
        request.setConfigKey("key");
        request.setConfigValue("value");
        request.setDescription("desc");

        assertEquals("key", request.getConfigKey());
        assertEquals("value", request.getConfigValue());
        assertEquals("desc", request.getDescription());
    }

    @Test
    void should_passValidation_when_validRequest() {
        ConfigUpdateRequest request = ConfigUpdateRequest.builder()
                .configKey("key")
                .configValue("value")
                .build();

        Set<ConstraintViolation<ConfigUpdateRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    void should_failValidation_when_configKeyBlank() {
        ConfigUpdateRequest request = ConfigUpdateRequest.builder()
                .configKey("")
                .configValue("value")
                .build();

        Set<ConstraintViolation<ConfigUpdateRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("Config key must not be blank")));
    }

    @Test
    void should_failValidation_when_configKeyNull() {
        ConfigUpdateRequest request = ConfigUpdateRequest.builder()
                .configKey(null)
                .configValue("value")
                .build();

        Set<ConstraintViolation<ConfigUpdateRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void should_failValidation_when_configValueBlank() {
        ConfigUpdateRequest request = ConfigUpdateRequest.builder()
                .configKey("key")
                .configValue("")
                .build();

        Set<ConstraintViolation<ConfigUpdateRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("Config value must not be blank")));
    }

    @Test
    void should_failValidation_when_configValueNull() {
        ConfigUpdateRequest request = ConfigUpdateRequest.builder()
                .configKey("key")
                .configValue(null)
                .build();

        Set<ConstraintViolation<ConfigUpdateRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void should_passValidation_when_descriptionNull() {
        ConfigUpdateRequest request = ConfigUpdateRequest.builder()
                .configKey("key")
                .configValue("value")
                .description(null)
                .build();

        Set<ConstraintViolation<ConfigUpdateRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        ConfigUpdateRequest r1 = ConfigUpdateRequest.builder()
                .configKey("key").configValue("val").build();
        ConfigUpdateRequest r2 = ConfigUpdateRequest.builder()
                .configKey("key").configValue("val").build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFields() {
        ConfigUpdateRequest r1 = ConfigUpdateRequest.builder()
                .configKey("key1").configValue("val").build();
        ConfigUpdateRequest r2 = ConfigUpdateRequest.builder()
                .configKey("key2").configValue("val").build();

        assertNotEquals(r1, r2);
    }

    @Test
    void should_supportToString() {
        ConfigUpdateRequest request = ConfigUpdateRequest.builder()
                .configKey("my.key").configValue("my.val").build();
        assertNotNull(request.toString());
        assertTrue(request.toString().contains("my.key"));
    }
}
