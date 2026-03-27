package com.squadron.common.config;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JetStreamConfigTest {

    @Mock
    private Connection natsConnection;

    @Mock
    private JetStreamManagement jetStreamManagement;

    @Mock
    private JetStream jetStream;

    @Mock
    private StreamInfo streamInfo;

    private JetStreamConfig jetStreamConfig;

    @BeforeEach
    void setUp() throws IOException {
        lenient().when(natsConnection.jetStreamManagement()).thenReturn(jetStreamManagement);
        jetStreamConfig = new JetStreamConfig(natsConnection);
    }

    @Test
    void should_createJetStreamBean_when_connectionAvailable() throws IOException {
        when(natsConnection.jetStream()).thenReturn(jetStream);
        JetStream result = jetStreamConfig.jetStream();
        assertNotNull(result);
        assertSame(jetStream, result);
    }

    @Test
    void should_createJetStreamManagementBean_when_connectionAvailable() throws IOException {
        JetStreamManagement result = jetStreamConfig.jetStreamManagement();
        assertNotNull(result);
        assertSame(jetStreamManagement, result);
    }

    @Test
    void should_createAllStreams_when_postConstructCalled() throws Exception {
        // All streams are new (getStreamInfo throws, so addStream is called)
        when(jetStreamManagement.getStreamInfo(anyString())).thenThrow(new RuntimeException("not found"));
        when(jetStreamManagement.addStream(any(StreamConfiguration.class))).thenReturn(streamInfo);

        jetStreamConfig.createStreams();

        // Verify 10 streams are created
        verify(jetStreamManagement, times(10)).addStream(any(StreamConfiguration.class));
    }

    @Test
    void should_updateExistingStream_when_streamAlreadyExists() throws Exception {
        // Stream exists
        when(jetStreamManagement.getStreamInfo("TASKS")).thenReturn(streamInfo);
        when(jetStreamManagement.updateStream(any(StreamConfiguration.class))).thenReturn(streamInfo);

        // Other streams are new
        when(jetStreamManagement.getStreamInfo(argThat(name -> !"TASKS".equals(name))))
                .thenThrow(new RuntimeException("not found"));
        when(jetStreamManagement.addStream(any(StreamConfiguration.class))).thenReturn(streamInfo);

        jetStreamConfig.createStreams();

        // TASKS stream is updated, not added
        verify(jetStreamManagement, atLeastOnce()).updateStream(any(StreamConfiguration.class));
        // Other 9 streams are added
        verify(jetStreamManagement, times(9)).addStream(any(StreamConfiguration.class));
    }

    @Test
    void should_handleGracefulFailure_when_jetStreamNotSupported() throws Exception {
        // Simulate NATS not supporting JetStream
        when(natsConnection.jetStreamManagement()).thenThrow(new RuntimeException("JetStream not enabled"));

        JetStreamConfig config = new JetStreamConfig(natsConnection);

        // Should not throw
        assertDoesNotThrow(config::createStreams);
    }

    @Test
    void should_continueCreatingStreams_when_oneStreamFails() throws Exception {
        // First stream creation fails, rest succeed
        when(jetStreamManagement.getStreamInfo(anyString())).thenThrow(new RuntimeException("not found"));
        when(jetStreamManagement.addStream(any(StreamConfiguration.class)))
                .thenThrow(new RuntimeException("creation failed"))  // First call fails
                .thenReturn(streamInfo);  // Rest succeed

        assertDoesNotThrow(() -> jetStreamConfig.createStreams());

        // All 10 stream creations attempted
        verify(jetStreamManagement, times(10)).addStream(any(StreamConfiguration.class));
    }

    @Test
    void should_createStreamWithCorrectConfig_when_createOrUpdateStreamCalled() throws Exception {
        when(jetStreamManagement.getStreamInfo("TASKS")).thenThrow(new RuntimeException("not found"));
        when(jetStreamManagement.addStream(any(StreamConfiguration.class))).thenReturn(streamInfo);

        jetStreamConfig.createOrUpdateStream(jetStreamManagement, "TASKS", "squadron.tasks.>", Duration.ofDays(7));

        ArgumentCaptor<StreamConfiguration> captor = ArgumentCaptor.forClass(StreamConfiguration.class);
        verify(jetStreamManagement).addStream(captor.capture());

        StreamConfiguration config = captor.getValue();
        assertEquals("TASKS", config.getName());
        assertTrue(config.getSubjects().contains("squadron.tasks.>"));
        assertEquals(Duration.ofDays(7), config.getMaxAge());
    }
}
