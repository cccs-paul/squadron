package com.squadron.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SquadronMetricsConfig}.
 */
class SquadronMetricsConfigTest {

    private SquadronMetricsConfig metricsConfig;
    private MeterRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        metricsConfig = new SquadronMetricsConfig();
        registry = new SimpleMeterRegistry();

        // Set the service name via reflection since @Value won't be processed in unit tests
        Field serviceNameField = SquadronMetricsConfig.class.getDeclaredField("serviceName");
        serviceNameField.setAccessible(true);
        serviceNameField.set(metricsConfig, "test-service");

        metricsConfig.bindTo(registry);
    }

    @Test
    void should_beAnnotatedWithConfiguration() {
        var annotation = SquadronMetricsConfig.class
                .getAnnotation(org.springframework.context.annotation.Configuration.class);
        assertThat(annotation).isNotNull();
    }

    @Test
    void should_registerBaseCounters_when_boundToRegistry() {
        // Verify the base counters were created
        Counter publishedCounter = registry.find("squadron.events.published")
                .tag("service", "test-service")
                .tag("event_type", "none")
                .counter();
        assertThat(publishedCounter).isNotNull();
        assertThat(publishedCounter.count()).isEqualTo(0.0);

        Counter consumedCounter = registry.find("squadron.events.consumed")
                .tag("service", "test-service")
                .tag("event_type", "none")
                .counter();
        assertThat(consumedCounter).isNotNull();
        assertThat(consumedCounter.count()).isEqualTo(0.0);

        Counter cbCounter = registry.find("squadron.circuit_breaker.state")
                .tag("service", "test-service")
                .tag("breaker_name", "none")
                .tag("state", "none")
                .counter();
        assertThat(cbCounter).isNotNull();
        assertThat(cbCounter.count()).isEqualTo(0.0);
    }

    @Test
    void should_incrementPublishedCounter_when_recordEventPublished() {
        metricsConfig.recordEventPublished("task.state.changed");

        Counter counter = registry.find("squadron.events.published")
                .tag("service", "test-service")
                .tag("event_type", "task.state.changed")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void should_incrementPublishedMultipleTimes() {
        metricsConfig.recordEventPublished("task.state.changed");
        metricsConfig.recordEventPublished("task.state.changed");
        metricsConfig.recordEventPublished("task.state.changed");

        Counter counter = registry.find("squadron.events.published")
                .tag("service", "test-service")
                .tag("event_type", "task.state.changed")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(3.0);
    }

    @Test
    void should_incrementConsumedCounter_when_recordEventConsumed() {
        metricsConfig.recordEventConsumed("agent.completed");

        Counter counter = registry.find("squadron.events.consumed")
                .tag("service", "test-service")
                .tag("event_type", "agent.completed")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void should_incrementCircuitBreakerCounter_when_stateRecorded() {
        metricsConfig.recordCircuitBreakerState("platform-api", "OPEN");

        Counter counter = registry.find("squadron.circuit_breaker.state")
                .tag("service", "test-service")
                .tag("breaker_name", "platform-api")
                .tag("state", "OPEN")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void should_separateCountersByEventType() {
        metricsConfig.recordEventPublished("type-a");
        metricsConfig.recordEventPublished("type-b");
        metricsConfig.recordEventPublished("type-a");

        Counter counterA = registry.find("squadron.events.published")
                .tag("event_type", "type-a")
                .counter();
        Counter counterB = registry.find("squadron.events.published")
                .tag("event_type", "type-b")
                .counter();

        assertThat(counterA).isNotNull();
        assertThat(counterA.count()).isEqualTo(2.0);
        assertThat(counterB).isNotNull();
        assertThat(counterB.count()).isEqualTo(1.0);
    }

    @Test
    void should_separateCircuitBreakerCountersByNameAndState() {
        metricsConfig.recordCircuitBreakerState("cb-1", "OPEN");
        metricsConfig.recordCircuitBreakerState("cb-1", "CLOSED");
        metricsConfig.recordCircuitBreakerState("cb-2", "OPEN");

        Counter cb1Open = registry.find("squadron.circuit_breaker.state")
                .tag("breaker_name", "cb-1").tag("state", "OPEN").counter();
        Counter cb1Closed = registry.find("squadron.circuit_breaker.state")
                .tag("breaker_name", "cb-1").tag("state", "CLOSED").counter();
        Counter cb2Open = registry.find("squadron.circuit_breaker.state")
                .tag("breaker_name", "cb-2").tag("state", "OPEN").counter();

        assertThat(cb1Open.count()).isEqualTo(1.0);
        assertThat(cb1Closed.count()).isEqualTo(1.0);
        assertThat(cb2Open.count()).isEqualTo(1.0);
    }

    @Test
    void should_notThrow_when_recordBeforeBindTo() throws Exception {
        SquadronMetricsConfig unboundConfig = new SquadronMetricsConfig();
        Field serviceNameField = SquadronMetricsConfig.class.getDeclaredField("serviceName");
        serviceNameField.setAccessible(true);
        serviceNameField.set(unboundConfig, "test-service");

        // These should not throw even though no registry is bound
        unboundConfig.recordEventPublished("test");
        unboundConfig.recordEventConsumed("test");
        unboundConfig.recordCircuitBreakerState("cb", "OPEN");
    }

    @Test
    void should_implementMeterBinder() {
        assertThat(metricsConfig).isInstanceOf(io.micrometer.core.instrument.binder.MeterBinder.class);
    }

    @Test
    void should_getServiceName() {
        assertThat(metricsConfig.getServiceName()).isEqualTo("test-service");
    }
}
