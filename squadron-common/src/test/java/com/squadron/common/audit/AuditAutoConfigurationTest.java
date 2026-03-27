package com.squadron.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.config.NatsEventPublisher;
import io.nats.client.Connection;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuditAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AuditAutoConfiguration.class))
            .withUserConfiguration(TestDependencies.class);

    @Configuration
    static class TestDependencies {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        public Connection natsConnection() {
            return mock(Connection.class);
        }

        @Bean
        public NatsEventPublisher natsEventPublisher(Connection natsConnection, ObjectMapper objectMapper) {
            return new NatsEventPublisher(natsConnection, objectMapper);
        }
    }

    @Test
    void should_createAllBeans_when_auditEnabled() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AuditService.class);
            assertThat(context).hasSingleBean(AuditAspect.class);
            assertThat(context).hasSingleBean(AuditQueryService.class);
            assertThat(context).hasSingleBean(AuditController.class);
            assertThat(context).hasSingleBean(AuditProperties.class);
        });
    }

    @Test
    void should_notCreateBeans_when_auditDisabled() {
        contextRunner
                .withPropertyValues("squadron.audit.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AuditService.class);
                    assertThat(context).doesNotHaveBean(AuditAspect.class);
                    assertThat(context).doesNotHaveBean(AuditQueryService.class);
                    assertThat(context).doesNotHaveBean(AuditController.class);
                });
    }

    @Test
    void should_useCustomProperties_when_configured() {
        contextRunner
                .withPropertyValues(
                        "squadron.audit.enabled=true",
                        "squadron.audit.publish-to-nats=false",
                        "squadron.audit.nats-subject=custom.audit.topic")
                .run(context -> {
                    assertThat(context).hasSingleBean(AuditProperties.class);
                    AuditProperties props = context.getBean(AuditProperties.class);
                    assertThat(props.isPublishToNats()).isFalse();
                    assertThat(props.getNatsSubject()).isEqualTo("custom.audit.topic");
                });
    }
}
