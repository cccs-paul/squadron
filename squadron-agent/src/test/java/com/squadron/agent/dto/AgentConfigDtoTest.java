package com.squadron.agent.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentConfigDtoTest {

    @Test
    void should_buildAgentConfigDto_when_usingBuilder() {
        AgentConfigDto config = AgentConfigDto.builder()
                .provider("openai-compatible")
                .model("gpt-4")
                .maxTokens(4096)
                .temperature(0.7)
                .systemPromptOverride("Custom prompt")
                .build();

        assertEquals("openai-compatible", config.getProvider());
        assertEquals("gpt-4", config.getModel());
        assertEquals(4096, config.getMaxTokens());
        assertEquals(0.7, config.getTemperature());
        assertEquals("Custom prompt", config.getSystemPromptOverride());
    }

    @Test
    void should_createAgentConfigDto_when_usingNoArgsConstructor() {
        AgentConfigDto config = new AgentConfigDto();
        assertNull(config.getProvider());
        assertNull(config.getModel());
        assertNull(config.getMaxTokens());
        assertNull(config.getTemperature());
        assertNull(config.getSystemPromptOverride());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        AgentConfigDto config = new AgentConfigDto();
        config.setProvider("openai-compatible");
        config.setModel("gpt-4");
        config.setMaxTokens(2048);
        config.setTemperature(0.5);
        config.setSystemPromptOverride("Override prompt");

        assertEquals("openai-compatible", config.getProvider());
        assertEquals("gpt-4", config.getModel());
        assertEquals(2048, config.getMaxTokens());
        assertEquals(0.5, config.getTemperature());
        assertEquals("Override prompt", config.getSystemPromptOverride());
    }

    @Test
    void should_buildEmptyConfig_when_noFieldsSet() {
        AgentConfigDto config = AgentConfigDto.builder().build();
        assertNull(config.getProvider());
        assertNull(config.getModel());
        assertNull(config.getMaxTokens());
        assertNull(config.getTemperature());
        assertNull(config.getSystemPromptOverride());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        AgentConfigDto c1 = AgentConfigDto.builder().provider("openai").model("gpt-4").build();
        AgentConfigDto c2 = AgentConfigDto.builder().provider("openai").model("gpt-4").build();

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        AgentConfigDto c1 = AgentConfigDto.builder().provider("openai").build();
        AgentConfigDto c2 = AgentConfigDto.builder().provider("anthropic").build();

        assertNotEquals(c1, c2);
    }

    @Test
    void should_createAgentConfigDto_when_usingAllArgsConstructor() {
        AgentConfigDto config = new AgentConfigDto("openai", "gpt-4", 4096, 0.7, "prompt");

        assertEquals("openai", config.getProvider());
        assertEquals("gpt-4", config.getModel());
        assertEquals(4096, config.getMaxTokens());
        assertEquals(0.7, config.getTemperature());
        assertEquals("prompt", config.getSystemPromptOverride());
    }
}
