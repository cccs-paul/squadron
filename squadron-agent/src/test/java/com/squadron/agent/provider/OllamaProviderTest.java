package com.squadron.agent.provider;

import com.squadron.agent.dto.AgentConfigDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.ChatClient.StreamResponseSpec;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OllamaProviderTest {

    @Mock
    private OllamaChatModel ollamaChatModel;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClientRequestSpec requestSpec;

    @Mock
    private CallResponseSpec callResponseSpec;

    private OllamaProvider provider;

    @BeforeEach
    void setUp() {
        provider = new OllamaProvider(ollamaChatModel);
    }

    // ---- Helper to set up the static mock for ChatClient.builder() ----

    private MockedStatic<ChatClient> mockChatClientStatic() {
        MockedStatic<ChatClient> chatClientStatic = mockStatic(ChatClient.class);
        ChatClient.Builder mockBuilder = mock(ChatClient.Builder.class);
        chatClientStatic.when(() -> ChatClient.builder(ollamaChatModel)).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(chatClient);
        return chatClientStatic;
    }

    // ---- Tests ----

    @Test
    void should_returnProviderName_when_getProviderNameCalled() {
        assertEquals("ollama", provider.getProviderName());
    }

    @Test
    void should_returnResponse_when_chatCalled() {
        try (MockedStatic<ChatClient> ignored = mockChatClientStatic()) {
            when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.content()).thenReturn("AI response");

            AgentConfigDto config = AgentConfigDto.builder().build();
            List<ChatMessage> history = List.of(
                    ChatMessage.builder().role("USER").content("previous message").build()
            );

            String result = provider.chat("System prompt", history, "Hello!", config);

            assertEquals("AI response", result);
        }
    }

    @Test
    void should_handleEmptyHistory_when_chatCalled() {
        try (MockedStatic<ChatClient> ignored = mockChatClientStatic()) {
            when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.content()).thenReturn("Response with no history");

            AgentConfigDto config = AgentConfigDto.builder().build();

            String result = provider.chat("System prompt", List.of(), "Hello!", config);

            assertEquals("Response with no history", result);

            // Verify prompt contains system message + user message (no history messages)
            ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
            verify(chatClient).prompt(promptCaptor.capture());
            List<Message> messages = promptCaptor.getValue().getInstructions();
            assertEquals(2, messages.size());
            assertInstanceOf(SystemMessage.class, messages.get(0));
            assertInstanceOf(UserMessage.class, messages.get(1));
        }
    }

    @Test
    void should_handleNullHistory_when_chatCalled() {
        try (MockedStatic<ChatClient> ignored = mockChatClientStatic()) {
            when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.content()).thenReturn("Response with null history");

            AgentConfigDto config = AgentConfigDto.builder().build();

            String result = provider.chat("System prompt", null, "Hello!", config);

            assertEquals("Response with null history", result);

            // Verify prompt contains system message + user message only
            ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
            verify(chatClient).prompt(promptCaptor.capture());
            List<Message> messages = promptCaptor.getValue().getInstructions();
            assertEquals(2, messages.size());
            assertInstanceOf(SystemMessage.class, messages.get(0));
            assertInstanceOf(UserMessage.class, messages.get(1));
        }
    }

    @Test
    void should_handleNullSystemPrompt_when_chatCalled() {
        try (MockedStatic<ChatClient> ignored = mockChatClientStatic()) {
            when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.content()).thenReturn("No system prompt");

            AgentConfigDto config = AgentConfigDto.builder().build();

            String result = provider.chat(null, List.of(), "Hello!", config);

            assertEquals("No system prompt", result);

            // Verify prompt contains only the user message (no system message)
            ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
            verify(chatClient).prompt(promptCaptor.capture());
            List<Message> messages = promptCaptor.getValue().getInstructions();
            assertEquals(1, messages.size());
            assertInstanceOf(UserMessage.class, messages.get(0));
        }
    }

    @Test
    void should_handleBlankSystemPrompt_when_chatCalled() {
        try (MockedStatic<ChatClient> ignored = mockChatClientStatic()) {
            when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.content()).thenReturn("Blank system prompt");

            AgentConfigDto config = AgentConfigDto.builder().build();

            String result = provider.chat("   ", List.of(), "Hello!", config);

            assertEquals("Blank system prompt", result);

            // Verify prompt contains only the user message (blank system prompt is skipped)
            ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
            verify(chatClient).prompt(promptCaptor.capture());
            List<Message> messages = promptCaptor.getValue().getInstructions();
            assertEquals(1, messages.size());
            assertInstanceOf(UserMessage.class, messages.get(0));
        }
    }

    @Test
    void should_handleMixedRolesInHistory_when_chatCalled() {
        try (MockedStatic<ChatClient> ignored = mockChatClientStatic()) {
            when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.content()).thenReturn("Mixed roles response");

            AgentConfigDto config = AgentConfigDto.builder().build();
            List<ChatMessage> history = List.of(
                    ChatMessage.builder().role("USER").content("user msg").build(),
                    ChatMessage.builder().role("ASSISTANT").content("asst msg").build(),
                    ChatMessage.builder().role("SYSTEM").content("sys msg").build(),
                    ChatMessage.builder().role("OTHER").content("other msg").build()
            );

            String result = provider.chat("System prompt", history, "Hello!", config);

            assertEquals("Mixed roles response", result);

            // Verify message types: system + USER + ASSISTANT + SYSTEM + OTHER(->User) + final user
            ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
            verify(chatClient).prompt(promptCaptor.capture());
            List<Message> messages = promptCaptor.getValue().getInstructions();
            assertEquals(6, messages.size());
            assertInstanceOf(SystemMessage.class, messages.get(0));  // system prompt
            assertInstanceOf(UserMessage.class, messages.get(1));     // USER history
            assertInstanceOf(AssistantMessage.class, messages.get(2)); // ASSISTANT history
            assertInstanceOf(SystemMessage.class, messages.get(3));   // SYSTEM history
            assertInstanceOf(UserMessage.class, messages.get(4));     // OTHER -> UserMessage
            assertInstanceOf(UserMessage.class, messages.get(5));     // final user message
        }
    }

    @Test
    void should_returnStreamFlux_when_chatStreamCalled() {
        try (MockedStatic<ChatClient> ignored = mockChatClientStatic()) {
            StreamResponseSpec streamResponseSpec = mock(StreamResponseSpec.class);

            when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
            when(requestSpec.stream()).thenReturn(streamResponseSpec);
            when(streamResponseSpec.content()).thenReturn(Flux.just("chunk1", "chunk2"));

            AgentConfigDto config = AgentConfigDto.builder().build();

            Flux<String> result = provider.chatStream("System prompt", List.of(), "Hello!", config);

            assertNotNull(result);
            List<String> chunks = result.collectList().block();
            assertNotNull(chunks);
            assertEquals(2, chunks.size());
            assertEquals("chunk1", chunks.get(0));
            assertEquals("chunk2", chunks.get(1));
        }
    }

    @Test
    void should_applyModelOverride_when_configHasModel() {
        try (MockedStatic<ChatClient> ignored = mockChatClientStatic()) {
            when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.content()).thenReturn("model override response");

            AgentConfigDto config = AgentConfigDto.builder()
                    .model("qwen2.5-coder:7b")
                    .build();

            String result = provider.chat("System prompt", List.of(), "Hello!", config);

            assertEquals("model override response", result);

            // Verify OllamaOptions were applied with the model
            ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
            verify(chatClient).prompt(promptCaptor.capture());
            Prompt capturedPrompt = promptCaptor.getValue();
            assertNotNull(capturedPrompt.getOptions());
            assertInstanceOf(OllamaOptions.class, capturedPrompt.getOptions());
            OllamaOptions options = (OllamaOptions) capturedPrompt.getOptions();
            assertEquals("qwen2.5-coder:7b", options.getModel());
        }
    }

    @Test
    void should_applyTemperatureOverride_when_configHasTemperature() {
        try (MockedStatic<ChatClient> ignored = mockChatClientStatic()) {
            when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.content()).thenReturn("temperature override response");

            AgentConfigDto config = AgentConfigDto.builder()
                    .temperature(0.7)
                    .build();

            String result = provider.chat("System prompt", List.of(), "Hello!", config);

            assertEquals("temperature override response", result);

            ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
            verify(chatClient).prompt(promptCaptor.capture());
            Prompt capturedPrompt = promptCaptor.getValue();
            assertNotNull(capturedPrompt.getOptions());
            assertInstanceOf(OllamaOptions.class, capturedPrompt.getOptions());
            OllamaOptions options = (OllamaOptions) capturedPrompt.getOptions();
            assertEquals(0.7, options.getTemperature(), 0.001);
        }
    }

    @Test
    void should_applyMaxTokensOverride_when_configHasMaxTokens() {
        try (MockedStatic<ChatClient> ignored = mockChatClientStatic()) {
            when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.content()).thenReturn("max tokens override response");

            AgentConfigDto config = AgentConfigDto.builder()
                    .maxTokens(2048)
                    .build();

            String result = provider.chat("System prompt", List.of(), "Hello!", config);

            assertEquals("max tokens override response", result);

            ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
            verify(chatClient).prompt(promptCaptor.capture());
            Prompt capturedPrompt = promptCaptor.getValue();
            assertNotNull(capturedPrompt.getOptions());
            assertInstanceOf(OllamaOptions.class, capturedPrompt.getOptions());
            OllamaOptions options = (OllamaOptions) capturedPrompt.getOptions();
            assertEquals(2048, options.getNumPredict());
        }
    }

    @Test
    void should_applyAllOverrides_when_configHasAllOptions() {
        try (MockedStatic<ChatClient> ignored = mockChatClientStatic()) {
            when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.content()).thenReturn("all overrides response");

            AgentConfigDto config = AgentConfigDto.builder()
                    .model("deepseek-coder:6.7b")
                    .temperature(0.3)
                    .maxTokens(4096)
                    .build();

            String result = provider.chat("System prompt", List.of(), "Hello!", config);

            assertEquals("all overrides response", result);

            ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
            verify(chatClient).prompt(promptCaptor.capture());
            Prompt capturedPrompt = promptCaptor.getValue();
            assertNotNull(capturedPrompt.getOptions());
            assertInstanceOf(OllamaOptions.class, capturedPrompt.getOptions());
            OllamaOptions options = (OllamaOptions) capturedPrompt.getOptions();
            assertEquals("deepseek-coder:6.7b", options.getModel());
            assertEquals(0.3, options.getTemperature(), 0.001);
            assertEquals(4096, options.getNumPredict());
        }
    }

    @Test
    void should_useDefaultPrompt_when_configIsNull() {
        try (MockedStatic<ChatClient> ignored = mockChatClientStatic()) {
            when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.content()).thenReturn("null config response");

            String result = provider.chat("System prompt", List.of(), "Hello!", null);

            assertEquals("null config response", result);

            // Verify no ChatOptions are set on the prompt
            ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
            verify(chatClient).prompt(promptCaptor.capture());
            Prompt capturedPrompt = promptCaptor.getValue();
            assertNull(capturedPrompt.getOptions());
        }
    }

    @Test
    void should_useDefaultPrompt_when_configHasNoOverrides() {
        try (MockedStatic<ChatClient> ignored = mockChatClientStatic()) {
            when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
            when(requestSpec.call()).thenReturn(callResponseSpec);
            when(callResponseSpec.content()).thenReturn("no overrides response");

            AgentConfigDto config = AgentConfigDto.builder()
                    .provider("ollama")
                    .systemPromptOverride("some override")
                    .build();

            String result = provider.chat("System prompt", List.of(), "Hello!", config);

            assertEquals("no overrides response", result);

            // model, temperature, maxTokens are all null -> no ChatOptions
            ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
            verify(chatClient).prompt(promptCaptor.capture());
            Prompt capturedPrompt = promptCaptor.getValue();
            assertNull(capturedPrompt.getOptions());
        }
    }
}
