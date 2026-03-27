package com.squadron.gateway.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Health status endpoint that aggregates health checks from all backend services
 * and infrastructure components. This endpoint is publicly accessible (no auth required)
 * so the Angular login page can display health indicators.
 */
@RestController
@RequestMapping("/api/health")
public class HealthStatusController {

    private static final Logger log = LoggerFactory.getLogger(HealthStatusController.class);
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(2);
    private static final String STATUS_UP = "UP";
    private static final String STATUS_DOWN = "DOWN";
    private static final String STATUS_DEGRADED = "DEGRADED";

    /**
     * Critical services whose failure results in an overall DOWN status.
     */
    private static final Set<String> CRITICAL_SERVICES = Set.of("gateway", "identity");
    private static final String CRITICAL_INFRA = "postgresql";

    private final WebClient webClient;
    private final ReactiveStringRedisTemplate redisTemplate;

    @Value("${squadron.health.services.gateway:http://localhost:8443}")
    private String gatewayUrl;

    @Value("${squadron.health.services.identity:http://localhost:8081}")
    private String identityUrl;

    @Value("${squadron.health.services.config:http://localhost:8082}")
    private String configUrl;

    @Value("${squadron.health.services.orchestrator:http://localhost:8083}")
    private String orchestratorUrl;

    @Value("${squadron.health.services.platform:http://localhost:8084}")
    private String platformUrl;

    @Value("${squadron.health.services.agent:http://localhost:8085}")
    private String agentUrl;

    @Value("${squadron.health.services.workspace:http://localhost:8086}")
    private String workspaceUrl;

    @Value("${squadron.health.services.git:http://localhost:8087}")
    private String gitUrl;

    @Value("${squadron.health.services.review:http://localhost:8088}")
    private String reviewUrl;

    @Value("${squadron.health.services.notification:http://localhost:8089}")
    private String notificationUrl;

    @Value("${squadron.health.infrastructure.nats-url:nats://localhost:4222}")
    private String natsUrl;

    @Value("${squadron.health.infrastructure.keycloak-url:#{null}}")
    private String keycloakUrl;

    public HealthStatusController(WebClient.Builder webClientBuilder,
                                  ReactiveStringRedisTemplate redisTemplate) {
        this.webClient = webClientBuilder.build();
        this.redisTemplate = redisTemplate;
    }

    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> getHealthStatus() {
        Mono<Map<String, Map<String, String>>> servicesMono = checkAllServices();
        Mono<Map<String, Map<String, String>>> infraMono = checkAllInfrastructure();

        return Mono.zip(servicesMono, infraMono)
                .map(tuple -> {
                    Map<String, Map<String, String>> services = tuple.getT1();
                    Map<String, Map<String, String>> infrastructure = tuple.getT2();

                    String overallStatus = computeOverallStatus(services, infrastructure);

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("status", overallStatus);
                    response.put("timestamp", Instant.now().toString());
                    response.put("services", services);
                    response.put("infrastructure", infrastructure);
                    return response;
                });
    }

    private Mono<Map<String, Map<String, String>>> checkAllServices() {
        return Mono.zip(
                checkService("gateway", gatewayUrl),
                checkService("identity", identityUrl),
                checkService("config", configUrl),
                checkService("orchestrator", orchestratorUrl),
                checkService("platform", platformUrl),
                checkService("agent", agentUrl),
                checkService("workspace", workspaceUrl),
                checkService("git", gitUrl)
        ).flatMap(tuple8 -> Mono.zip(
                checkService("review", reviewUrl),
                checkService("notification", notificationUrl)
        ).map(tuple2 -> {
            Map<String, Map<String, String>> services = new LinkedHashMap<>();
            services.put("gateway", tuple8.getT1());
            services.put("identity", tuple8.getT2());
            services.put("config", tuple8.getT3());
            services.put("orchestrator", tuple8.getT4());
            services.put("platform", tuple8.getT5());
            services.put("agent", tuple8.getT6());
            services.put("workspace", tuple8.getT7());
            services.put("git", tuple8.getT8());
            services.put("review", tuple2.getT1());
            services.put("notification", tuple2.getT2());
            return services;
        }));
    }

    private Mono<Map<String, String>> checkService(String name, String baseUrl) {
        return webClient.get()
                .uri(baseUrl + "/actuator/health")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(HEALTH_CHECK_TIMEOUT)
                .map(body -> {
                    String status = extractStatus(body);
                    return serviceEntry(status, baseUrl);
                })
                .onErrorResume(ex -> {
                    log.debug("Health check failed for service '{}' at {}: {}", name, baseUrl, ex.getMessage());
                    return Mono.just(serviceEntry(STATUS_DOWN, baseUrl));
                });
    }

    private Mono<Map<String, Map<String, String>>> checkAllInfrastructure() {
        return Mono.zip(
                checkPostgresql(),
                checkRedis(),
                checkNats(),
                checkKeycloak()
        ).map(tuple -> {
            Map<String, Map<String, String>> infra = new LinkedHashMap<>();
            infra.put("postgresql", tuple.getT1());
            infra.put("redis", tuple.getT2());
            infra.put("nats", tuple.getT3());
            infra.put("keycloak", tuple.getT4());
            return infra;
        });
    }

