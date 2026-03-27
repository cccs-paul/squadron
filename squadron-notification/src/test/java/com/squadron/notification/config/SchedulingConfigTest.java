package com.squadron.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulingConfigTest {

    @Test
    void should_beAnnotatedWithEnableScheduling() {
        SchedulingConfig config = new SchedulingConfig();
        assertNotNull(config);
        assertTrue(config.getClass().isAnnotationPresent(EnableScheduling.class));
    }
}
