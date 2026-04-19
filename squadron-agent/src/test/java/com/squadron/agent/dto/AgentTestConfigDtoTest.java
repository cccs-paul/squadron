package com.squadron.agent.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentTestConfigDtoTest {

    @Test
    void should_buildWithAllFields() {
        AgentTestConfigDto dto = AgentTestConfigDto.builder()
                .generatorProvider("openai")
                .generatorModel("gpt-4o")
                .generatorHostingType("PLATFORM")
                .generatorBaseUrl("https://api.openai.com")
                .generatorApiKey("sk-key")
                .build();

        assertEquals("openai", dto.getGeneratorProvider());
        assertEquals("gpt-4o", dto.getGeneratorModel());
        assertEquals("PLATFORM", dto.getGeneratorHostingType());
        assertEquals("https://api.openai.com", dto.getGeneratorBaseUrl());
        assertEquals("sk-key", dto.getGeneratorApiKey());
    }

    @Test
    void should_haveDefaultValues() {
        AgentTestConfigDto dto = AgentTestConfigDto.builder().build();

        assertEquals("ollama", dto.getGeneratorProvider());
        assertEquals("gemma4:e2b", dto.getGeneratorModel());
        assertEquals("SELF_HOSTED", dto.getGeneratorHostingType());
        assertNull(dto.getGeneratorBaseUrl());
        assertNull(dto.getGeneratorApiKey());
    }

    @Test
    void should_supportNoArgsConstructor() {
        AgentTestConfigDto dto = new AgentTestConfigDto();

        assertEquals("ollama", dto.getGeneratorProvider());
        assertEquals("gemma4:e2b", dto.getGeneratorModel());
        assertEquals("SELF_HOSTED", dto.getGeneratorHostingType());
    }

    @Test
    void should_supportSettersAndGetters() {
        AgentTestConfigDto dto = new AgentTestConfigDto();
        dto.setGeneratorProvider("anthropic");
        dto.setGeneratorModel("claude-opus-4");
        dto.setGeneratorHostingType("PLATFORM");
        dto.setGeneratorBaseUrl("https://api.anthropic.com");
        dto.setGeneratorApiKey("sk-ant-key");

        assertEquals("anthropic", dto.getGeneratorProvider());
        assertEquals("claude-opus-4", dto.getGeneratorModel());
        assertEquals("PLATFORM", dto.getGeneratorHostingType());
        assertEquals("https://api.anthropic.com", dto.getGeneratorBaseUrl());
        assertEquals("sk-ant-key", dto.getGeneratorApiKey());
    }
}
