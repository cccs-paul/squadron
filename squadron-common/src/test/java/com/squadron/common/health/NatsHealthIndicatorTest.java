package com.squadron.common.health;

import io.nats.client.Connection;
import io.nats.client.api.ServerInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NatsHealthIndicator}.
 */
@ExtendWith(MockitoExtension.class)
class NatsHealthIndicatorTest {

    @Mock
    private Connection natsConnection;

    @Mock
    private ServerInfo serverInfo;

    private NatsHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new NatsHealthIndicator(natsConnection);
    }

    @Test
    void should_returnUp_when_natsConnectionIsConnected() {
        when(natsConnection.getStatus()).thenReturn(Connection.Status.CONNECTED);
        when(natsConnection.getConnectedUrl()).thenReturn("nats://localhost:4222");
        when(natsConnection.getServerInfo()).thenReturn(serverInfo);
        when(serverInfo.getServerId()).thenReturn("server-1");
        when(serverInfo.getVersion()).thenReturn("2.10.0");
        when(serverInfo.getCluster()).thenReturn("squadron-cluster");

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("status", "CONNECTED");
        assertThat(health.getDetails()).containsEntry("connectedUrl", "nats://localhost:4222");
        assertThat(health.getDetails().get("serverInfo").toString())
                .contains("server-1")
                .contains("2.10.0")
                .contains("squadron-cluster");
    }

    @Test
    void should_returnUp_when_clusterIsNull() {
        when(natsConnection.getStatus()).thenReturn(Connection.Status.CONNECTED);
        when(natsConnection.getConnectedUrl()).thenReturn("nats://localhost:4222");
        when(natsConnection.getServerInfo()).thenReturn(serverInfo);
        when(serverInfo.getServerId()).thenReturn("server-1");
        when(serverInfo.getVersion()).thenReturn("2.10.0");
        when(serverInfo.getCluster()).thenReturn(null);

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("serverInfo").toString()).contains("none");
    }

    @Test
    void should_returnDown_when_natsConnectionIsDisconnected() {
        when(natsConnection.getStatus()).thenReturn(Connection.Status.DISCONNECTED);

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("status", "DISCONNECTED");
        assertThat(health.getDetails()).containsEntry("reason", "NATS connection is not in CONNECTED state");
    }

    @Test
    void should_returnDown_when_natsConnectionIsClosed() {
        when(natsConnection.getStatus()).thenReturn(Connection.Status.CLOSED);

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("status", "CLOSED");
    }

    @Test
    void should_returnDown_when_natsConnectionIsReconnecting() {
        when(natsConnection.getStatus()).thenReturn(Connection.Status.RECONNECTING);

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("status", "RECONNECTING");
    }

    @Test
    void should_returnDown_when_exceptionThrown() {
        when(natsConnection.getStatus()).thenThrow(new RuntimeException("Connection lost"));

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("error").toString()).contains("Connection lost");
    }

    @Test
    void should_returnUp_when_serverInfoIsNull() {
        when(natsConnection.getStatus()).thenReturn(Connection.Status.CONNECTED);
        when(natsConnection.getConnectedUrl()).thenReturn("nats://localhost:4222");
        when(natsConnection.getServerInfo()).thenReturn(null);

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("serverInfo")).isEqualTo("unavailable");
    }

    @Test
    void should_returnUp_when_serverInfoThrows() {
        when(natsConnection.getStatus()).thenReturn(Connection.Status.CONNECTED);
        when(natsConnection.getConnectedUrl()).thenReturn("nats://localhost:4222");
        when(natsConnection.getServerInfo()).thenThrow(new RuntimeException("info error"));

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("serverInfo")).isEqualTo("unavailable");
    }

    @Test
    void should_beAnnotatedWithComponent() {
        var annotation = NatsHealthIndicator.class
                .getAnnotation(org.springframework.stereotype.Component.class);
        assertThat(annotation).isNotNull();
    }

    @Test
    void should_beConditionalOnNatsConnectionBean() {
        var annotation = NatsHealthIndicator.class
                .getAnnotation(org.springframework.boot.autoconfigure.condition.ConditionalOnBean.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains(Connection.class);
    }
}
