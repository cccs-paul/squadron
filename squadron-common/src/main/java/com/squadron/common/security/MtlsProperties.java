package com.squadron.common.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for mutual TLS (mTLS) between Squadron services.
 * <p>
 * When {@code squadron.mtls.enabled=true}, services authenticate each other
 * using client certificates. The key store holds the service's own certificate
 * and private key; the trust store holds the CA certificate(s) used to verify
 * peer certificates.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "squadron.mtls")
public class MtlsProperties {

    /**
     * Whether mTLS is enabled for inter-service communication.
     */
    private boolean enabled = false;

    /**
     * Path to the PKCS12/JKS key store containing the service's certificate and private key.
     */
    private String keyStorePath;

    /**
     * Password for the key store.
     */
    private String keyStorePassword;

    /**
     * Path to the trust store containing trusted CA certificates.
     */
    private String trustStorePath;

    /**
     * Password for the trust store.
     */
    private String trustStorePassword;

    /**
     * Key store type. Defaults to PKCS12.
     */
    private String keyStoreType = "PKCS12";

    /**
     * Trust store type. Defaults to PKCS12.
     */
    private String trustStoreType = "PKCS12";

    /**
     * TLS protocol version. Defaults to TLSv1.3.
     */
    private String protocol = "TLSv1.3";
}
