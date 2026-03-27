package com.squadron.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Common Micrometer metrics configuration for all Squadron services.
 * <p>
 * Registers the following custom metrics:
 * <ul>
 *   <li>{@code squadron.events.published} - counter of published NATS events, tagged by service and event type</li>
 *   <li>{@code squadron.events.consumed} - counter of consumed NATS events, tagged by service and event type</li>
 *   <li>{@code squadron.circuit_breaker.state} - counter of circuit breaker state transitions, tagged by service and breaker name</li>
 * </ul>
 * </p>
 */
@Configuration
public class SquadronMetricsConfig implements MeterBinder {

    @Value("${spring.application.name:unknown}")
    private String serviceName;

    private MeterRegistry registry;

    @Override
    public void bindTo(MeterRegistry registry) {
        this.registry = registry;

        // Pre-register counters with base tags so they appear in /actuator/prometheus
        // even before any events are published.
        Counter.builder("squadron.events.published")
                .description("Number of events published to NATS")
                .tag("service", serviceName)
                .tag("event_type", "none")
                .register(registry);

        Counter.builder("squadron.events.consumed")
                .description("Number of events consumed from NATS")
                .tag("service", serviceName)
                .tag("event_type", "none")
                .register(registry);

        Counter.builder("squadron.circuit_breaker.state")
                .description("Circuit breaker state transition count")
                .tag("service", serviceName)
                .tag("breaker_name", "none")
                .tag("state", "none")
                .register(registry);
    }

    /**
     * Record that an event was published.
     *
     * @param eventType the type/subject of the event
     */
    public void recordEventPublished(String eventType) {
        if (registry != null) {
            Counter.builder("squadron.events.published")
                    .tag("service", serviceName)
                    .tag("event_type", eventType)
                    .register(registry)
                    .increment();
        }
    }

    /**
     * Record that an event was consumed.
     *
     * @param eventType the type/subject of the event
     */
    public void recordEventConsumed(String eventType) {
        if (registry != null) {
            Counter.builder("squadron.events.consumed")
                    .tag("service", serviceName)
                    .tag("event_type", eventType)
                    .register(registry)
                    .increment();
        }
    }

    /**
     * Record a circuit breaker state transition.
     *
     * @param breakerName the circuit breaker name
     * @param state       the new state (CLOSED, OPEN, HALF_OPEN)
     */
    public void recordCircuitBreakerState(String breakerName, String state) {
        if (registry != null) {
            Counter.builder("squadron.circuit_breaker.state")
                    .tag("service", serviceName)
                    .tag("breaker_name", breakerName)
                    .tag("state", state)
                    .register(registry)
                    .increment();
        }
    }

    /**
     * Provides access to the service name for testing.
     */
    String getServiceName() {
        return serviceName;
    }
}
