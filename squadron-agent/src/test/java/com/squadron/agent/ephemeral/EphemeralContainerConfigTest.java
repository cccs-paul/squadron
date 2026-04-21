package com.squadron.agent.ephemeral;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EphemeralContainerConfigTest {

    @Test
    void should_haveCorrectDefaults() {
        EphemeralContainerConfig config = new EphemeralContainerConfig();

        assertEquals("squadron/agent-sandbox:latest", config.getImage());
        assertEquals(4096, config.getOpenCodePort());
        assertEquals(60, config.getHealthCheckTimeoutSeconds());
        assertEquals(2, config.getHealthCheckIntervalSeconds());
        assertEquals("512Mi", config.getMemoryLimit());
        assertEquals("1", config.getCpuLimit());
        assertEquals("squadron-ephemeral", config.getOpenCodePassword());
        assertEquals("opencode", config.getOpenCodeUsername());
        assertTrue(config.isEnabled());
    }

    @Test
    void should_allowSettingAllProperties() {
        EphemeralContainerConfig config = new EphemeralContainerConfig();

        config.setImage("custom-image:v2");
        config.setOpenCodePort(8080);
        config.setHealthCheckTimeoutSeconds(120);
        config.setHealthCheckIntervalSeconds(5);
        config.setMemoryLimit("1Gi");
        config.setCpuLimit("2");
        config.setOpenCodePassword("my-secret");
        config.setOpenCodeUsername("admin");
        config.setEnabled(false);

        assertEquals("custom-image:v2", config.getImage());
        assertEquals(8080, config.getOpenCodePort());
        assertEquals(120, config.getHealthCheckTimeoutSeconds());
        assertEquals(5, config.getHealthCheckIntervalSeconds());
        assertEquals("1Gi", config.getMemoryLimit());
        assertEquals("2", config.getCpuLimit());
        assertEquals("my-secret", config.getOpenCodePassword());
        assertEquals("admin", config.getOpenCodeUsername());
        assertFalse(config.isEnabled());
    }
}
