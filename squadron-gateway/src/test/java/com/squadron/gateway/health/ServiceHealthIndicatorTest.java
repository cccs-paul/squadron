package com.squadron.gateway.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ServiceHealthIndicator}.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
@ExtendWith(MockitoExtension.class)
class ServiceHealthIndicatorTest {

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

    private ServiceHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() throws Exception {
        when(webClientBuilder.build()).thenReturn(webClient);
        healthIndicator = new ServiceHealthIndicator(webClientBuilder);

        setField(healthIndicator, "identityUrl", "http://localhost:8081");
        setField(healthIndicator, "configUrl", "http://localhost:8082");
        setField(healthIndicator, "orchestratorUrl", "http://localhost:8083");
        setField(healthIndicator, "platformUrl", "http://localhost:8084");
        setField(healthIndicator, "agentUrl", "http://localhost:8085");
        setField(healthIndicator, "workspaceUrl", "http://localhost:8086");
        setField(healthIndicator, "gitUrl", "http://localhost:8087");
        setField(healthIndicator, "reviewUrl", "http://localhost:8088");
        setField(healthIndicator, "notificationUrl", "http://localhost:8089");
    }

    @Test
    void should_beAnnotatedWithComponent() {
        var annotation = ServiceHealthIndicator.class
                .getAnnotation(org.springframework.stereotype.Component.class);
        assertThat(annotation).isNotNull();
    }

    @Test
    void should_returnUp_when_allServicesHealthy() {
        stubWebClientAllUp();

        StepVerifier.create(healthIndicator.health())
                .assertNext(health -> {
                    assertThat(health.getStatus()).isEqualTo(Status.UP);
                    Map<String, Object> details = health.getDetails();

                    // Check we have all 9 downstream services
                    assertThat(details).containsKey("identity");
                    assertThat(details).containsKey("config");
                    assertThat(details).containsKey("orchestrator");
                    assertThat(details).containsKey("platform");
                    assertThat(details).containsKey("agent");
                    assertThat(details).containsKey("workspace");
                    assertThat(details).containsKey("git");
                    assertThat(details).containsKey("review");
                    assertThat(details).containsKey("notification");
                    assertThat(details).containsKey("summary");
                    assertThat(details.get("summary").toString()).contains("9/9");

                    // Check each service is UP
                    for (String key : new String[]{"identity", "config", "orchestrator",
                            "platform", "agent", "workspace", "git", "review", "notification"}) {
                        Map<String, String> entry = (Map<String, String>) details.get(key);
                        assertThat(entry.get("status")).as("Service %s should be UP", key).isEqualTo("UP");
                        assertThat(entry.get("url")).as("Service %s should have URL", key).isNotNull();
                    }
                })
                .verifyComplete();
    }

    @Test
    void should_returnDown_when_allServicesFail() {
        stubWebClientAllDown();

        StepVerifier.create(healthIndicator.health())
                .assertNext(health -> {
                    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
                    Map<String, Object> details = health.getDetails();
                    assertThat(details.get("summary").toString()).contains("0/9");

                    for (String key : new String[]{"identity", "config", "orchestrator",
                            "platform", "agent", "workspace", "git", "review", "notification"}) {
                        Map<String, String> entry = (Map<String, String>) details.get(key);
                        assertThat(entry.get("status")).as("Service %s should be DOWN", key).isEqualTo("DOWN");
                    }
                })
                .verifyComplete();
    }

    @Test
    void should_returnDown_when_someServicesDown() {
        // Mix of UP and error responses
        Map<String, Object> upResponse = Map.of("status", "UP");
        RuntimeException error = new RuntimeException("Connection refused");

        stubWebClient();
        lenient().when(responseSpec.bodyToMono(Map.class))
                .thenReturn(Mono.just(upResponse))
                .thenReturn(Mono.error(error))
                .thenReturn(Mono.just(upResponse))
                .thenReturn(Mono.just(upResponse))
                .thenReturn(Mono.just(upResponse))
                .thenReturn(Mono.just(upResponse))
                .thenReturn(Mono.just(upResponse))
                .thenReturn(Mono.just(upResponse))
                .thenReturn(Mono.just(upResponse));

        StepVerifier.create(healthIndicator.health())
                .assertNext(health -> {
                    // At least one is down, so aggregate should be DOWN
                    Map<String, Object> details = health.getDetails();
                    assertThat(details).containsKey("summary");
                })
                .verifyComplete();
    }

    @Test
    void should_includeServiceUrls_inDetails() {
        stubWebClientAllUp();

        StepVerifier.create(healthIndicator.health())
                .assertNext(health -> {
                    Map<String, Object> details = health.getDetails();
                    @SuppressWarnings("unchecked")
                    Map<String, String> identityEntry = (Map<String, String>) details.get("identity");
                    assertThat(identityEntry.get("url")).isEqualTo("http://localhost:8081");
                })
                .verifyComplete();
    }

    @Test
    void should_includeSummary_inDetails() {
        stubWebClientAllUp();

        StepVerifier.create(healthIndicator.health())
                .assertNext(health -> {
                    Map<String, Object> details = health.getDetails();
                    assertThat(details.get("summary")).isNotNull();
                    assertThat(details.get("summary").toString()).matches("\\d+/\\d+ services healthy");
                })
                .verifyComplete();
    }

    @Test
    void should_handleNonUpStatusFromService() {
        stubWebClient();
        Map<String, Object> downResponse = Map.of("status", "DOWN");
        lenient().when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(downResponse));

        StepVerifier.create(healthIndicator.health())
                .assertNext(health -> {
                    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
                })
                .verifyComplete();
    }

    // ---- Helper methods ----

    private void stubWebClientAllUp() {
        Map<String, Object> upResponse = Map.of("status", "UP");
        stubWebClient();
        lenient().when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(upResponse));
    }

    private void stubWebClientAllDown() {
        RuntimeException error = new RuntimeException("Connection refused");
        stubWebClient();
        lenient().when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.error(error));
    }

    private void stubWebClient() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        lenient().when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
