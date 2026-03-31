package com.squadron.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Value("${squadron.routes.identity-uri:http://localhost:8081}")
    private String identityUri;

    @Value("${squadron.routes.config-uri:http://localhost:8082}")
    private String configUri;

    @Value("${squadron.routes.orchestrator-uri:http://localhost:8083}")
    private String orchestratorUri;

    @Value("${squadron.routes.platform-uri:http://localhost:8084}")
    private String platformUri;

    @Value("${squadron.routes.agent-uri:http://localhost:8085}")
    private String agentUri;

    @Value("${squadron.routes.workspace-uri:http://localhost:8086}")
    private String workspaceUri;

    @Value("${squadron.routes.git-uri:http://localhost:8087}")
    private String gitUri;

    @Value("${squadron.routes.review-uri:http://localhost:8088}")
    private String reviewUri;

    @Value("${squadron.routes.notification-uri:http://localhost:8089}")
    private String notificationUri;

    @Bean
    public RouteLocator squadronRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("websocket-agent", r -> r
                        .path("/ws/agent/**")
                        .uri(agentUri))
                .route("websocket-notifications", r -> r
                        .path("/ws/notifications/**")
                        .uri(notificationUri))
                .route("auth-service", r -> r
                        .path("/api/auth/**")
                        .uri(identityUri))
                .route("identity-service", r -> r
                        .path("/api/identity/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri(identityUri))
                .route("config-service", r -> r
                        .path("/api/config/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri(configUri))
                .route("orchestrator-projects", r -> r
                        .path("/api/projects/**")
                        .uri(orchestratorUri))
                .route("orchestrator-service", r -> r
                        .path("/api/tasks/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri(orchestratorUri))
                .route("platform-service", r -> r
                        .path("/api/platforms/**")
                        .uri(platformUri))
                .route("agent-dashboard", r -> r
                        .path("/api/agents/dashboard/**", "/api/agents/dashboard")
                        .uri(agentUri))
                .route("agent-service", r -> r
                        .path("/api/agents/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri(agentUri))
                .route("workspace-service", r -> r
                        .path("/api/workspaces/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri(workspaceUri))
                .route("git-service", r -> r
                        .path("/api/git/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri(gitUri))
                .route("review-service", r -> r
                        .path("/api/reviews/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri(reviewUri))
                .route("notification-service", r -> r
                        .path("/api/notifications/**")
                        .filters(f -> f.stripPrefix(2))
                        .uri(notificationUri))
                .build();
    }
}
