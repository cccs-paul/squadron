package com.squadron.gateway.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties.SwaggerUrl;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * OpenAPI aggregation configuration for the API Gateway.
 * <p>
 * Configures the Swagger UI served by the gateway to allow switching between
 * the OpenAPI specs from each downstream microservice. Each service exposes
 * its own {@code /api-docs} endpoint; the gateway proxies these and lists
 * them in a single unified Swagger UI dropdown.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public Set<SwaggerUrl> swaggerUrls() {
        Set<SwaggerUrl> urls = new LinkedHashSet<>();
        urls.add(new SwaggerUrl("identity", "/services/identity/api-docs", "Identity Service"));
        urls.add(new SwaggerUrl("config", "/services/config/api-docs", "Config Service"));
        urls.add(new SwaggerUrl("orchestrator", "/services/orchestrator/api-docs", "Orchestrator Service"));
        urls.add(new SwaggerUrl("platform", "/services/platform/api-docs", "Platform Service"));
        urls.add(new SwaggerUrl("agent", "/services/agent/api-docs", "Agent Service"));
        urls.add(new SwaggerUrl("workspace", "/services/workspace/api-docs", "Workspace Service"));
        urls.add(new SwaggerUrl("git", "/services/git/api-docs", "Git Service"));
        urls.add(new SwaggerUrl("review", "/services/review/api-docs", "Review Service"));
        urls.add(new SwaggerUrl("notification", "/services/notification/api-docs", "Notification Service"));
        return urls;
    }

    @Bean
    public SwaggerUiConfigProperties swaggerUiConfigProperties(Set<SwaggerUrl> swaggerUrls) {
        SwaggerUiConfigProperties properties = new SwaggerUiConfigProperties();
        properties.setUrls(swaggerUrls);
        return properties;
    }

    @Bean
    public GroupedOpenApi gatewayApi() {
        return GroupedOpenApi.builder()
                .group("gateway")
                .pathsToMatch("/api/**")
                .build();
    }
}
