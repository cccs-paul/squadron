package com.squadron.agent.provider;

import com.squadron.agent.dto.AgentConfigDto;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentProviderTest {

    @Test
    void should_declareGetProviderNameMethod_when_interfaceInspected() throws NoSuchMethodException {
        Method method = AgentProvider.class.getMethod("getProviderName");
        assertEquals(String.class, method.getReturnType());
        assertEquals(0, method.getParameterCount());
    }

    @Test
    void should_declareChatMethod_when_interfaceInspected() throws NoSuchMethodException {
        Method method = AgentProvider.class.getMethod("chat", String.class, List.class, String.class, AgentConfigDto.class);
        assertEquals(String.class, method.getReturnType());
        assertEquals(4, method.getParameterCount());
    }

    @Test
    void should_declareChatStreamMethod_when_interfaceInspected() throws NoSuchMethodException {
        Method method = AgentProvider.class.getMethod("chatStream", String.class, List.class, String.class, AgentConfigDto.class);
        assertEquals(Flux.class, method.getReturnType());
        assertEquals(4, method.getParameterCount());
    }

    @Test
    void should_beInterface_when_classInspected() {
        assertTrue(AgentProvider.class.isInterface());
    }

    @Test
    void should_haveThreeMethods_when_interfaceInspected() {
        Method[] methods = AgentProvider.class.getDeclaredMethods();
        assertEquals(3, methods.length);
    }

    @Test
    void should_implementAllMethods_when_concreteImplementation() {
        AgentProvider provider = new AgentProvider() {
            @Override
            public String getProviderName() {
                return "test-provider";
            }

            @Override
            public String chat(String systemPrompt, List<ChatMessage> history, String userMessage, AgentConfigDto config) {
                return "response";
            }

            @Override
            public Flux<String> chatStream(String systemPrompt, List<ChatMessage> history, String userMessage, AgentConfigDto config) {
                return Flux.just("chunk1", "chunk2");
            }
        };

        assertEquals("test-provider", provider.getProviderName());
        assertEquals("response", provider.chat("prompt", List.of(), "message", null));
        assertNotNull(provider.chatStream("prompt", List.of(), "message", null));
    }
}
