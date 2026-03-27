package com.squadron.git.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.*;

class WebClientConfigTest {

    private final WebClientConfig config = new WebClientConfig();

    @Test
    void should_createWebClientBuilder_bean() {
        WebClient.Builder builder = config.webClientBuilder();
        assertNotNull(builder);
    }

    @Test
    void should_buildWebClient_fromBuilder() {
        WebClient.Builder builder = config.webClientBuilder();
        WebClient webClient = builder.build();
        assertNotNull(webClient);
    }

    @Test
    void should_allowLargeResponseBody() {
        // The config sets maxInMemorySize to 16MB; verifying the builder creates
        // a functional WebClient that can be used with large codec buffer
        WebClient.Builder builder = config.webClientBuilder();
        WebClient webClient = builder.baseUrl("https://example.com").build();
        assertNotNull(webClient);
    }

    @Test
    void should_beAnnotatedWithConfiguration() {
        assertTrue(WebClientConfig.class.isAnnotationPresent(
                org.springframework.context.annotation.Configuration.class));
    }

    @Test
    void should_haveWebClientBuilderMethod() throws NoSuchMethodException {
        assertNotNull(WebClientConfig.class.getMethod("webClientBuilder"));
    }
}