    /**
     * Check PostgreSQL health by calling the identity service's actuator health endpoint
     * and inspecting the db component. Falls back to overall identity service status.
     */
    private Mono<Map<String, String>> checkPostgresql() {
        return webClient.get()
                .uri(identityUrl + "/actuator/health")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(HEALTH_CHECK_TIMEOUT)
                .map(body -> {
                    // Try to extract db component status from the health response
                    String status = extractComponentStatus(body, "db");
                    if (status == null) {
                        // Fall back to overall service status as proxy
                        status = extractStatus(body);
                    }
                    return infraEntry(status);
                })
                .onErrorResume(ex -> {
                    log.debug("PostgreSQL health check failed: {}", ex.getMessage());
                    return Mono.just(infraEntry(STATUS_DOWN));
                });
    }

    /**
     * Check Redis health by executing a simple PING command via the reactive Redis template.
     */
    private Mono<Map<String, String>> checkRedis() {
        return redisTemplate.getConnectionFactory()
                .getReactiveConnection()
                .ping()
                .timeout(HEALTH_CHECK_TIMEOUT)
                .map(pong -> infraEntry(STATUS_UP))
                .onErrorResume(ex -> {
                    log.debug("Redis health check failed: {}", ex.getMessage());
                    return Mono.just(infraEntry(STATUS_DOWN));
                });
    }

    /**
     * Check NATS health by attempting a TCP connection to the NATS URL.
     * Parses the nats:// URL to extract host and port, then performs
     * a lightweight HTTP health check against the NATS monitoring endpoint.
     */
    private Mono<Map<String, String>> checkNats() {
        try {
            String httpUrl = natsUrl.replace("nats://", "http://").replace(":4222", ":8222");
            return webClient.get()
                    .uri(httpUrl + "/healthz")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(HEALTH_CHECK_TIMEOUT)
                    .map(body -> infraEntry(STATUS_UP))
                    .onErrorResume(ex -> {
                        log.debug("NATS health check failed: {}", ex.getMessage());
                        return Mono.just(infraEntry(STATUS_DOWN));
                    });
        } catch (Exception ex) {
            log.debug("NATS URL parsing failed: {}", ex.getMessage());
            return Mono.just(infraEntry(STATUS_DOWN));
        }
    }

    /**
     * Check Keycloak health by calling its health endpoint.
     * Returns UNKNOWN if no Keycloak URL is configured.
     */
    private Mono<Map<String, String>> checkKeycloak() {
        if (keycloakUrl == null || keycloakUrl.isBlank()) {
            return Mono.just(infraEntry("UNKNOWN"));
        }
        return webClient.get()
                .uri(keycloakUrl + "/health")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(HEALTH_CHECK_TIMEOUT)
                .map(body -> {
                    String status = extractStatus(body);
                    return infraEntry(status);
                })
                .onErrorResume(ex -> {
                    log.debug("Keycloak health check failed: {}", ex.getMessage());
                    return Mono.just(infraEntry(STATUS_DOWN));
                });
    }

    String computeOverallStatus(Map<String, Map<String, String>> services,
                                Map<String, Map<String, String>> infrastructure) {
        // Check if critical services are down
        boolean criticalServiceDown = CRITICAL_SERVICES.stream()
                .anyMatch(name -> {
                    Map<String, String> entry = services.get(name);
                    return entry == null || STATUS_DOWN.equals(entry.get("status"));
                });

        // Check if critical infrastructure is down
        Map<String, String> pgEntry = infrastructure.get(CRITICAL_INFRA);
        boolean criticalInfraDown = pgEntry == null || STATUS_DOWN.equals(pgEntry.get("status"));

        if (criticalServiceDown || criticalInfraDown) {
            return STATUS_DOWN;
        }

        // Check if any service or infrastructure is down (non-critical)
        boolean anyServiceDown = services.values().stream()
                .anyMatch(entry -> STATUS_DOWN.equals(entry.get("status")));
        boolean anyInfraDown = infrastructure.values().stream()
                .anyMatch(entry -> STATUS_DOWN.equals(entry.get("status")));

        if (anyServiceDown || anyInfraDown) {
            return STATUS_DEGRADED;
        }

        return STATUS_UP;
    }

    @SuppressWarnings("unchecked")
    private String extractStatus(Map<?, ?> body) {
        if (body == null) {
            return STATUS_DOWN;
        }
        Object status = body.get("status");
        if (status instanceof String s) {
            return s.equalsIgnoreCase("UP") ? STATUS_UP : STATUS_DOWN;
        }
        return STATUS_DOWN;
    }

    @SuppressWarnings("unchecked")
    private String extractComponentStatus(Map<?, ?> body, String componentName) {
        if (body == null) {
            return null;
        }
        Object components = body.get("components");
        if (components instanceof Map<?, ?> comps) {
            Object component = comps.get(componentName);
            if (component instanceof Map<?, ?> comp) {
                Object status = comp.get("status");
                if (status instanceof String s) {
                    return s.equalsIgnoreCase("UP") ? STATUS_UP : STATUS_DOWN;
                }
            }
        }
        return null;
    }

    private Map<String, String> serviceEntry(String status, String url) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("status", status);
        entry.put("url", url);
        return entry;
    }

    private Map<String, String> infraEntry(String status) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("status", status);
        return entry;
    }
}
