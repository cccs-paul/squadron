package com.squadron.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.agent.config.SecurityConfig;
import com.squadron.agent.dto.ChatRequest;
import com.squadron.agent.dto.ChatResponse;
import com.squadron.agent.dto.StreamChunk;
import com.squadron.agent.entity.Conversation;
import com.squadron.agent.entity.ConversationMessage;
import com.squadron.agent.service.AgentService;
import com.squadron.agent.service.ConversationService;
import com.squadron.common.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AgentChatController.class)
@ContextConfiguration(classes = {AgentChatController.class, SecurityConfig.class})
@TestPropertySource(properties = {
    "squadron.security.jwt.jwks-uri=http://localhost:8081/api/auth/jwks"
})
class AgentChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AgentService agentService;

    @MockBean
    private ConversationService conversationService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        TenantContext.setContext(TenantContext.builder()
                .tenantId(tenantId)
                .userId(userId)
                .build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_chat_when_validRequest() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatRequest request = ChatRequest.builder()
                .taskId(taskId)
                .agentType("planner")
                .message("Create a plan for this task")
                .build();

        ChatResponse response = ChatResponse.builder()
                .conversationId(conversationId)
                .messageId(messageId)
                .role("assistant")
                .content("Here is the plan...")
                .tokenCount(100)
                .status("ACTIVE")
                .build();

        when(agentService.chat(any(ChatRequest.class), any(UUID.class), any(UUID.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/agents/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.conversationId").value(conversationId.toString()))
                .andExpect(jsonPath("$.data.messageId").value(messageId.toString()))
                .andExpect(jsonPath("$.data.role").value("assistant"))
                .andExpect(jsonPath("$.data.content").value("Here is the plan..."))
                .andExpect(jsonPath("$.data.tokenCount").value(100))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(agentService).chat(any(ChatRequest.class), any(UUID.class), any(UUID.class));
    }

    @Test
    @WithMockUser(roles = {"squadron-admin"})
    void should_chat_when_adminRole() throws Exception {
        UUID taskId = UUID.randomUUID();

        ChatRequest request = ChatRequest.builder()
                .taskId(taskId)
                .agentType("coder")
                .message("Implement this feature")
                .build();

        ChatResponse response = ChatResponse.builder()
                .conversationId(UUID.randomUUID())
                .messageId(UUID.randomUUID())
                .role("assistant")
                .content("Done")
                .tokenCount(50)
                .status("ACTIVE")
                .build();

        when(agentService.chat(any(ChatRequest.class), any(UUID.class), any(UUID.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/agents/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = {"team-lead"})
    void should_chat_when_teamLeadRole() throws Exception {
        UUID taskId = UUID.randomUUID();

        ChatRequest request = ChatRequest.builder()
                .taskId(taskId)
                .agentType("reviewer")
                .message("Review this code")
                .build();

        ChatResponse response = ChatResponse.builder()
                .conversationId(UUID.randomUUID())
                .messageId(UUID.randomUUID())
                .role("assistant")
                .content("Review complete")
                .tokenCount(75)
                .status("ACTIVE")
                .build();

        when(agentService.chat(any(ChatRequest.class), any(UUID.class), any(UUID.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/agents/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_getConversation_when_exists() throws Exception {
        UUID conversationId = UUID.randomUUID();

        Conversation conversation = Conversation.builder()
                .id(conversationId)
                .tenantId(tenantId)
                .taskId(UUID.randomUUID())
                .userId(userId)
                .agentType("planner")
                .status("ACTIVE")
                .totalTokens(500L)
                .build();

        when(conversationService.getConversation(conversationId)).thenReturn(conversation);

        mockMvc.perform(get("/api/agents/chat/conversation/{conversationId}", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(conversationId.toString()))
                .andExpect(jsonPath("$.data.agentType").value("planner"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(conversationService).getConversation(conversationId);
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_getConversationMessages_when_exists() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID messageId1 = UUID.randomUUID();
        UUID messageId2 = UUID.randomUUID();

        List<ConversationMessage> messages = List.of(
                ConversationMessage.builder()
                        .id(messageId1)
                        .conversationId(conversationId)
                        .role("user")
                        .content("Hello")
                        .tokenCount(10)
                        .build(),
                ConversationMessage.builder()
                        .id(messageId2)
                        .conversationId(conversationId)
                        .role("assistant")
                        .content("Hi there")
                        .tokenCount(15)
                        .build()
        );

        when(conversationService.getConversationMessages(conversationId)).thenReturn(messages);

        mockMvc.perform(get("/api/agents/chat/conversation/{conversationId}/messages", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].role").value("user"))
                .andExpect(jsonPath("$.data[0].content").value("Hello"))
                .andExpect(jsonPath("$.data[1].role").value("assistant"))
                .andExpect(jsonPath("$.data[1].content").value("Hi there"));

        verify(conversationService).getConversationMessages(conversationId);
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_getConversationsByTask_when_exists() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID conversationId1 = UUID.randomUUID();
        UUID conversationId2 = UUID.randomUUID();

        List<Conversation> conversations = List.of(
                Conversation.builder()
                        .id(conversationId1)
                        .tenantId(tenantId)
                        .taskId(taskId)
                        .userId(userId)
                        .agentType("planner")
                        .status("CLOSED")
                        .totalTokens(200L)
                        .build(),
                Conversation.builder()
                        .id(conversationId2)
                        .tenantId(tenantId)
                        .taskId(taskId)
                        .userId(userId)
                        .agentType("coder")
                        .status("ACTIVE")
                        .totalTokens(300L)
                        .build()
        );

        when(conversationService.getConversationsByTask(taskId)).thenReturn(conversations);

        mockMvc.perform(get("/api/agents/chat/task/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(conversationId1.toString()))
                .andExpect(jsonPath("$.data[0].agentType").value("planner"))
                .andExpect(jsonPath("$.data[1].id").value(conversationId2.toString()))
                .andExpect(jsonPath("$.data[1].agentType").value("coder"));

        verify(conversationService).getConversationsByTask(taskId);
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_closeConversation_when_active() throws Exception {
        UUID conversationId = UUID.randomUUID();

        Conversation closedConversation = Conversation.builder()
                .id(conversationId)
                .tenantId(tenantId)
                .taskId(UUID.randomUUID())
                .userId(userId)
                .agentType("planner")
                .status("CLOSED")
                .totalTokens(500L)
                .build();

        when(conversationService.closeConversation(conversationId)).thenReturn(closedConversation);

        mockMvc.perform(post("/api/agents/chat/conversation/{conversationId}/close", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(conversationId.toString()))
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        verify(conversationService).closeConversation(conversationId);
    }

    @Test
    void should_return401_when_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/agents/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":\"" + UUID.randomUUID() + "\",\"agentType\":\"planner\",\"message\":\"test\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_return401_when_gettingConversationUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/agents/chat/conversation/{conversationId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = {"viewer"})
    void should_return403_when_insufficientRole() throws Exception {
        ChatRequest request = ChatRequest.builder()
                .taskId(UUID.randomUUID())
                .agentType("planner")
                .message("test")
                .build();

        mockMvc.perform(post("/api/agents/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"developer"})
    void should_chatStream_returnSSE() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatRequest request = ChatRequest.builder()
                .taskId(taskId)
                .agentType("CODING")
                .message("Implement the feature")
                .build();

        StreamChunk chunkData = StreamChunk.builder()
                .conversationId(conversationId)
                .content("Hello")
                .type("chunk")
                .build();

        StreamChunk doneData = StreamChunk.builder()
                .conversationId(conversationId)
                .messageId(messageId)
                .type("done")
                .tokenCount(1)
                .build();

        Flux<ServerSentEvent<StreamChunk>> sseFlux = Flux.just(
                ServerSentEvent.<StreamChunk>builder().data(chunkData).build(),
                ServerSentEvent.<StreamChunk>builder().data(doneData).build()
        );

        when(agentService.chatStream(any(ChatRequest.class), any(UUID.class), any(UUID.class)))
                .thenReturn(sseFlux);

        MvcResult mvcResult = mockMvc.perform(post("/api/agents/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk());

        verify(agentService).chatStream(any(ChatRequest.class), any(UUID.class), any(UUID.class));
    }
}
