package com.squadron.gateway.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for HealthStatusController.
 */
@ExtendWith(MockitoExtension.class)
class HealthStatusControllerTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveRedisConnectionFactory connectionFactory;

    @Mock
    private ReactiveRedisConnection reactiveRedisConnection;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private HealthStatusController controller;

    @BeforeEach
    void setUp() throws Exception {
        when(webClientBuilder.build()).thenReturn(webClient);

        controller = new HealthStatusController(webClientBuilder, redisTemplate);

        setField(controller, "gatewayUrl", "http://localhost:8443");
        setField(controller, "identityUrl", "http://localhost:8081");
        setField(controller, "configUrl", "http://localhost:8082");
        setField(controller, "orchestratorUrl", "http://localhost:8083");
        setField(controller, "platformUrl", "http://localhost:8084");
        setField(controller, "agentUrl", "http://localhost:8085");
        setField(controller, "workspaceUrl", "http://localhost:8086");
        setField(controller, "gitUrl", "http://localhost:8087");
        setField(controller, "reviewUrl", "http://localhost:8088");
        setField(controller, "notificationUrl", "http://localhost:8089");
        setField(controller, "natsUrl", "nats://localhost:4222");
        setField(controller, "keycloakUrl", null);
    }

    @Test
    void should_beAnnotatedWithRestController() {
        var annotation = HealthStatusController.class
                .getAnnotation(org.springframework.web.bind.annotation.RestController.class);
        assertThat(annotation).isNotNull();
    }

    @Test
    void should_beAnnotatedWithRequestMapping() {
        var annotation = HealthStatusController.class
                .getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly("/api/health");
    }

    @Test
    void should_haveGetStatusMethod() throws NoSuchMethodException {
        var method = HealthStatusController.class.getMethod("getHealthStatus");
        var getMapping = method.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class);
        assertThat(getMapping).isNotNull();
        assertThat(getMapping.value()).containsExactly("/status");
    }

    @Test
    void should_haveGetStatusProduceJson() throws NoSuchMethodException {
        var method = HealthStatusController.class.getMethod("getHealthStatus");
        var getMapping = method.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class);
        assertThat(getMapping).isNotNull();
        assertThat(getMapping.produces()).containsExactly("application/json");
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_returnAllUp_when_allServicesHealthy() {
        stubWebClientAllUp();
        stubRedisUp();

        StepVerifier.create(controller.getHealthStatus())
                .assertNext(response -> {
                    assertThat(response.get("status")).isEqualTo("UP");
                    assertThat(response.get("timestamp")).isNotNull();

                    Map<String, Map<String, String>> services =
                            (Map<String, Map<String, String>>) response.get("services");
                    assertThat(services).containsKeys("gateway", "identity", "config",
                            "orchestrator", "platform", "agent", "workspace", "git",
                            "review", "notification");
                    assertThat(services).hasSize(10);

                    services.forEach((name, entry) -> {
                        assertThat(entry.get("status")).as("Service %s should be UP", name).isEqualTo("UP");
                        assertThat(entry.get("url")).as("Service %s should have url", name).isNotNull();
                    });

                    Map<String, Map<String, String>> infra =
                            (Map<String, Map<String, String>>) response.get("infrastructure");
                    assertThat(infra).containsKeys("postgresql", "redis", "nats", "keycloak");
                    assertThat(infra.get("postgresql").get("status")).isEqualTo("UP");
                    assertThat(infra.get("redis").get("status")).isEqualTo("UP");
                    assertThat(infra.get("nats").get("status")).isEqualTo("UP");
                    assertThat(infra.get("keycloak").get("status")).isEqualTo("UNKNOWN");
                })
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_returnDown_when_allServicesFail() {
        stubWebClientAllDown();
        stubRedisDown();

        StepVerifier.create(controller.getHealthStatus())
                .assertNext(response -> {
                    assertThat(response.get("status")).isEqualTo("DOWN");

                    Map<String, Map<String, String>> services =
                            (Map<String, Map<String, String>>) response.get("services");
                    services.forEach((name, entry) -> {
                        assertThat(entry.get("status")).as("Service %s should be DOWN", name).isEqualTo("DOWN");
                    });

                    Map<String, Map<String, String>> infra =
                            (Map<String, Map<String, String>>) response.get("infrastructure");
                    assertThat(infra.get("postgresql").get("status")).isEqualTo("DOWN");
                    assertThat(infra.get("redis").get("status")).isEqualTo("DOWN");
                    assertThat(infra.get("nats").get("status")).isEqualTo("DOWN");
                    assertThat(infra.get("keycloak").get("status")).isEqualTo("UNKNOWN");
                })
                .verifyComplete();
    }

    @Test
    void should_returnDown_when_criticalServiceGatewayDown() {
        Map<String, Map<String, String>> services = new LinkedHashMap<>();
        services.put("gateway", Map.of("status", "DOWN", "url", "http://localhost:8443"));
        services.put("identity", Map.of("status", "UP", "url", "http://localhost:8081"));
        services.put("config", Map.of("status", "UP", "url", "http://localhost:8082"));

        Map<String, Map<String, String>> infra = allInfraUp();

        String status = controller.computeOverallStatus(services, infra);
        assertThat(status).isEqualTo("DOWN");
    }

    @Test
    void should_returnDown_when_criticalServiceIdentityDown() {
        Map<String, Map<String, String>> services = new LinkedHashMap<>();
        services.put("gateway", Map.of("status", "UP", "url", "http://localhost:8443"));
        services.put("identity", Map.of("status", "DOWN", "url", "http://localhost:8081"));
        services.put("config", Map.of("status", "UP", "url", "http://localhost:8082"));

        Map<String, Map<String, String>> infra = allInfraUp();

        String status = controller.computeOverallStatus(services, infra);
        assertThat(status).isEqualTo("DOWN");
    }

    @Test
    void should_returnDown_when_postgresqlDown() {
        Map<String, Map<String, String>> services = new LinkedHashMap<>();
        services.put("gateway", Map.of("status", "UP", "url", "http://localhost:8443"));
        services.put("identity", Map.of("status", "UP", "url", "http://localhost:8081"));

        Map<String, Map<String, String>> infra = new LinkedHashMap<>();
        infra.put("postgresql", Map.of("status", "DOWN"));
        infra.put("redis", Map.of("status", "UP"));
        infra.put("nats", Map.of("status", "UP"));
        infra.put("keycloak", Map.of("status", "UP"));

        String status = controller.computeOverallStatus(services, infra);
        assertThat(status).isEqualTo("DOWN");
    }

    @Test
    void should_returnDegraded_when_nonCriticalServiceDown() {
        Map<String, Map<String, String>> services = new LinkedHashMap<>();
        services.put("gateway", Map.of("status", "UP", "url", "http://localhost:8443"));
        services.put("identity", Map.of("status", "UP", "url", "http://localhost:8081"));
        services.put("config", Map.of("status", "DOWN", "url", "http://localhost:8082"));
        services.put("orchestrator", Map.of("status", "UP", "url", "http://localhost:8083"));

        Map<String, Map<String, String>> infra = allInfraUp();

        String status = controller.computeOverallStatus(services, infra);
        assertThat(status).isEqualTo("DEGRADED");
    }

    @Test
    void should_returnDegraded_when_redisDown() {
        Map<String, Map<String, String>> services = new LinkedHashMap<>();
        services.put("gateway", Map.of("status", "UP", "url", "http://localhost:8443"));
        services.put("identity", Map.of("status", "UP", "url", "http://localhost:8081"));

        Map<String, Map<String, String>> infra = new LinkedHashMap<>();
        infra.put("postgresql", Map.of("status", "UP"));
        infra.put("redis", Map.of("status", "DOWN"));
        infra.put("nats", Map.of("status", "UP"));
        infra.put("keycloak", Map.of("status", "UP"));

        String status = controller.computeOverallStatus(services, infra);
        assertThat(status).isEqualTo("DEGRADED");
    }

    @Test
    void should_returnDegraded_when_natsDown() {
        Map<String, Map<String, String>> services = new LinkedHashMap<>();
        services.put("gateway", Map.of("status", "UP", "url", "http://localhost:8443"));
        services.put("identity", Map.of("status", "UP", "url", "http://localhost:8081"));

        Map<String, Map<String, String>> infra = new LinkedHashMap<>();
        infra.put("postgresql", Map.of("status", "UP"));
        infra.put("redis", Map.of("status", "UP"));
        infra.put("nats", Map.of("status", "DOWN"));
        infra.put("keycloak", Map.of("status", "UP"));

        String status = controller.computeOverallStatus(services, infra);
        assertThat(status).isEqualTo("DEGRADED");
    }

    @Test
    void should_returnDegraded_when_keycloakDown() {
        Map<String, Map<String, String>> services = new LinkedHashMap<>();
        services.put("gateway", Map.of("status", "UP", "url", "http://localhost:8443"));
        services.put("identity", Map.of("status", "UP", "url", "http://localhost:8081"));

        Map<String, Map<String, String>> infra = new LinkedHashMap<>();
        infra.put("postgresql", Map.of("status", "UP"));
        infra.put("redis", Map.of("status", "UP"));
        infra.put("nats", Map.of("status", "UP"));
        infra.put("keycloak", Map.of("status", "DOWN"));

        String status = controller.computeOverallStatus(services, infra);
        assertThat(status).isEqualTo("DEGRADED");
    }

    @Test
    void should_returnUp_when_allComponentsUp() {
        Map<String, Map<String, String>> services = new LinkedHashMap<>();
        services.put("gateway", Map.of("status", "UP", "url", "http://localhost:8443"));
        services.put("identity", Map.of("status", "UP", "url", "http://localhost:8081"));
        services.put("config", Map.of("status", "UP", "url", "http://localhost:8082"));

        Map<String, Map<String, String>> infra = allInfraUp();

        String status = controller.computeOverallStatus(services, infra);
        assertThat(status).isEqualTo("UP");
    }

    @Test
    void should_returnDown_when_multipleCriticalComponentsDown() {
        Map<String, Map<String, String>> services = new LinkedHashMap<>();
        services.put("gateway", Map.of("status", "DOWN", "url", "http://localhost:8443"));
        services.put("identity", Map.of("status", "DOWN", "url", "http://localhost:8081"));

        Map<String, Map<String, String>> infra = new LinkedHashMap<>();
        infra.put("postgresql", Map.of("status", "DOWN"));
        infra.put("redis", Map.of("status", "DOWN"));
        infra.put("nats", Map.of("status", "DOWN"));
        infra.put("keycloak", Map.of("status", "DOWN"));

        String status = controller.computeOverallStatus(services, infra);
        assertThat(status).isEqualTo("DOWN");
    }

    @Test
    void should_treatUnknownAsNotDown_when_computingOverallStatus() {
        Map<String, Map<String, String>> services = new LinkedHashMap<>();
        services.put("gateway", Map.of("status", "UP", "url", "http://localhost:8443"));
        services.put("identity", Map.of("status", "UP", "url", "http://localhost:8081"));

        Map<String, Map<String, String>> infra = new LinkedHashMap<>();
        infra.put("postgresql", Map.of("status", "UP"));
        infra.put("redis", Map.of("status", "UP"));
        infra.put("nats", Map.of("status", "UP"));
        infra.put("keycloak", Map.of("status", "UNKNOWN"));

        String status = controller.computeOverallStatus(services, infra);
        assertThat(status).isEqualTo("UP");
    }

    @Test
    void should_returnDown_when_criticalServiceMissing() {
        Map<String, Map<String, String>> services = new LinkedHashMap<>();
        // "gateway" is missing entirely
        services.put("identity", Map.of("status", "UP", "url", "http://localhost:8081"));

        Map<String, Map<String, String>> infra = new LinkedHashMap<>();
        infra.put("postgresql", Map.of("status", "UP"));
        infra.put("redis", Map.of("status", "UP"));

        String status = controller.computeOverallStatus(services, infra);
        assertThat(status).isEqualTo("DOWN");
    }

    @Test
    void should_returnDown_when_postgresqlMissing() {
        Map<String, Map<String, String>> services = new LinkedHashMap<>();
        services.put("gateway", Map.of("status", "UP", "url", "http://localhost:8443"));
        services.put("identity", Map.of("status", "UP", "url", "http://localhost:8081"));

        Map<String, Map<String, String>> infra = new LinkedHashMap<>();
        // "postgresql" is missing entirely
        infra.put("redis", Map.of("status", "UP"));
        infra.put("nats", Map.of("status", "UP"));

        String status = controller.computeOverallStatus(services, infra);
        assertThat(status).isEqualTo("DOWN");
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_containTimestampInResponse_when_healthy() {
        stubWebClientAllUp();
        stubRedisUp();

        StepVerifier.create(controller.getHealthStatus())
                .assertNext(response -> {
                    String timestamp = (String) response.get("timestamp");
                    assertThat(timestamp).isNotNull().isNotEmpty();
                    assertThat(java.time.Instant.parse(timestamp)).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_includeServiceUrls_when_returning() {
        stubWebClientAllUp();
        stubRedisUp();

        StepVerifier.create(controller.getHealthStatus())
                .assertNext(response -> {
                    Map<String, Map<String, String>> services =
                            (Map<String, Map<String, String>>) response.get("services");
                    assertThat(services.get("gateway").get("url")).isEqualTo("http://localhost:8443");
                    assertThat(services.get("identity").get("url")).isEqualTo("http://localhost:8081");
                    assertThat(services.get("config").get("url")).isEqualTo("http://localhost:8082");
                    assertThat(services.get("orchestrator").get("url")).isEqualTo("http://localhost:8083");
                    assertThat(services.get("platform").get("url")).isEqualTo("http://localhost:8084");
                    assertThat(services.get("agent").get("url")).isEqualTo("http://localhost:8085");
                    assertThat(services.get("workspace").get("url")).isEqualTo("http://localhost:8086");
                    assertThat(services.get("git").get("url")).isEqualTo("http://localhost:8087");
                    assertThat(services.get("review").get("url")).isEqualTo("http://localhost:8088");
                    assertThat(services.get("notification").get("url")).isEqualTo("http://localhost:8089");
                })
                .verifyComplete();
    }

    @Test
    void should_haveComputeOverallStatusMethod() throws NoSuchMethodException {
        var method = HealthStatusController.class.getDeclaredMethod(
                "computeOverallStatus", Map.class, Map.class);
        assertThat(method).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_returnFourInfrastructureComponents() {
        stubWebClientAllUp();
        stubRedisUp();

        StepVerifier.create(controller.getHealthStatus())
                .assertNext(response -> {
                    Map<String, Map<String, String>> infra =
                            (Map<String, Map<String, String>>) response.get("infrastructure");
                    assertThat(infra).hasSize(4);
                    assertThat(infra).containsKeys("postgresql", "redis", "nats", "keycloak");
                })
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_returnTenServices() {
        stubWebClientAllUp();
        stubRedisUp();

        StepVerifier.create(controller.getHealthStatus())
                .assertNext(response -> {
                    Map<String, Map<String, String>> services =
                            (Map<String, Map<String, String>>) response.get("services");
                    assertThat(services).hasSize(10);
                })
                .verifyComplete();
    }

    // ---- Helper methods ----

    @SuppressWarnings("unchecked")
    private void stubWebClientAllUp() {
        Map<String, Object> upResponse = Map.of("status", "UP");
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        lenient().when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        lenient().when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(upResponse));
        lenient().when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("OK"));
    }

    @SuppressWarnings("unchecked")
    private void stubWebClientAllDown() {
        RuntimeException error = new RuntimeException("Connection refused");
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        lenient().when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        lenient().when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.error(error));
        lenient().when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(error));
    }

    private void stubRedisUp() {
        when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.getReactiveConnection()).thenReturn(reactiveRedisConnection);
        when(reactiveRedisConnection.ping()).thenReturn(Mono.just("PONG"));
    }

    private void stubRedisDown() {
        when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.getReactiveConnection()).thenReturn(reactiveRedisConnection);
        when(reactiveRedisConnection.ping()).thenReturn(Mono.error(new RuntimeException("Connection refused")));
    }

    private Map<String, Map<String, String>> allInfraUp() {
        Map<String, Map<String, String>> infra = new LinkedHashMap<>();
        infra.put("postgresql", Map.of("status", "UP"));
        infra.put("redis", Map.of("status", "UP"));
        infra.put("nats", Map.of("status", "UP"));
        infra.put("keycloak", Map.of("status", "UP"));
        return infra;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
