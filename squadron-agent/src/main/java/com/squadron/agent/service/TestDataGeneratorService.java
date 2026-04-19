package com.squadron.agent.service;

import com.squadron.agent.dto.AgentConfigDto;
import com.squadron.agent.entity.AgentTestConfig;
import com.squadron.agent.provider.AgentProviderRegistry;
import com.squadron.agent.provider.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Generates fake test data (plans, code, reviews) using a configurable LLM.
 * The model used is determined by the user's {@link AgentTestConfig} settings.
 * Defaults to a local Ollama instance running Gemma 4.
 */
@Service
public class TestDataGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(TestDataGeneratorService.class);

    private final AgentProviderRegistry providerRegistry;

    public TestDataGeneratorService(AgentProviderRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    /**
     * Generates a fake task plan for testing the PLANNING agent capability.
     */
    public String generateFakePlan(AgentConfigDto generatorConfig) {
        String systemPrompt = """
                You are a test data generator for a software development platform.
                Generate a realistic but simple software task plan for a small feature.
                The plan should include:
                1. A clear objective (e.g. "Add a health check endpoint")
                2. 3-5 implementation steps
                3. Expected files to create or modify
                4. Acceptance criteria
                
                Keep it concise — this is for testing an AI planning agent.
                Output ONLY the plan content, no preamble.
                """;

        String userMessage = "Generate a simple task plan for a small feature in a Spring Boot REST API.";
        return callGenerator(generatorConfig, systemPrompt, userMessage, "fake plan");
    }

    /**
     * Generates a fake codebase context for testing the CODE_GENERATION agent capability.
     */
    public String generateFakeCodebase(AgentConfigDto generatorConfig) {
        String systemPrompt = """
                You are a test data generator for a software development platform.
                Generate a small, realistic Java/Spring Boot codebase snippet that represents
                an existing project. Include:
                1. A simple entity class (e.g. Product.java)
                2. A repository interface
                3. A service class with 1-2 methods
                4. A controller with 1-2 endpoints
                
                Use standard Spring Boot patterns. Keep each file short (under 30 lines).
                Format each file as: === filename.java ===\\n<contents>\\n
                Output ONLY the code files, no preamble.
                """;

        String userMessage = "Generate a small Spring Boot codebase for a product catalog feature.";
        return callGenerator(generatorConfig, systemPrompt, userMessage, "fake codebase");
    }

    /**
     * Generates a fake code diff for testing the CODE_REVIEW / QA agent capability.
     */
    public String generateFakeCodeForReview(AgentConfigDto generatorConfig) {
        String systemPrompt = """
                You are a test data generator for a software development platform.
                Generate a realistic code change (diff) for review. Include:
                1. A brief description of the change
                2. The modified/added code (2-3 files, Java/Spring Boot)
                3. Intentionally include 1-2 minor issues for the reviewer to find
                   (e.g. missing null check, hardcoded value, missing test)
                
                Format as a unified diff or clearly show before/after.
                Output ONLY the review material, no preamble.
                """;

        String userMessage = "Generate a code change that adds pagination to an existing REST endpoint.";
        return callGenerator(generatorConfig, systemPrompt, userMessage, "fake review material");
    }

    /**
     * Calls the configured generator model to produce test data.
     * Falls back to a hardcoded stub if the model is unavailable.
     */
    private String callGenerator(AgentConfigDto config, String systemPrompt,
                                  String userMessage, String description) {
        try {
            var provider = providerRegistry.getProvider(config.getProvider());
            String result = provider.chat(systemPrompt, Collections.emptyList(), userMessage, config);
            log.info("Generated {} using provider={}, model={} ({} chars)",
                    description, config.getProvider(), config.getModel(), result.length());
            return result;
        } catch (Exception e) {
            log.warn("Failed to generate {} via LLM, using fallback stub: {}", description, e.getMessage());
            return generateFallbackStub(description);
        }
    }

    /**
     * Returns a hardcoded fallback stub when the generator model is unavailable.
     */
    static String generateFallbackStub(String description) {
        return switch (description) {
            case "fake plan" -> """
                    ## Task: Add Health Check Endpoint
                    
                    ### Objective
                    Add a `/actuator/health/custom` endpoint that reports application-specific health.
                    
                    ### Steps
                    1. Create `CustomHealthIndicator` implementing `HealthIndicator`
                    2. Check database connectivity
                    3. Check external service availability
                    4. Register as a Spring bean
                    
                    ### Files
                    - `src/main/java/com/example/health/CustomHealthIndicator.java` (new)
                    - `src/test/java/com/example/health/CustomHealthIndicatorTest.java` (new)
                    
                    ### Acceptance Criteria
                    - GET /actuator/health/custom returns 200 with status UP/DOWN
                    - Includes database and external service checks
                    - Unit tests cover both UP and DOWN scenarios
                    """;
            case "fake codebase" -> """
                    === Product.java ===
                    package com.example.catalog;
                    
                    import jakarta.persistence.*;
                    import lombok.*;
                    
                    @Entity @Data @Builder @NoArgsConstructor @AllArgsConstructor
                    public class Product {
                        @Id @GeneratedValue(strategy = GenerationType.UUID)
                        private java.util.UUID id;
                        private String name;
                        private String description;
                        private double price;
                    }
                    
                    === ProductRepository.java ===
                    package com.example.catalog;
                    
                    import org.springframework.data.jpa.repository.JpaRepository;
                    import java.util.UUID;
                    
                    public interface ProductRepository extends JpaRepository<Product, UUID> {}
                    
                    === ProductService.java ===
                    package com.example.catalog;
                    
                    import org.springframework.stereotype.Service;
                    import java.util.*;
                    
                    @Service
                    public class ProductService {
                        private final ProductRepository repo;
                        public ProductService(ProductRepository repo) { this.repo = repo; }
                        public List<Product> findAll() { return repo.findAll(); }
                        public Product create(Product p) { return repo.save(p); }
                    }
                    
                    === ProductController.java ===
                    package com.example.catalog;
                    
                    import org.springframework.web.bind.annotation.*;
                    import java.util.*;
                    
                    @RestController @RequestMapping("/api/products")
                    public class ProductController {
                        private final ProductService service;
                        public ProductController(ProductService s) { this.service = s; }
                        @GetMapping public List<Product> list() { return service.findAll(); }
                        @PostMapping public Product create(@RequestBody Product p) { return service.create(p); }
                    }
                    """;
            case "fake review material" -> """
                    ## Change: Add pagination to GET /api/products
                    
                    ### Modified: ProductController.java
                    ```java
                    @GetMapping
                    public Page<Product> list(
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "20") int size) {
                        return service.findAll(page, size);
                    }
                    ```
                    
                    ### Modified: ProductService.java
                    ```java
                    public Page<Product> findAll(int page, int size) {
                        // Issue: no validation on page/size — negative values will cause errors
                        return repo.findAll(PageRequest.of(page, size));
                    }
                    ```
                    
                    ### Missing: No test coverage for the pagination change.
                    """;
            default -> "[Test data generation unavailable — " + description + "]";
        };
    }
}
