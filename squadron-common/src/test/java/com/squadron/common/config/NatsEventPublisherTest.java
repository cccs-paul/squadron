package com.squadron.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.event.SquadronEvent;
import com.squadron.common.event.TaskStateChangedEvent;
import io.nats.client.Connection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NatsEventPublisherTest {

    @Mock
    private Connection natsConnection;

    @Mock
    private ObjectMapper objectMapper;

    private NatsEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new NatsEventPublisher(natsConnection, objectMapper);
    }

    @Test
    void should_publishEvent_when_validEventProvided() throws Exception {
        SquadronEvent event = new SquadronEvent();
        event.setEventType("TEST_EVENT");
        byte[] serialized = "{\"eventType\":\"TEST_EVENT\"}".getBytes();
        when(objectMapper.writeValueAsBytes(event)).thenReturn(serialized);

        publisher.publish("squadron.events.test", event);

        verify(natsConnection).publish(eq("squadron.events.test"), eq(serialized));
    }

    @Test
    void should_serializeEventToBytes_when_publishing() throws Exception {
        TaskStateChangedEvent event = new TaskStateChangedEvent();
        byte[] serialized = "{}".getBytes();
        when(objectMapper.writeValueAsBytes(event)).thenReturn(serialized);

        publisher.publish("squadron.events.task", event);

        verify(objectMapper).writeValueAsBytes(event);
    }

    @Test
    void should_throwRuntimeException_when_serializationFails() throws Exception {
        SquadronEvent event = new SquadronEvent();
        event.setEventType("BAD_EVENT");
        when(objectMapper.writeValueAsBytes(event)).thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("fail") {});

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> publisher.publish("squadron.events.test", event));

        assertEquals("Failed to publish event", thrown.getMessage());
    }

    @Test
    void should_wrapCause_when_publishingFails() throws Exception {
        SquadronEvent event = new SquadronEvent();
        event.setEventType("FAIL_EVENT");
        Exception cause = new com.fasterxml.jackson.core.JsonProcessingException("serialize error") {};
        when(objectMapper.writeValueAsBytes(event)).thenThrow(cause);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> publisher.publish("squadron.events.test", event));

        assertSame(cause, thrown.getCause());
    }

    @Test
    void should_returnCompletableFuture_when_publishAsyncCalled() throws Exception {
        SquadronEvent event = new SquadronEvent();
        event.setEventType("ASYNC_EVENT");
        byte[] serialized = "{}".getBytes();
        when(objectMapper.writeValueAsBytes(event)).thenReturn(serialized);

        CompletableFuture<Void> future = publisher.publishAsync("squadron.events.async", event);

        assertNotNull(future);
        future.join(); // wait for completion
        verify(natsConnection, timeout(1000)).publish(eq("squadron.events.async"), eq(serialized));
    }

    @Test
    void should_completeExceptionally_when_publishAsyncFails() throws Exception {
        SquadronEvent event = new SquadronEvent();
        event.setEventType("FAIL_ASYNC");
        when(objectMapper.writeValueAsBytes(event)).thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("fail") {});

        CompletableFuture<Void> future = publisher.publishAsync("squadron.events.fail", event);

        assertThrows(Exception.class, future::join);
    }

    @Test
    void should_useCorrectSubject_when_publishing() throws Exception {
        SquadronEvent event = new SquadronEvent();
        event.setEventType("TEST");
        byte[] serialized = "{}".getBytes();
        when(objectMapper.writeValueAsBytes(event)).thenReturn(serialized);

        publisher.publish("custom.subject.name", event);

        verify(natsConnection).publish(eq("custom.subject.name"), any(byte[].class));
    }
}
