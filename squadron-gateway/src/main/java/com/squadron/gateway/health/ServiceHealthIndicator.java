package com.squadron.gateway.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom reactive health indicator for the API Gateway that checks
 * all downstream backend service health endpoints.
 * <p>
 * Reports UP when all downstream services are healthy, DOWN when any
 * critical service is unreachable, and includes per-service detail.
 * </p>
 */
@Component
public class ServiceHealthIndicator implements ReactiveHealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(ServiceHealthIndicator.class);
    private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(3);

    private final WebClient webClient;

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

    public ServiceHealthIndicator(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Override
    public Mono<Health> health() {
        Map<String, String> serviceUrls = buildServiceMap();

        return Flux.fromIterable(serviceUrls.entrySet())
                .flatMap(entry -> checkService(entry.getKey(), entry.getValue()))
                .collectList()
                .map(results -> {
                    Map<String, Object> details = new LinkedHashMap<>();
                    boolean allUp = true;
                    int upCount = 0;
                    int totalCount = results.size();

                    for (ServiceCheckResult result : results) {
                        details.put(result.name(), Map.of(
                                "status", result.status(),
                                "url", result.url()));
                        if (!"UP".equals(result.status())) {
                            allUp = false;
                        } else {
                            upCount++;
                        }
                    }

                    details.put("summary", String.format("%d/%d services healthy", upCount, totalCount));

                    if (allUp) {
                        return Health.up().withDetails(details).build();
                    } else {
                        return Health.down().withDetails(details).build();
                    }
                });
    }

    private Map<String, String> buildServiceMap() {
        Map<String, String> services = new LinkedHashMap<>();
        services.put("identity", identityUrl);
        services.put("config", configUrl);
        services.put("orchestrator", orchestratorUrl);
        services.put("platform", platformUrl);
        services.put("agent", agentUrl);
        services.put("workspace", workspaceUrl);
        services.put("git", gitUrl);
        services.put("review", reviewUrl);
        services.put("notification", notificationUrl);
        return services;
    }

    @SuppressWarnings("unchecked")
    private Mono<ServiceCheckResult> checkService(String name, String baseUrl) {
        return webClient.get()
                .uri(baseUrl + "/actuator/health")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(CHECK_TIMEOUT)
                .map(body -> {
                    Object status = body.get("status");
                    String statusStr = (status instanceof String s && s.equalsIgnoreCase("UP"))
                            ? "UP" : "DOWN";
                    return new ServiceCheckResult(name, statusStr, baseUrl);
                })
                .onErrorResume(ex -> {
                    log.debug("Downstream health check failed for '{}' at {}: {}",
                            name, baseUrl, ex.getMessage());
                    return Mono.just(new ServiceCheckResult(name, "DOWN", baseUrl));
                });
    }

    /**
     * Internal record for health check results.
     */
    record ServiceCheckResult(String name, String status, String url) {
    }
}
