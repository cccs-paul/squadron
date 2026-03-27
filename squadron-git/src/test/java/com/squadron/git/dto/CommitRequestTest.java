package com.squadron.git.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CommitRequestTest {

    @Test
    void should_createWithBuilder_when_allFieldsSet() {
        UUID workspaceId = UUID.randomUUID();
        CommitRequest request = CommitRequest.builder()
                .workspaceId(workspaceId)
                .message("Fix critical bug in auth module")
                .authorName("Jane Doe")
                .authorEmail("jane@example.com")
                .build();

        assertEquals(workspaceId, request.getWorkspaceId());
        assertEquals("Fix critical bug in auth module", request.getMessage());
        assertEquals("Jane Doe", request.getAuthorName());
        assertEquals("jane@example.com", request.getAuthorEmail());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        CommitRequest request = new CommitRequest();
        assertNull(request.getWorkspaceId());
        assertNull(request.getMessage());
        assertNull(request.getAuthorName());
        assertNull(request.getAuthorEmail());
    }

    @Test
    void should_createWithAllArgsConstructor() {
        UUID workspaceId = UUID.randomUUID();
        CommitRequest request = new CommitRequest(workspaceId, "Initial commit", "Bot", "bot@squadron.dev");

        assertEquals(workspaceId, request.getWorkspaceId());
        assertEquals("Initial commit", request.getMessage());
        assertEquals("Bot", request.getAuthorName());
        assertEquals("bot@squadron.dev", request.getAuthorEmail());
    }

    @Test
    void should_supportSettersAndGetters() {
        CommitRequest request = new CommitRequest();
        UUID workspaceId = UUID.randomUUID();
        request.setWorkspaceId(workspaceId);
        request.setMessage("Refactor service layer");
        request.setAuthorName("Dev");
        request.setAuthorEmail("dev@squadron.dev");

        assertEquals(workspaceId, request.getWorkspaceId());
        assertEquals("Refactor service layer", request.getMessage());
        assertEquals("Dev", request.getAuthorName());
        assertEquals("dev@squadron.dev", request.getAuthorEmail());
    }

    @Test
    void should_allowNullOptionalFields() {
        UUID workspaceId = UUID.randomUUID();
        CommitRequest request = CommitRequest.builder()
                .workspaceId(workspaceId)
                .message("Add new feature")
                .build();

        assertEquals(workspaceId, request.getWorkspaceId());
        assertEquals("Add new feature", request.getMessage());
        assertNull(request.getAuthorName());
        assertNull(request.getAuthorEmail());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        UUID workspaceId = UUID.randomUUID();
        CommitRequest request1 = CommitRequest.builder()
                .workspaceId(workspaceId)
                .message("Fix bug")
                .authorName("Dev")
                .authorEmail("dev@test.com")
                .build();
        CommitRequest request2 = CommitRequest.builder()
                .workspaceId(workspaceId)
                .message("Fix bug")
                .authorName("Dev")
                .authorEmail("dev@test.com")
                .build();

        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentMessage() {
        UUID workspaceId = UUID.randomUUID();
        CommitRequest request1 = CommitRequest.builder()
                .workspaceId(workspaceId)
                .message("Fix bug A")
                .build();
        CommitRequest request2 = CommitRequest.builder()
                .workspaceId(workspaceId)
                .message("Fix bug B")
                .build();

        assertNotEquals(request1, request2);
    }

    @Test
    void should_supportToString() {
        UUID workspaceId = UUID.randomUUID();
        CommitRequest request = CommitRequest.builder()
                .workspaceId(workspaceId)
                .message("Add tests")
                .authorName("Tester")
                .authorEmail("test@test.com")
                .build();

        String str = request.toString();
        assertNotNull(str);
        assertTrue(str.contains("workspaceId"));
        assertTrue(str.contains("message"));
        assertTrue(str.contains("authorName"));
        assertTrue(str.contains("authorEmail"));
    }
}
