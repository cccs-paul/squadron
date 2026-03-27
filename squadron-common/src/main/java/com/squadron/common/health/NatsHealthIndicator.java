package com.squadron.common.health;

import io.nats.client.Connection;
import io.nats.client.api.ServerInfo;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator for NATS messaging connectivity.
 * <p>
 * Reports UP when the NATS connection is in the CONNECTED state,
 * along with the connected server URL. Reports DOWN otherwise,
 * including the current connection status for diagnostics.
 * </p>
 * <p>
 * Only activated when a {@link Connection} bean is present in the
 * application context (i.e., when NATS is configured).
 * </p>
 */
@Component
@ConditionalOnBean(Connection.class)
public class NatsHealthIndicator implements HealthIndicator {

    private final Connection natsConnection;

    public NatsHealthIndicator(Connection natsConnection) {
        this.natsConnection = natsConnection;
    }

    @Override
    public Health health() {
        try {
            Connection.Status status = natsConnection.getStatus();
            if (status == Connection.Status.CONNECTED) {
                return Health.up()
                        .withDetail("status", status.name())
                        .withDetail("connectedUrl", natsConnection.getConnectedUrl())
                        .withDetail("serverInfo", getServerInfo())
                        .build();
            } else {
                return Health.down()
                        .withDetail("status", status.name())
                        .withDetail("reason", "NATS connection is not in CONNECTED state")
                        .build();
            }
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }
    }

    private String getServerInfo() {
        try {
            ServerInfo serverInfo = natsConnection.getServerInfo();
            if (serverInfo != null) {
                return String.format("server=%s, version=%s, cluster=%s",
                        serverInfo.getServerId(),
                        serverInfo.getVersion(),
                        serverInfo.getCluster() != null ? serverInfo.getCluster() : "none");
            }
            return "unavailable";
        } catch (Exception e) {
            return "unavailable";
        }
    }
}
