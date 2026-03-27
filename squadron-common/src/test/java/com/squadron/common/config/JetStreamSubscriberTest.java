package com.squadron.common.config;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.PushSubscribeOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JetStreamSubscriberTest {

    @Mock
    private Connection natsConnection;

    @Mock
    private JetStream jetStream;

    @Mock
    private Dispatcher dispatcher;

    @Mock
    private JetStreamSubscription jetStreamSubscription;

    @Mock
    private Message message;

    private JetStreamSubscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber = new JetStreamSubscriber(natsConnection);
    }

    @Test
    void should_createJetStreamSubscription_when_jetStreamAvailable() throws Exception {
        subscriber.setJetStream(jetStream);
        when(natsConnection.createDispatcher()).thenReturn(dispatcher);
        when(jetStream.subscribe(anyString(), anyString(), any(Dispatcher.class),
                any(MessageHandler.class), eq(false), any(PushSubscribeOptions.class)))
                .thenReturn(jetStreamSubscription);

        Consumer<Message> handler = msg -> {};

        subscriber.subscribe("squadron.tasks.>", "task-consumer", "task-group", handler);

        verify(jetStream).subscribe(eq("squadron.tasks.>"), eq("task-group"),
                eq(dispatcher), any(MessageHandler.class), eq(false),
                any(PushSubscribeOptions.class));
    }

    @Test
    void should_fallBackToCoreNats_when_jetStreamUnavailable() {
        // No JetStream set
        when(natsConnection.createDispatcher(any(MessageHandler.class))).thenReturn(dispatcher);

        Consumer<Message> handler = msg -> {};

        subscriber.subscribe("squadron.tasks.>", "task-consumer", "task-group", handler);

        verify(dispatcher).subscribe("squadron.tasks.>", "task-group");
    }

    @Test
    void should_fallBackToCoreNats_when_jetStreamSubscriptionFails() throws Exception {
        subscriber.setJetStream(jetStream);
        when(natsConnection.createDispatcher()).thenReturn(dispatcher);
        when(jetStream.subscribe(anyString(), anyString(), any(Dispatcher.class),
                any(MessageHandler.class), eq(false), any(PushSubscribeOptions.class)))
                .thenThrow(new RuntimeException("subscribe failed"));

        // Fallback
        when(natsConnection.createDispatcher(any(MessageHandler.class))).thenReturn(dispatcher);

        Consumer<Message> handler = msg -> {};

        subscriber.subscribe("squadron.tasks.>", "task-consumer", "task-group", handler);

        // Verify fallback to core NATS
        verify(dispatcher).subscribe("squadron.tasks.>", "task-group");
    }

    @Test
    void should_subscribeWithoutQueueGroup_when_twoArgSubscribeCalled() {
        when(natsConnection.createDispatcher(any(MessageHandler.class))).thenReturn(dispatcher);

        Consumer<Message> handler = msg -> {};

        subscriber.subscribe("squadron.tasks.>", "task-consumer", handler);

        // Queue group is null - subscribe(subject, null) on dispatcher
        verify(dispatcher).subscribe(eq("squadron.tasks.>"), (String) isNull());
    }

    @Test
    void should_ackMessage_when_handlerSucceeds() throws Exception {
        subscriber.setJetStream(jetStream);
        when(natsConnection.createDispatcher()).thenReturn(dispatcher);

        // Capture the MessageHandler passed to jetStream.subscribe
        ArgumentCaptor<MessageHandler> handlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        when(jetStream.subscribe(anyString(), anyString(), any(Dispatcher.class),
                handlerCaptor.capture(), eq(false), any(PushSubscribeOptions.class)))
                .thenReturn(jetStreamSubscription);

        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        Consumer<Message> handler = msg -> handlerCalled.set(true);

        subscriber.subscribe("squadron.tasks.>", "task-consumer", "task-group", handler);

        // Invoke the captured handler
        MessageHandler capturedHandler = handlerCaptor.getValue();
        capturedHandler.onMessage(message);

        assertTrue(handlerCalled.get());
        verify(message).ack();
    }

    @Test
    void should_nakMessage_when_handlerThrows() throws Exception {
        subscriber.setJetStream(jetStream);
        when(natsConnection.createDispatcher()).thenReturn(dispatcher);

        ArgumentCaptor<MessageHandler> handlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        when(jetStream.subscribe(anyString(), anyString(), any(Dispatcher.class),
                handlerCaptor.capture(), eq(false), any(PushSubscribeOptions.class)))
                .thenReturn(jetStreamSubscription);

        Consumer<Message> handler = msg -> {
            throw new RuntimeException("processing failed");
        };

        subscriber.subscribe("squadron.tasks.>", "task-consumer", "task-group", handler);

        // Invoke the captured handler
        MessageHandler capturedHandler = handlerCaptor.getValue();
        capturedHandler.onMessage(message);

        verify(message, never()).ack();
        verify(message).nak();
    }

    @Test
    void should_handleExceptionInCoreNatsHandler_when_handlerThrows() {
        when(natsConnection.createDispatcher(any(MessageHandler.class))).thenReturn(dispatcher);

        Consumer<Message> handler = msg -> {
            throw new RuntimeException("processing failed");
        };

        // Should not throw during subscription setup
        assertDoesNotThrow(() ->
                subscriber.subscribe("squadron.tasks.>", "task-consumer", "task-group", handler));
    }
}
