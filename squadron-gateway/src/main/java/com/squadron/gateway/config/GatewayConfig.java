package com.squadron.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator squadronRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("websocket-agent", r -> r
                        .path("/ws/agent/**")
                        .uri("lb://squadron-agent"))
                .route("websocket-notifications", r -> r
                        .path("/ws/notifications/**")
                        .uri("lb://squadron-notification"))
                .route("auth-service", r -> r
                        .path("/api/auth/**")
                        .uri("lb://squadron-identity"))
                .route("identity-service", r -> r
                        .path("/api/identity/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri("lb://squadron-identity"))
                .route("config-service", r -> r
                        .path("/api/config/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri("lb://squadron-config"))
                .route("orchestrator-service", r -> r
                        .path("/api/tasks/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri("lb://squadron-orchestrator"))
                .route("platform-service", r -> r
                        .path("/api/platforms/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri("lb://squadron-platform"))
                .route("agent-service", r -> r
                        .path("/api/agents/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri("lb://squadron-agent"))
                .route("workspace-service", r -> r
                        .path("/api/workspaces/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri("lb://squadron-workspace"))
                .route("git-service", r -> r
                        .path("/api/git/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri("lb://squadron-git"))
                .route("review-service", r -> r
                        .path("/api/reviews/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri("lb://squadron-review"))
                .route("notification-service", r -> r
                        .path("/api/notifications/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri("lb://squadron-notification"))
                .build();
    }
}
