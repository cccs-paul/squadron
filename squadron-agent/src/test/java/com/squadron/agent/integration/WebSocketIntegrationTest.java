package com.squadron.agent.integration;

import com.squadron.agent.config.WebSocketConfig;
import com.squadron.agent.controller.AgentWebSocketController;
import com.squadron.agent.dto.ChatRequest;
import com.squadron.agent.dto.StreamChunk;
import com.squadron.agent.entity.Conversation;
import com.squadron.agent.entity.ConversationMessage;
import com.squadron.agent.provider.AgentProvider;
import com.squadron.agent.provider.AgentProviderRegistry;
import com.squadron.agent.service.ConversationService;
import com.squadron.agent.service.SquadronConfigService;
import com.squadron.agent.service.SystemPromptBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * WebSocket integration test for the agent chat endpoint.
 * <p>
 * Starts the full Spring Boot application with a random port, connects a
 * STOMP WebSocket client to {@code /ws/agent}, sends chat messages to
 * {@code /app/chat}, and verifies streaming response chunks arrive on
 * {@code /topic/chat/{conversationId}}.
 * <p>
 * Service dependencies (ConversationService, AgentProviderRegistry, etc.)
 * are mocked to isolate the WebSocket transport layer.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.allow-bean-definition-overriding=true",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false",
                "spring.datasource.url=jdbc:h2:mem:ws_test;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.ai.openai.api-key=test-key",
                "spring.ai.ollama.base-url=http://localhost:11434",
                "spring.ai.ollama.init.timeout=0s",
                "spring.ai.ollama.init.pull-model-strategy=never"
        }
)
@Testcontainers
class WebSocketIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> natsContainer = new GenericContainer<>("nats:latest")
            .withExposedPorts(4222)
            .withCommand("-js");

    @LocalServerPort
    private int port;

    @MockBean
    private ConversationService conversationService;

    @MockBean
    private SquadronConfigService configService;

    @MockBean
    private AgentProviderRegistry providerRegistry;

    @MockBean
    private SystemPromptBuilder promptBuilder;

    @MockBean
    private AgentProvider agentProvider;

    private WebSocketStompClient stompClient;
    private StompSession stompSession;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String natsUrl = "nats://" + natsContainer.getHost() + ":" + natsContainer.getMappedPort(4222);
        registry.add("squadron.nats.url", () -> natsUrl);
        registry.add("squadron.security.jwt.jwks-uri",
                () -> "http://localhost:19999/api/auth/jwks");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:19999/realms/test/protocol/openid-connect/certs");
    }

    @BeforeEach
    void setUp() throws Exception {
        List<Transport> transports = new ArrayList<>();
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));

        SockJsClient sockJsClient = new SockJsClient(transports);
        stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        String url = "ws://localhost:" + port + "/ws/agent";
        CompletableFuture<StompSession> future = stompClient.connectAsync(
                url, new StompSessionHandlerAdapter() {
                    @Override
                    public void handleException(StompSession session, StompCommand command,
                                                StompHeaders headers, byte[] payload,
                                                Throwable exception) {
                        // Log but don't fail - errors are tested explicitly
                    }

                    @Override
                    public void handleTransportError(StompSession session, Throwable exception) {
                        // Log but don't fail
                    }
                });
        stompSession = future.get(10, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() {
        if (stompSession != null && stompSession.isConnected()) {
            stompSession.disconnect();
        }
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    // ========================================================================
    // Connection test
    // ========================================================================

    @Test
    void should_connectToWebSocketEndpoint() {
        assertThat(stompSession).isNotNull();
        assertThat(stompSession.isConnected()).isTrue();
    }

    // ========================================================================
    // Chat message and response streaming
    // ========================================================================

    @Test
    void should_receiveStreamedChunks_whenSendingChatMessage() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        // Mock conversation service
        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .tenantId(taskId)
                .taskId(taskId)
                .userId(UUID.randomUUID())
                .agentType("CODING")
                .build();
        when(conversationService.startConversation(any(), eq(taskId), any(), eq("CODING")))
                .thenReturn(conversation);
        when(conversationService.getConversationMessages(conversationId))
                .thenReturn(Collections.emptyList());
        when(conversationService.addMessage(eq(conversationId), eq("USER"), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());
        when(conversationService.addMessage(eq(conversationId), eq("ASSISTANT"), anyString(), any(Integer.class)))
                .thenReturn(ConversationMessage.builder().id(messageId).build());

        // Mock config
        when(configService.resolveAgentConfig(any(), any(), any(), eq("CODING")))
                .thenReturn(null);
        when(promptBuilder.buildCodingPrompt(any(), any()))
                .thenReturn("You are a coding assistant.");

        // Mock provider to stream response chunks
        when(providerRegistry.getProvider(any())).thenReturn(agentProvider);
        when(agentProvider.chatStream(anyString(), any(), anyString(), any()))
                .thenReturn(Flux.just("Hello", " World", "!"));

        // Subscribe to the response topic
        CopyOnWriteArrayList<StreamChunk> receivedChunks = new CopyOnWriteArrayList<>();
        CountDownLatch doneLatch = new CountDownLatch(1);

        stompSession.subscribe("/topic/chat/" + conversationId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return StreamChunk.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                StreamChunk chunk = (StreamChunk) payload;
                receivedChunks.add(chunk);
                if ("done".equals(chunk.getType())) {
                    doneLatch.countDown();
                }
            }
        });

        // Small delay to ensure subscription is established
        Thread.sleep(500);

        // Send chat message
        ChatRequest chatRequest = ChatRequest.builder()
                .taskId(taskId)
                .agentType("CODING")
                .message("Write a hello world function")
                .build();

        stompSession.send("/app/chat", chatRequest);

        // Wait for the "done" chunk
        boolean received = doneLatch.await(10, TimeUnit.SECONDS);
        assertThat(received)
                .as("Expected to receive 'done' chunk within timeout")
                .isTrue();

        // Verify we received streaming chunks + done
        assertThat(receivedChunks).isNotEmpty();

        // Check that chunk types include at least "chunk" and "done"
        List<String> chunkTypes = receivedChunks.stream()
                .map(StreamChunk::getType)
                .toList();
        assertThat(chunkTypes).contains("chunk", "done");

        // Verify the done chunk has conversation ID
        StreamChunk doneChunk = receivedChunks.stream()
                .filter(c -> "done".equals(c.getType()))
                .findFirst()
                .orElse(null);
        assertThat(doneChunk).isNotNull();
        assertThat(doneChunk.getConversationId()).isEqualTo(conversationId);
    }

    // ========================================================================
    // Error handling - provider failure
    // ========================================================================

    @Test
    void should_receiveErrorChunk_whenProviderFails() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .tenantId(taskId)
                .taskId(taskId)
                .userId(UUID.randomUUID())
                .agentType("PLANNING")
                .build();
        when(conversationService.startConversation(any(), eq(taskId), any(), eq("PLANNING")))
                .thenReturn(conversation);
        when(conversationService.getConversationMessages(conversationId))
                .thenReturn(Collections.emptyList());
        when(conversationService.addMessage(eq(conversationId), eq("USER"), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());

        when(configService.resolveAgentConfig(any(), any(), any(), eq("PLANNING")))
                .thenReturn(null);
        when(promptBuilder.buildPlanningPrompt(any(), any()))
                .thenReturn("You are a planning assistant.");

        // Mock provider to emit an error
        when(providerRegistry.getProvider(any())).thenReturn(agentProvider);
        when(agentProvider.chatStream(anyString(), any(), anyString(), any()))
                .thenReturn(Flux.error(new RuntimeException("Provider unavailable")));

        CopyOnWriteArrayList<StreamChunk> receivedChunks = new CopyOnWriteArrayList<>();
        CountDownLatch errorLatch = new CountDownLatch(1);

        stompSession.subscribe("/topic/chat/" + conversationId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return StreamChunk.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                StreamChunk chunk = (StreamChunk) payload;
                receivedChunks.add(chunk);
                if ("error".equals(chunk.getType())) {
                    errorLatch.countDown();
                }
            }
        });

        Thread.sleep(500);

        ChatRequest chatRequest = ChatRequest.builder()
                .taskId(taskId)
                .agentType("PLANNING")
                .message("Create a plan")
                .build();
        stompSession.send("/app/chat", chatRequest);

        boolean received = errorLatch.await(10, TimeUnit.SECONDS);
        assertThat(received)
                .as("Expected to receive 'error' chunk within timeout")
                .isTrue();

        StreamChunk errorChunk = receivedChunks.stream()
                .filter(c -> "error".equals(c.getType()))
                .findFirst()
                .orElse(null);
        assertThat(errorChunk).isNotNull();
        assertThat(errorChunk.getConversationId()).isEqualTo(conversationId);
        assertThat(errorChunk.getContent()).contains("Provider unavailable");
    }

    // ========================================================================
    // Continuing existing conversation
    // ========================================================================

    @Test
    void should_continueExistingConversation_whenConversationIdProvided() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .tenantId(taskId)
                .taskId(taskId)
                .userId(UUID.randomUUID())
                .agentType("CODING")
                .build();
        when(conversationService.getConversation(conversationId))
                .thenReturn(conversation);
        when(conversationService.getConversationMessages(conversationId))
                .thenReturn(Collections.emptyList());
        when(conversationService.addMessage(eq(conversationId), eq("USER"), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());
        when(conversationService.addMessage(eq(conversationId), eq("ASSISTANT"), anyString(), any(Integer.class)))
                .thenReturn(ConversationMessage.builder().id(messageId).build());

        when(configService.resolveAgentConfig(any(), any(), any(), eq("CODING")))
                .thenReturn(null);
        when(promptBuilder.buildCodingPrompt(any(), any()))
                .thenReturn("You are a coding assistant.");
        when(providerRegistry.getProvider(any())).thenReturn(agentProvider);
        when(agentProvider.chatStream(anyString(), any(), anyString(), any()))
                .thenReturn(Flux.just("Continued response"));

        CopyOnWriteArrayList<StreamChunk> receivedChunks = new CopyOnWriteArrayList<>();
        CountDownLatch doneLatch = new CountDownLatch(1);

        stompSession.subscribe("/topic/chat/" + conversationId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return StreamChunk.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                StreamChunk chunk = (StreamChunk) payload;
                receivedChunks.add(chunk);
                if ("done".equals(chunk.getType())) {
                    doneLatch.countDown();
                }
            }
        });

        Thread.sleep(500);

        ChatRequest chatRequest = ChatRequest.builder()
                .conversationId(conversationId) // Continuing existing conversation
                .taskId(taskId)
                .agentType("CODING")
                .message("Continue the implementation")
                .build();
        stompSession.send("/app/chat", chatRequest);

        boolean received = doneLatch.await(10, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(receivedChunks).isNotEmpty();
    }
}
