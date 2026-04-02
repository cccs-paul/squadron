package com.squadron.platform.adapter;

import com.squadron.platform.dto.PlatformProjectDto;
import com.squadron.platform.dto.PlatformTaskDto;
import com.squadron.platform.dto.PlatformTaskFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketingPlatformAdapterTest {

    @Mock
    private TicketingPlatformAdapter adapter;

    @Test
    void should_beAnInterface() {
        assertTrue(TicketingPlatformAdapter.class.isInterface());
    }

    @Test
    void should_declareAllExpectedMethods() {
        List<String> methodNames = Arrays.stream(TicketingPlatformAdapter.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();

        assertTrue(methodNames.contains("getPlatformType"));
        assertTrue(methodNames.contains("configure"));
        assertTrue(methodNames.contains("fetchTasks"));
        assertTrue(methodNames.contains("getTask"));
        assertTrue(methodNames.contains("updateTaskStatus"));
        assertTrue(methodNames.contains("addComment"));
        assertTrue(methodNames.contains("getAvailableStatuses"));
        assertTrue(methodNames.contains("testConnection"));
        assertTrue(methodNames.contains("getProjects"));
    }

    @Test
    void should_haveExactlyNineMethods() {
        Method[] methods = TicketingPlatformAdapter.class.getDeclaredMethods();
        assertEquals(9, methods.length);
    }

    @Test
    void should_mockGetPlatformType() {
        when(adapter.getPlatformType()).thenReturn("JIRA_CLOUD");

        assertEquals("JIRA_CLOUD", adapter.getPlatformType());
        verify(adapter).getPlatformType();
    }

    @Test
    void should_mockConfigure() {
        Map<String, String> creds = Map.of("pat", "token123");
        doNothing().when(adapter).configure("https://jira.example.com", creds);

        adapter.configure("https://jira.example.com", creds);

        verify(adapter).configure("https://jira.example.com", creds);
    }

    @Test
    void should_mockFetchTasks() {
        PlatformTaskFilter filter = new PlatformTaskFilter();
        PlatformTaskDto task = new PlatformTaskDto();
        task.setExternalId("JIRA-1");
        task.setTitle("Bug fix");

        when(adapter.fetchTasks("PROJ", filter)).thenReturn(List.of(task));

        List<PlatformTaskDto> result = adapter.fetchTasks("PROJ", filter);

        assertEquals(1, result.size());
        assertEquals("JIRA-1", result.get(0).getExternalId());
        verify(adapter).fetchTasks("PROJ", filter);
    }

    @Test
    void should_mockGetTask() {
        PlatformTaskDto task = new PlatformTaskDto();
        task.setExternalId("JIRA-42");
        task.setTitle("Feature");

        when(adapter.getTask("JIRA-42")).thenReturn(task);

        PlatformTaskDto result = adapter.getTask("JIRA-42");

        assertEquals("JIRA-42", result.getExternalId());
        assertEquals("Feature", result.getTitle());
        verify(adapter).getTask("JIRA-42");
    }

    @Test
    void should_mockUpdateTaskStatus() {
        doNothing().when(adapter).updateTaskStatus("JIRA-1", "DONE", "Completed");

        adapter.updateTaskStatus("JIRA-1", "DONE", "Completed");

        verify(adapter).updateTaskStatus("JIRA-1", "DONE", "Completed");
    }

    @Test
    void should_mockAddComment() {
        doNothing().when(adapter).addComment("JIRA-1", "This is a comment");

        adapter.addComment("JIRA-1", "This is a comment");

        verify(adapter).addComment("JIRA-1", "This is a comment");
    }

    @Test
    void should_mockGetAvailableStatuses() {
        when(adapter.getAvailableStatuses("PROJ")).thenReturn(List.of("TODO", "IN_PROGRESS", "DONE"));

        List<String> statuses = adapter.getAvailableStatuses("PROJ");

        assertEquals(3, statuses.size());
        assertTrue(statuses.contains("TODO"));
        assertTrue(statuses.contains("IN_PROGRESS"));
        assertTrue(statuses.contains("DONE"));
        verify(adapter).getAvailableStatuses("PROJ");
    }

    @Test
    void should_mockTestConnection() {
        when(adapter.testConnection()).thenReturn(true);
        assertTrue(adapter.testConnection());

        when(adapter.testConnection()).thenReturn(false);
        assertFalse(adapter.testConnection());
    }

    @Test
    void should_allowAnonymousImplementation() {
        TicketingPlatformAdapter anonymous = new TicketingPlatformAdapter() {
            @Override
            public String getPlatformType() { return "CUSTOM"; }

            @Override
            public void configure(String baseUrl, Map<String, String> credentials) {}

            @Override
            public List<PlatformTaskDto> fetchTasks(String projectKey, PlatformTaskFilter filter) {
                return List.of();
            }

            @Override
            public PlatformTaskDto getTask(String externalId) { return null; }

            @Override
            public void updateTaskStatus(String externalId, String status, String comment) {}

            @Override
            public void addComment(String externalId, String comment) {}

            @Override
            public List<String> getAvailableStatuses(String projectKey) {
                return List.of("OPEN", "CLOSED");
            }

            @Override
            public boolean testConnection() { return true; }

            @Override
            public List<PlatformProjectDto> getProjects() { return List.of(); }
        };

        assertEquals("CUSTOM", anonymous.getPlatformType());
        assertTrue(anonymous.testConnection());
        assertEquals(List.of("OPEN", "CLOSED"), anonymous.getAvailableStatuses("ANY"));
        assertTrue(anonymous.fetchTasks("ANY", null).isEmpty());
        assertNull(anonymous.getTask("X"));
        assertTrue(anonymous.getProjects().isEmpty());
    }
}
