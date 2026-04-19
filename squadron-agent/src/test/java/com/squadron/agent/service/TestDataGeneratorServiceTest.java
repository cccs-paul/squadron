package com.squadron.agent.service;

import com.squadron.agent.dto.AgentConfigDto;
import com.squadron.agent.provider.AgentProvider;
import com.squadron.agent.provider.AgentProviderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestDataGeneratorServiceTest {

    @Mock
    private AgentProviderRegistry providerRegistry;

    @Mock
    private AgentProvider mockProvider;

    private TestDataGeneratorService service;

    private AgentConfigDto generatorConfig;

    @BeforeEach
    void setUp() {
        service = new TestDataGeneratorService(providerRegistry);
        generatorConfig = AgentConfigDto.builder()
                .provider("ollama")
                .model("gemma4:e2b")
                .build();
    }

    @Test
    void should_generateFakePlan_when_providerAvailable() {
        when(providerRegistry.getProvider(anyString())).thenReturn(mockProvider);
        when(mockProvider.chat(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn("## Plan: Add health check endpoint\n1. Create class\n2. Test it");

        String result = service.generateFakePlan(generatorConfig);

        assertNotNull(result);
        assertTrue(result.contains("health check"));
    }

    @Test
    void should_useFallbackStub_when_providerFailsForPlan() {
        when(providerRegistry.getProvider(anyString())).thenThrow(new RuntimeException("Connection refused"));

        String result = service.generateFakePlan(generatorConfig);

        assertNotNull(result);
        assertTrue(result.contains("Health Check Endpoint"));
    }

    @Test
    void should_useFallbackStub_when_providerFailsForCodebase() {
        when(providerRegistry.getProvider(anyString())).thenThrow(new RuntimeException("Connection refused"));

        String result = service.generateFakeCodebase(generatorConfig);

        assertNotNull(result);
        assertTrue(result.contains("Product.java"));
    }

    @Test
    void should_useFallbackStub_when_providerFailsForReview() {
        when(providerRegistry.getProvider(anyString())).thenThrow(new RuntimeException("Connection refused"));

        String result = service.generateFakeCodeForReview(generatorConfig);

        assertNotNull(result);
        assertTrue(result.contains("pagination"));
    }

    @Test
    void should_generateFakeCodebase_when_providerAvailable() {
        when(providerRegistry.getProvider(anyString())).thenReturn(mockProvider);
        when(mockProvider.chat(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn("=== MyEntity.java ===\npublic class MyEntity {}");

        String result = service.generateFakeCodebase(generatorConfig);

        assertNotNull(result);
        assertTrue(result.contains("MyEntity"));
    }

    @Test
    void should_generateFakeCodeForReview_when_providerAvailable() {
        when(providerRegistry.getProvider(anyString())).thenReturn(mockProvider);
        when(mockProvider.chat(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn("## Code Review\nMissing null check on line 42");

        String result = service.generateFakeCodeForReview(generatorConfig);

        assertNotNull(result);
        assertTrue(result.contains("null check"));
    }

    @Test
    void should_returnFallbackPlan_when_generatorThrows() {
        when(providerRegistry.getProvider(anyString())).thenReturn(mockProvider);
        when(mockProvider.chat(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenThrow(new RuntimeException("Model timeout"));

        String result = service.generateFakePlan(generatorConfig);

        assertTrue(result.contains("Health Check Endpoint"));
        assertTrue(result.contains("Acceptance Criteria"));
    }

    @Test
    void should_returnFallbackCodebase_when_generatorThrows() {
        when(providerRegistry.getProvider(anyString())).thenReturn(mockProvider);
        when(mockProvider.chat(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenThrow(new RuntimeException("Model timeout"));

        String result = service.generateFakeCodebase(generatorConfig);

        assertTrue(result.contains("ProductRepository.java"));
        assertTrue(result.contains("ProductService.java"));
    }

    @Test
    void should_returnFallbackReview_when_generatorThrows() {
        when(providerRegistry.getProvider(anyString())).thenReturn(mockProvider);
        when(mockProvider.chat(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenThrow(new RuntimeException("Model timeout"));

        String result = service.generateFakeCodeForReview(generatorConfig);

        assertTrue(result.contains("pagination"));
        assertTrue(result.contains("no validation"));
    }

    @Test
    void should_containRealisticContent_when_fallbackStubGeneratedForPlan() {
        String stub = TestDataGeneratorService.generateFallbackStub("fake plan");

        assertTrue(stub.contains("## Task"));
        assertTrue(stub.contains("### Objective"));
        assertTrue(stub.contains("### Steps"));
        assertTrue(stub.contains("### Files"));
        assertTrue(stub.contains("### Acceptance Criteria"));
    }

    @Test
    void should_containRealisticContent_when_fallbackStubGeneratedForCodebase() {
        String stub = TestDataGeneratorService.generateFallbackStub("fake codebase");

        assertTrue(stub.contains("=== Product.java ==="));
        assertTrue(stub.contains("@Entity"));
        assertTrue(stub.contains("JpaRepository"));
        assertTrue(stub.contains("@RestController"));
    }

    @Test
    void should_containRealisticContent_when_fallbackStubGeneratedForReview() {
        String stub = TestDataGeneratorService.generateFallbackStub("fake review material");

        assertTrue(stub.contains("## Change"));
        assertTrue(stub.contains("ProductController.java"));
        assertTrue(stub.contains("ProductService.java"));
        assertTrue(stub.contains("No test coverage"));
    }
}
