package com.squadron.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ChatModelConfigTest {

    @Test
    void should_returnOpenAiChatModel_asPrimary() {
        ChatModelConfig config = new ChatModelConfig();
        OpenAiChatModel openAiChatModel = mock(OpenAiChatModel.class);

        ChatModel result = config.primaryChatModel(openAiChatModel);

        assertThat(result).isSameAs(openAiChatModel);
    }

    @Test
    void should_returnChatModelInstance() {
        ChatModelConfig config = new ChatModelConfig();
        OpenAiChatModel openAiChatModel = mock(OpenAiChatModel.class);

        ChatModel result = config.primaryChatModel(openAiChatModel);

        assertThat(result).isInstanceOf(ChatModel.class);
    }
}
