package com.squadron.orchestrator.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
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

class DelegateTaskRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void should_createWithBuilder_when_allFieldsProvided() {
        DelegateTaskRequest request = DelegateTaskRequest.builder()
                .agentType("CODER")
                .agentName("Sol")
                .instructions("Implement the login feature")
                .targetState("PROPOSE_CODE")
                .build();

        assertEquals("CODER", request.getAgentType());
        assertEquals("Sol", request.getAgentName());
        assertEquals("Implement the login feature", request.getInstructions());
        assertEquals("PROPOSE_CODE", request.getTargetState());
    }

    @Test
    void should_createWithNoArgsConstructor_when_defaultsExpected() {
        DelegateTaskRequest request = new DelegateTaskRequest();

        assertNull(request.getAgentType());
        assertNull(request.getAgentName());
        assertNull(request.getInstructions());
        assertNull(request.getTargetState());
    }

    @Test
    void should_createWithAllArgsConstructor_when_allParametersPassed() {
        DelegateTaskRequest request = new DelegateTaskRequest(
                "PLANNER", "Titan", "Plan the sprint", "PLANNING"
        );

        assertEquals("PLANNER", request.getAgentType());
        assertEquals("Titan", request.getAgentName());
        assertEquals("Plan the sprint", request.getInstructions());
        assertEquals("PLANNING", request.getTargetState());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        DelegateTaskRequest request = new DelegateTaskRequest();

        request.setAgentType("REVIEWER");
        request.setAgentName("Aegis");
        request.setInstructions("Review PR #42");
        request.setTargetState("REVIEW");

        assertEquals("REVIEWER", request.getAgentType());
        assertEquals("Aegis", request.getAgentName());
        assertEquals("Review PR #42", request.getInstructions());
        assertEquals("REVIEW", request.getTargetState());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        DelegateTaskRequest r1 = DelegateTaskRequest.builder()
                .agentType("CODER").agentName("Sol").instructions("Fix it").targetState("PROPOSE_CODE")
                .build();
        DelegateTaskRequest r2 = DelegateTaskRequest.builder()
                .agentType("CODER").agentName("Sol").instructions("Fix it").targetState("PROPOSE_CODE")
                .build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        DelegateTaskRequest r1 = DelegateTaskRequest.builder().agentType("CODER").build();
        DelegateTaskRequest r2 = DelegateTaskRequest.builder().agentType("PLANNER").build();

        assertNotEquals(r1, r2);
    }

    @Test
    void should_haveToString_when_called() {
        DelegateTaskRequest request = DelegateTaskRequest.builder()
                .agentType("QA")
                .agentName("Sentinel")
                .build();

        assertNotNull(request.toString());
        assertTrue(request.toString().contains("QA"));
        assertTrue(request.toString().contains("Sentinel"));
    }

    @Test
    void should_roundTripJson_when_serializeAndDeserialize() throws Exception {
        DelegateTaskRequest original = DelegateTaskRequest.builder()
                .agentType("CODER")
                .agentName("Sol")
                .instructions("Implement auth flow")
                .targetState("PROPOSE_CODE")
                .build();

        String json = mapper.writeValueAsString(original);
        DelegateTaskRequest deserialized = mapper.readValue(json, DelegateTaskRequest.class);

        assertEquals(original, deserialized);
    }

    @Test
    void should_serializeToJson_when_usingJackson() throws Exception {
        DelegateTaskRequest request = DelegateTaskRequest.builder()
                .agentType("PLANNER")
                .agentName("Titan")
                .instructions("Break down the epic")
                .targetState("PLANNING")
                .build();

        String json = mapper.writeValueAsString(request);

        assertNotNull(json);
        assertTrue(json.contains("\"agentType\":\"PLANNER\""));
        assertTrue(json.contains("\"agentName\":\"Titan\""));
        assertTrue(json.contains("\"instructions\":\"Break down the epic\""));
        assertTrue(json.contains("\"targetState\":\"PLANNING\""));
    }

    @Test
    void should_deserializeFromJson_when_validJsonProvided() throws Exception {
        String json = """
                {
                    "agentType": "REVIEWER",
                    "agentName": "Aegis",
                    "instructions": "Check for security issues",
                    "targetState": "REVIEW"
                }
                """;

        DelegateTaskRequest request = mapper.readValue(json, DelegateTaskRequest.class);

        assertEquals("REVIEWER", request.getAgentType());
        assertEquals("Aegis", request.getAgentName());
        assertEquals("Check for security issues", request.getInstructions());
        assertEquals("REVIEW", request.getTargetState());
    }

    @Test
    void should_passValidation_when_agentTypeProvided() {
        DelegateTaskRequest request = DelegateTaskRequest.builder()
                .agentType("CODER")
                .build();

        Set<ConstraintViolation<DelegateTaskRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void should_failValidation_when_agentTypeNull() {
        DelegateTaskRequest request = DelegateTaskRequest.builder()
                .agentName("Sol")
                .instructions("Do something")
                .build();

        Set<ConstraintViolation<DelegateTaskRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        ConstraintViolation<DelegateTaskRequest> violation = violations.iterator().next();
        assertEquals("agentType", violation.getPropertyPath().toString());
        assertEquals("Agent type is required", violation.getMessage());
    }

    @Test
    void should_failValidation_when_agentTypeBlank() {
        DelegateTaskRequest request = DelegateTaskRequest.builder()
                .agentType("   ")
                .build();

        Set<ConstraintViolation<DelegateTaskRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        ConstraintViolation<DelegateTaskRequest> violation = violations.iterator().next();
        assertEquals("agentType", violation.getPropertyPath().toString());
    }

    @Test
    void should_failValidation_when_agentTypeEmpty() {
        DelegateTaskRequest request = DelegateTaskRequest.builder()
                .agentType("")
                .build();

        Set<ConstraintViolation<DelegateTaskRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertEquals("agentType", violations.iterator().next().getPropertyPath().toString());
    }
}
