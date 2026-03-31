package com.squadron.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.factory.StripPrefixGatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GatewayConfig route definitions.
 * Registers only the minimal beans needed by RouteLocatorBuilder (predicate + filter factories).
 */
class GatewayConfigTest {

    private GatewayConfig gatewayConfig;
    private RouteLocatorBuilder routeLocatorBuilder;

    /**
     * Minimal configuration providing only the gateway predicate and filter beans
     * needed by RouteLocatorBuilder to construct routes.
     */
    @Configuration
    static class MinimalGatewayBeans {
        @Bean
        public PathRoutePredicateFactory pathRoutePredicateFactory() {
            return new PathRoutePredicateFactory();
        }

        @Bean
        public StripPrefixGatewayFilterFactory stripPrefixGatewayFilterFactory() {
            return new StripPrefixGatewayFilterFactory();
        }
    }

    @BeforeEach
    void setUp() {
        gatewayConfig = new GatewayConfig();
        // Set all route URIs via reflection (simulating @Value injection)
        ReflectionTestUtils.setField(gatewayConfig, "identityUri", "http://squadron-identity:8081");
        ReflectionTestUtils.setField(gatewayConfig, "configUri", "http://squadron-config:8082");
        ReflectionTestUtils.setField(gatewayConfig, "orchestratorUri", "http://squadron-orchestrator:8083");
        ReflectionTestUtils.setField(gatewayConfig, "platformUri", "http://squadron-platform:8084");
        ReflectionTestUtils.setField(gatewayConfig, "agentUri", "http://squadron-agent:8085");
        ReflectionTestUtils.setField(gatewayConfig, "workspaceUri", "http://squadron-workspace:8086");
        ReflectionTestUtils.setField(gatewayConfig, "gitUri", "http://squadron-git:8087");
        ReflectionTestUtils.setField(gatewayConfig, "reviewUri", "http://squadron-review:8088");
        ReflectionTestUtils.setField(gatewayConfig, "notificationUri", "http://squadron-notification:8089");

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(MinimalGatewayBeans.class);
        context.refresh();
        routeLocatorBuilder = new RouteLocatorBuilder(context);
    }

    @Test
    void should_beAnnotatedWithConfiguration() {
        var annotation = GatewayConfig.class
                .getAnnotation(org.springframework.context.annotation.Configuration.class);
        assertThat(annotation).isNotNull();
    }

    @Test
    void should_haveSquadronRoutesMethod() throws NoSuchMethodException {
        var method = GatewayConfig.class.getMethod("squadronRoutes", RouteLocatorBuilder.class);
        assertThat(method).isNotNull();

        var beanAnnotation = method.getAnnotation(org.springframework.context.annotation.Bean.class);
        assertThat(beanAnnotation).isNotNull();
    }

    @Test
    void should_createRouteLocator_when_builderProvided() {
        RouteLocator routeLocator = gatewayConfig.squadronRoutes(routeLocatorBuilder);

        assertThat(routeLocator).isNotNull();

        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();
        assertThat(routes).hasSize(14);
    }

    @Test
    void should_defineAuthRoute_with_correctPath() {
        List<Route> routes = getRoutes();

        Route authRoute = findRoute(routes, "auth-service");
        assertThat(authRoute).isNotNull();
        assertThat(authRoute.getUri().toString()).isEqualTo("http://squadron-identity:8081");
    }

    @Test
    void should_defineIdentityRoute_with_correctPath() {
        List<Route> routes = getRoutes();

        Route identityRoute = findRoute(routes, "identity-service");
        assertThat(identityRoute).isNotNull();
        assertThat(identityRoute.getUri().toString()).isEqualTo("http://squadron-identity:8081");
    }

    @Test
    void should_defineConfigRoute_with_correctUri() {
        List<Route> routes = getRoutes();

        Route route = findRoute(routes, "config-service");
        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("http://squadron-config:8082");
    }

    @Test
    void should_defineOrchestratorRoute_with_correctUri() {
        List<Route> routes = getRoutes();

        Route route = findRoute(routes, "orchestrator-service");
        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("http://squadron-orchestrator:8083");
    }

    @Test
    void should_definePlatformRoute_with_correctUri() {
        List<Route> routes = getRoutes();

        Route route = findRoute(routes, "platform-service");
        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("http://squadron-platform:8084");
    }

