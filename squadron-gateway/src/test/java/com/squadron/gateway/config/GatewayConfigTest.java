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
        assertThat(routes).hasSize(10);
    }

    @Test
    void should_defineAuthRoute_with_correctPath() {
        List<Route> routes = getRoutes();

        Route authRoute = findRoute(routes, "auth-service");
        assertThat(authRoute).isNotNull();
        assertThat(authRoute.getUri().toString()).isEqualTo("lb://squadron-identity");
    }

    @Test
    void should_defineIdentityRoute_with_correctPath() {
        List<Route> routes = getRoutes();

        Route identityRoute = findRoute(routes, "identity-service");
        assertThat(identityRoute).isNotNull();
        assertThat(identityRoute.getUri().toString()).isEqualTo("lb://squadron-identity");
    }

    @Test
    void should_defineConfigRoute_with_correctUri() {
        List<Route> routes = getRoutes();

        Route route = findRoute(routes, "config-service");
        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("lb://squadron-config");
    }

    @Test
    void should_defineOrchestratorRoute_with_correctUri() {
        List<Route> routes = getRoutes();

        Route route = findRoute(routes, "orchestrator-service");
        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("lb://squadron-orchestrator");
    }

    @Test
    void should_definePlatformRoute_with_correctUri() {
        List<Route> routes = getRoutes();

        Route route = findRoute(routes, "platform-service");
        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("lb://squadron-platform");
    }

    @Test
    void should_defineAgentRoute_with_correctUri() {
        List<Route> routes = getRoutes();

        Route route = findRoute(routes, "agent-service");
        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("lb://squadron-agent");
    }

    @Test
    void should_defineWorkspaceRoute_with_correctUri() {
        List<Route> routes = getRoutes();

        Route route = findRoute(routes, "workspace-service");
        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("lb://squadron-workspace");
    }

    @Test
    void should_defineGitRoute_with_correctUri() {
        List<Route> routes = getRoutes();

        Route route = findRoute(routes, "git-service");
        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("lb://squadron-git");
    }

    @Test
    void should_defineReviewRoute_with_correctUri() {
        List<Route> routes = getRoutes();

        Route route = findRoute(routes, "review-service");
        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("lb://squadron-review");
    }

    @Test
    void should_defineNotificationRoute_with_correctUri() {
        List<Route> routes = getRoutes();

        Route route = findRoute(routes, "notification-service");
        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("lb://squadron-notification");
    }

    @Test
    void should_haveAllExpectedRouteIds() {
        List<Route> routes = getRoutes();

        List<String> routeIds = routes.stream().map(Route::getId).toList();
        assertThat(routeIds).containsExactlyInAnyOrder(
                "auth-service",
                "identity-service",
                "config-service",
                "orchestrator-service",
                "platform-service",
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
    void should_haveAuthRouteWithoutStripPrefix() {
        List<Route> routes = getRoutes();

        Route authRoute = findRoute(routes, "auth-service");
        assertThat(authRoute).isNotNull();
        // auth-service does NOT have stripPrefix -- it forwards /api/auth/** as-is
        assertThat(authRoute.getFilters()).isEmpty();
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
