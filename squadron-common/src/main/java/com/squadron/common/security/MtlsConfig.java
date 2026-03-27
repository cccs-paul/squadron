package com.squadron.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

/**
 * Configures mutual TLS (mTLS) for inter-service communication.
 * <p>
 * This configuration is only active when {@code squadron.mtls.enabled=true}.
 * It creates an {@link SSLContext} from the configured key store and trust store,
 * then provides a {@link RestTemplate} bean that uses mTLS for all outbound requests.
 * <p>
 * The mTLS RestTemplate is registered as {@code mtlsRestTemplate} to avoid conflicting
 * with other RestTemplate beans in the application context.
 */
@Configuration
@ConditionalOnProperty(name = "squadron.mtls.enabled", havingValue = "true")
@EnableConfigurationProperties(MtlsProperties.class)
public class MtlsConfig {

    private static final Logger log = LoggerFactory.getLogger(MtlsConfig.class);

    @Bean
    public SSLContext mtlsSslContext(MtlsProperties properties)
            throws KeyStoreException, IOException, NoSuchAlgorithmException,
            CertificateException, UnrecoverableKeyException, KeyManagementException {

        // Load key store (service's own certificate + private key)
        KeyStore keyStore = KeyStore.getInstance(properties.getKeyStoreType());
        try (FileInputStream kis = new FileInputStream(properties.getKeyStorePath())) {
            keyStore.load(kis, properties.getKeyStorePassword().toCharArray());
        }

        // Load trust store (trusted CA certificates)
        KeyStore trustStore = KeyStore.getInstance(properties.getTrustStoreType());
        try (FileInputStream tis = new FileInputStream(properties.getTrustStorePath())) {
            trustStore.load(tis, properties.getTrustStorePassword().toCharArray());
        }

        // Initialize key manager
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, properties.getKeyStorePassword().toCharArray());

        // Initialize trust manager
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        // Build SSL context
        SSLContext sslContext = SSLContext.getInstance(properties.getProtocol());
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        log.info("mTLS SSLContext initialized with protocol={}, keyStoreType={}",
                properties.getProtocol(), properties.getKeyStoreType());
        return sslContext;
    }

    @Bean("mtlsRestTemplate")
    public RestTemplate mtlsRestTemplate(SSLContext mtlsSslContext) {
        // Apply the mTLS SSLContext as the default for this RestTemplate
        SSLContext.setDefault(mtlsSslContext);

        RestTemplate restTemplate = new RestTemplate();

        log.info("mTLS RestTemplate created for inter-service communication");
        return restTemplate;
    }
}
