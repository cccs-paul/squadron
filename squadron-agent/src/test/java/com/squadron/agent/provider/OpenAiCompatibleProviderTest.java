package com.squadron.agent.provider;

import com.squadron.agent.dto.AgentConfigDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.ChatClient.StreamResponseSpec;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiCompatibleProviderTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClientRequestSpec requestSpec;

    @Mock
    private CallResponseSpec callResponseSpec;

    private OpenAiCompatibleProvider provider;

    @BeforeEach
    void setUp() {
        provider = new OpenAiCompatibleProvider(chatClientBuilder);
    }

    @Test
    void should_returnProviderName_when_getProviderNameCalled() {
        assertEquals("openai-compatible", provider.getProviderName());
    }

    @Test
    void should_returnResponse_when_chatCalled() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
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

    @Test
    void should_handleEmptyHistory_when_chatCalled() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Response with no history");

        AgentConfigDto config = AgentConfigDto.builder().build();

        String result = provider.chat("System prompt", List.of(), "Hello!", config);

        assertEquals("Response with no history", result);
    }

    @Test
    void should_handleNullHistory_when_chatCalled() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Response with null history");

        AgentConfigDto config = AgentConfigDto.builder().build();

        String result = provider.chat("System prompt", null, "Hello!", config);

        assertEquals("Response with null history", result);
    }

    @Test
    void should_handleNullSystemPrompt_when_chatCalled() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("No system prompt");

        AgentConfigDto config = AgentConfigDto.builder().build();

        String result = provider.chat(null, List.of(), "Hello!", config);

        assertEquals("No system prompt", result);
    }

    @Test
    void should_handleBlankSystemPrompt_when_chatCalled() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Blank system prompt");

        AgentConfigDto config = AgentConfigDto.builder().build();

        String result = provider.chat("   ", List.of(), "Hello!", config);

        assertEquals("Blank system prompt", result);
    }

    @Test
    void should_handleMixedRolesInHistory_when_chatCalled() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
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
    }

    @Test
    void should_returnStreamFlux_when_chatStreamCalled() {
        StreamResponseSpec streamResponseSpec = mock(StreamResponseSpec.class);

        when(chatClientBuilder.build()).thenReturn(chatClient);
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