    @Test
    void should_defineAgentRoute_with_correctUri() {
        List<Route> routes = getRoutes();

        Route route = findRoute(routes, "agent-service");
        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("http://squadron-agent:8085");
    }

    @Test
    void should_defineWorkspaceRoute_with_correctUri() {
        List<Route> routes = getRoutes();

        Route route = findRoute(routes, "workspace-service");
        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("http://squadron-workspace:8086");
    }

    @Test
    void should_defineGitRoute_with_correctUri() {
        List<Route> routes = getRoutes();

        Route route = findRoute(routes, "git-service");
        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("http://squadron-git:8087");
    }

    @Test
    void should_defineReviewRoute_with_correctUri() {
        List<Route> routes = getRoutes();

        Route route = findRoute(routes, "review-service");
        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("http://squadron-review:8088");
    }

    @Test
    void should_defineNotificationRoute_with_correctUri() {
        List<Route> routes = getRoutes();

        Route route = findRoute(routes, "notification-service");
        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("http://squadron-notification:8089");
    }

    @Test
    void should_haveAllExpectedRouteIds() {
        List<Route> routes = getRoutes();

        List<String> routeIds = routes.stream().map(Route::getId).toList();
        assertThat(routeIds).containsExactlyInAnyOrder(
                "websocket-agent",
                "websocket-notifications",
                "auth-service",
                "identity-service",
                "config-service",
                "orchestrator-projects",
                "orchestrator-service",
                "platform-service",
                "agent-dashboard",
                "agent-service",
                "workspace-service",
                "git-service",
                "review-service",
                "notification-service"
        );
    }

    @Test
    void should_haveIdentityRouteWithFilters() {
        List<Route> routes = getRoutes();

        Route identityRoute = findRoute(routes, "identity-service");
        assertThat(identityRoute).isNotNull();
        // identity-service has a stripPrefix filter
        assertThat(identityRoute.getFilters()).isNotEmpty();
    }

    @Test
    void should_defineWebsocketAgentRoute_with_correctUri() {
        List<Route> routes = getRoutes();

        Route route = findRoute(routes, "websocket-agent");
        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("http://squadron-agent:8085");
        assertThat(route.getFilters()).isEmpty();
    }

    @Test
    void should_defineWebsocketNotificationsRoute_with_correctUri() {
        List<Route> routes = getRoutes();

        Route route = findRoute(routes, "websocket-notifications");
        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("http://squadron-notification:8089");
        assertThat(route.getFilters()).isEmpty();
    }

    @Test
    void should_defineOrchestratorProjectsRoute_with_correctUri() {
        List<Route> routes = getRoutes();

        Route route = findRoute(routes, "orchestrator-projects");
        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("http://squadron-orchestrator:8083");
        // orchestrator-projects does NOT have stripPrefix -- it forwards /api/projects/** as-is
        assertThat(route.getFilters()).isEmpty();
    }

    @Test
    void should_defineAgentDashboardRoute_with_correctUri() {
        List<Route> routes = getRoutes();

        Route route = findRoute(routes, "agent-dashboard");
        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("http://squadron-agent:8085");
        // agent-dashboard does NOT have stripPrefix -- it forwards /api/agents/dashboard as-is
        assertThat(route.getFilters()).isEmpty();
    }

    @Test
    void should_haveAuthRouteWithoutStripPrefix() {
        List<Route> routes = getRoutes();

        Route authRoute = findRoute(routes, "auth-service");
        assertThat(authRoute).isNotNull();
        // auth-service does NOT have stripPrefix -- it forwards /api/auth/** as-is
        assertThat(authRoute.getFilters()).isEmpty();
    }

    @Test
    void should_havePlatformRouteWithoutStripPrefix() {
        List<Route> routes = getRoutes();

        Route route = findRoute(routes, "platform-service");
        assertThat(route).isNotNull();
        // platform-service does NOT have stripPrefix -- it forwards /api/platforms/** as-is
        // because the platform controllers use @RequestMapping("/api/platforms/connections") etc.
        assertThat(route.getFilters()).isEmpty();
    }

    private List<Route> getRoutes() {
        RouteLocator routeLocator = gatewayConfig.squadronRoutes(routeLocatorBuilder);
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();
        return routes;
    }

    private Route findRoute(List<Route> routes, String routeId) {
        return routes.stream()
                .filter(r -> routeId.equals(r.getId()))
                .findFirst()
                .orElse(null);
    }
}
