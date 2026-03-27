package com.squadron.platform.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformAdapterRegistryTest {

    @Mock
    private TicketingPlatformAdapter jiraCloudAdapter;

    @Mock
    private TicketingPlatformAdapter jiraServerAdapter;

    @Mock
    private TicketingPlatformAdapter githubAdapter;

    @Mock
    private TicketingPlatformAdapter gitlabAdapter;

    @Mock
    private TicketingPlatformAdapter azureDevOpsAdapter;

    private PlatformAdapterRegistry registry;

    @BeforeEach
    void setUp() {
        when(jiraCloudAdapter.getPlatformType()).thenReturn("JIRA_CLOUD");
        when(jiraServerAdapter.getPlatformType()).thenReturn("JIRA_SERVER");
        when(githubAdapter.getPlatformType()).thenReturn("GITHUB");
        when(gitlabAdapter.getPlatformType()).thenReturn("GITLAB");
        when(azureDevOpsAdapter.getPlatformType()).thenReturn("AZURE_DEVOPS");

        registry = new PlatformAdapterRegistry(List.of(
                jiraCloudAdapter, jiraServerAdapter, githubAdapter, gitlabAdapter, azureDevOpsAdapter
        ));
    }

    @Test
    void should_returnAdapter_forJiraCloud() {
        TicketingPlatformAdapter adapter = registry.getAdapter("JIRA_CLOUD");
        assertNotNull(adapter);
        assertEquals("JIRA_CLOUD", adapter.getPlatformType());
    }

    @Test
    void should_returnAdapter_forJiraServer() {
        TicketingPlatformAdapter adapter = registry.getAdapter("JIRA_SERVER");
        assertNotNull(adapter);
        assertEquals("JIRA_SERVER", adapter.getPlatformType());
    }

    @Test
    void should_returnAdapter_forGitHub() {
        TicketingPlatformAdapter adapter = registry.getAdapter("GITHUB");
        assertNotNull(adapter);
        assertEquals("GITHUB", adapter.getPlatformType());
    }

    @Test
    void should_returnAdapter_forGitLab() {
        TicketingPlatformAdapter adapter = registry.getAdapter("GITLAB");
        assertNotNull(adapter);
        assertEquals("GITLAB", adapter.getPlatformType());
    }

    @Test
    void should_returnAdapter_forAzureDevOps() {
        TicketingPlatformAdapter adapter = registry.getAdapter("AZURE_DEVOPS");
        assertNotNull(adapter);
        assertEquals("AZURE_DEVOPS", adapter.getPlatformType());
    }

    @Test
    void should_throwException_forUnknownPlatform() {
        assertThrows(IllegalArgumentException.class, () -> registry.getAdapter("UNKNOWN"));
    }

    @Test
    void should_returnAllRegisteredPlatformTypes() {
        List<String> types = registry.getRegisteredPlatformTypes();
        assertEquals(5, types.size());
        assertTrue(types.contains("JIRA_CLOUD"));
        assertTrue(types.contains("JIRA_SERVER"));
        assertTrue(types.contains("GITHUB"));
        assertTrue(types.contains("GITLAB"));
        assertTrue(types.contains("AZURE_DEVOPS"));
    }

    @Test
    void should_handleEmptyAdapterList() {
        PlatformAdapterRegistry emptyRegistry = new PlatformAdapterRegistry(List.of());
        assertTrue(emptyRegistry.getRegisteredPlatformTypes().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> emptyRegistry.getAdapter("JIRA_CLOUD"));
    }

    @Test
    void should_returnCorrectExceptionMessage_forUnknownPlatform() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> registry.getAdapter("NONEXISTENT"));
        assertTrue(ex.getMessage().contains("NONEXISTENT"));
    }
}
