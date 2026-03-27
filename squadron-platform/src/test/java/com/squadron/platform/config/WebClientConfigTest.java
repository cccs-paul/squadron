package com.squadron.platform.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class WebClientConfigTest {

    @Test
    void should_createWebClientBuilder() {
        WebClientConfig config = new WebClientConfig();
        WebClient.Builder builder = config.webClientBuilder();
        assertNotNull(builder);
    }

    @Test
    void should_buildWebClientFromBuilder() {
        WebClientConfig config = new WebClientConfig();
        WebClient.Builder builder = config.webClientBuilder();
        WebClient webClient = builder.build();
        assertNotNull(webClient);
    }
}
