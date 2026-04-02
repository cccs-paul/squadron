package com.squadron.platform.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebClientSslHelperTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient.Builder clonedBuilder;

    @Test
    void should_returnTrustedBuilder() {
        when(webClientBuilder.clone()).thenReturn(clonedBuilder);
        when(clonedBuilder.clientConnector(any())).thenReturn(clonedBuilder);

        WebClientSslHelper helper = new WebClientSslHelper(webClientBuilder);
        WebClient.Builder result = helper.trustedBuilder();

        assertNotNull(result);
        verify(webClientBuilder).clone();
        verify(clonedBuilder).clientConnector(any());
    }
}
