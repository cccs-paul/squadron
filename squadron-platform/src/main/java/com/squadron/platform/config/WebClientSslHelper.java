package com.squadron.platform.config;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;

/**
 * Utility that produces WebClient instances which trust all TLS certificates.
 * This is necessary for connecting to on-premises servers that use internal CA
 * certificates not present in the JDK default truststore.
 *
 * WARNING: This disables certificate verification. In a production environment,
 * consider importing the specific CA certificates into the JVM truststore instead.
 */
@Component
public class WebClientSslHelper {

    private static final Logger log = LoggerFactory.getLogger(WebClientSslHelper.class);

    private final WebClient.Builder webClientBuilder;

    public WebClientSslHelper(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    /**
     * Creates a new WebClient.Builder that trusts all SSL/TLS certificates.
     * Callers can further customize the builder (set base URL, headers, etc.)
     * before calling .build().
     */
    public WebClient.Builder trustedBuilder() {
        try {
            SslContext sslContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();

            HttpClient httpClient = HttpClient.create()
                    .secure(spec -> spec.sslContext(sslContext));

            return webClientBuilder.clone()
                    .clientConnector(new ReactorClientHttpConnector(httpClient));
        } catch (SSLException e) {
            log.warn("Failed to create SSL-trusting WebClient, falling back to default: {}", e.getMessage());
            return webClientBuilder.clone();
        }
    }
}
