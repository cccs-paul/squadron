package com.squadron.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for the Squadron audit logging framework.
 * Activates when {@code squadron.audit.enabled} is {@code true} (the default).
 */
@Configuration
@EnableConfigurationProperties(AuditProperties.class)
@ConditionalOnProperty(prefix = "squadron.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuditQueryService auditQueryService() {
        return new AuditQueryService();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditService auditService(AuditProperties properties,
                                     ObjectMapper objectMapper,
                                     Connection natsConnection,
                                     AuditQueryService auditQueryService) {
        return new AuditService(properties, objectMapper, natsConnection, auditQueryService);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditAspect auditAspect(AuditService auditService) {
        return new AuditAspect(auditService);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditController auditController(AuditQueryService auditQueryService) {
        return new AuditController(auditQueryService);
    }
}
