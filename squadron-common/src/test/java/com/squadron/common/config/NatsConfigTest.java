package com.squadron.common.config;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NatsConfigTest {

    private NatsConfig natsConfig;

    @BeforeEach
    void setUp() throws Exception {
        natsConfig = new NatsConfig();
        Field natsUrlField = NatsConfig.class.getDeclaredField("natsUrl");
        natsUrlField.setAccessible(true);
        natsUrlField.set(natsConfig, "nats://localhost:4222");
    }

    @Test
    void should_createNatsConnection_when_beanCalled() throws Exception {
        Connection mockConnection = mock(Connection.class);

        try (MockedStatic<Nats> natsMock = mockStatic(Nats.class)) {
            natsMock.when(() -> Nats.connect(any(Options.class))).thenReturn(mockConnection);

            Connection connection = natsConfig.natsConnection();

            assertNotNull(connection);
            assertSame(mockConnection, connection);
            natsMock.verify(() -> Nats.connect(any(Options.class)));
        }
    }

    @Test
    void should_throwIOException_when_natsConnectionFails() {
        try (MockedStatic<Nats> natsMock = mockStatic(Nats.class)) {
            natsMock.when(() -> Nats.connect(any(Options.class)))
                    .thenThrow(new IOException("Connection refused"));

            assertThrows(IOException.class, natsConfig::natsConnection);
        }
    }

    @Test
    void should_throwInterruptedException_when_natsConnectionInterrupted() {
        try (MockedStatic<Nats> natsMock = mockStatic(Nats.class)) {
            natsMock.when(() -> Nats.connect(any(Options.class)))
                    .thenThrow(new InterruptedException("interrupted"));

            assertThrows(InterruptedException.class, natsConfig::natsConnection);
        }
    }
}
