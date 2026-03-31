package com.squadron.agent.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgentProgressDtoTest {

    @Test
    void should_buildAgentProgressDto_when_usingBuilder() {
        UUID convId = UUID.randomUUID();

        AgentProgressDto.ProgressItem item = AgentProgressDto.ProgressItem.builder()
                .content("Write unit tests")
                .status("in_progress")
                .priority("high")
                .build();

        AgentProgressDto progress = AgentProgressDto.builder()
                .conversationId(convId)
                .agentType("CODING")
                .phase("TESTING")
                .currentStep("Writing tests")
                .completedSteps(3)
                .totalSteps(10)
                .items(List.of(item))
                .build();

        assertEquals(convId, progress.getConversationId());
        assertEquals("CODING", progress.getAgentType());
        assertEquals("TESTING", progress.getPhase());
        assertEquals("Writing tests", progress.getCurrentStep());
        assertEquals(3, progress.getCompletedSteps());
        assertEquals(10, progress.getTotalSteps());
        assertNotNull(progress.getItems());
        assertEquals(1, progress.getItems().size());
        assertEquals("Write unit tests", progress.getItems().get(0).getContent());
        assertEquals("in_progress", progress.getItems().get(0).getStatus());
        assertEquals("high", progress.getItems().get(0).getPriority());
    }

    @Test
    void should_createAgentProgressDto_when_usingNoArgsConstructor() {
        AgentProgressDto progress = new AgentProgressDto();

        assertNull(progress.getConversationId());
        assertNull(progress.getAgentType());
        assertNull(progress.getPhase());
        assertNull(progress.getCurrentStep());
        assertEquals(0, progress.getCompletedSteps());
        assertEquals(0, progress.getTotalSteps());
        assertNull(progress.getItems());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        AgentProgressDto progress = new AgentProgressDto();
        UUID convId = UUID.randomUUID();

        progress.setConversationId(convId);
        progress.setAgentType("REVIEW");
        progress.setPhase("REVIEWING");
        progress.setCurrentStep("Analyzing code");
        progress.setCompletedSteps(5);
        progress.setTotalSteps(8);

        AgentProgressDto.ProgressItem item = new AgentProgressDto.ProgressItem();
        item.setContent("Check style");
        item.setStatus("completed");
        item.setPriority("low");
        progress.setItems(List.of(item));

        assertEquals(convId, progress.getConversationId());
        assertEquals("REVIEW", progress.getAgentType());
        assertEquals("REVIEWING", progress.getPhase());
        assertEquals("Analyzing code", progress.getCurrentStep());
        assertEquals(5, progress.getCompletedSteps());
        assertEquals(8, progress.getTotalSteps());
        assertEquals(1, progress.getItems().size());
        assertEquals("Check style", progress.getItems().get(0).getContent());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID convId = UUID.randomUUID();

        AgentProgressDto p1 = AgentProgressDto.builder()
                .conversationId(convId)
                .agentType("CODING")
                .phase("PLANNING")
                .currentStep("Step 1")
                .completedSteps(1)
                .totalSteps(5)
                .build();

        AgentProgressDto p2 = AgentProgressDto.builder()
                .conversationId(convId)
                .agentType("CODING")
                .phase("PLANNING")
                .currentStep("Step 1")
                .completedSteps(1)
                .totalSteps(5)
                .build();

        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        AgentProgressDto p1 = AgentProgressDto.builder().phase("PLANNING").build();
        AgentProgressDto p2 = AgentProgressDto.builder().phase("CODING").build();

        assertNotEquals(p1, p2);
    }

    @Test
    void should_haveToString_when_called() {
        AgentProgressDto progress = AgentProgressDto.builder()
                .agentType("CODING")
                .phase("TESTING")
                .build();
        String toString = progress.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("CODING"));
        assertTrue(toString.contains("TESTING"));
    }

    @Test
    void should_buildProgressItem_when_usingAllArgsConstructor() {
        AgentProgressDto.ProgressItem item = new AgentProgressDto.ProgressItem(
                "Implement feature", "pending", "medium");

        assertEquals("Implement feature", item.getContent());
        assertEquals("pending", item.getStatus());
        assertEquals("medium", item.getPriority());
    }

    @Test
    void should_createProgressItem_when_usingNoArgsConstructor() {
        AgentProgressDto.ProgressItem item = new AgentProgressDto.ProgressItem();

        assertNull(item.getContent());
        assertNull(item.getStatus());
        assertNull(item.getPriority());
    }
}
