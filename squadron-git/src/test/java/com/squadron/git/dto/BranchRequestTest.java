package com.squadron.git.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BranchRequestTest {

    @Test
    void should_createWithBuilder_when_allFieldsSet() {
        UUID workspaceId = UUID.randomUUID();
        BranchRequest request = BranchRequest.builder()
                .workspaceId(workspaceId)
                .branchName("feature/new-feature")
                .baseBranch("main")
                .build();

        assertEquals(workspaceId, request.getWorkspaceId());
        assertEquals("feature/new-feature", request.getBranchName());
        assertEquals("main", request.getBaseBranch());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        BranchRequest request = new BranchRequest();
        assertNull(request.getWorkspaceId());
        assertNull(request.getBranchName());
        assertNull(request.getBaseBranch());
    }

    @Test
    void should_createWithAllArgsConstructor() {
        UUID workspaceId = UUID.randomUUID();
        BranchRequest request = new BranchRequest(workspaceId, "feature/test", "develop");

        assertEquals(workspaceId, request.getWorkspaceId());
        assertEquals("feature/test", request.getBranchName());
        assertEquals("develop", request.getBaseBranch());
    }

    @Test
    void should_supportSettersAndGetters() {
        BranchRequest request = new BranchRequest();
        UUID workspaceId = UUID.randomUUID();
        request.setWorkspaceId(workspaceId);
        request.setBranchName("hotfix/urgent");
        request.setBaseBranch("release/1.0");

        assertEquals(workspaceId, request.getWorkspaceId());
        assertEquals("hotfix/urgent", request.getBranchName());
        assertEquals("release/1.0", request.getBaseBranch());
    }

    @Test
    void should_allowNullBaseBranch_when_optional() {
        UUID workspaceId = UUID.randomUUID();
        BranchRequest request = BranchRequest.builder()
                .workspaceId(workspaceId)
                .branchName("feature/test")
                .build();

        assertEquals(workspaceId, request.getWorkspaceId());
        assertEquals("feature/test", request.getBranchName());
        assertNull(request.getBaseBranch());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        UUID workspaceId = UUID.randomUUID();
        BranchRequest request1 = BranchRequest.builder()
                .workspaceId(workspaceId)
                .branchName("feature/test")
                .baseBranch("main")
                .build();
        BranchRequest request2 = BranchRequest.builder()
                .workspaceId(workspaceId)
                .branchName("feature/test")
                .baseBranch("main")
                .build();

        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFields() {
        UUID workspaceId = UUID.randomUUID();
        BranchRequest request1 = BranchRequest.builder()
                .workspaceId(workspaceId)
                .branchName("feature/a")
                .build();
        BranchRequest request2 = BranchRequest.builder()
                .workspaceId(workspaceId)
                .branchName("feature/b")
                .build();

        assertNotEquals(request1, request2);
    }

    @Test
    void should_supportToString() {
        UUID workspaceId = UUID.randomUUID();
        BranchRequest request = BranchRequest.builder()
                .workspaceId(workspaceId)
                .branchName("feature/test")
                .baseBranch("main")
                .build();

        String str = request.toString();
        assertNotNull(str);
        assertTrue(str.contains("workspaceId"));
        assertTrue(str.contains("branchName"));
        assertTrue(str.contains("baseBranch"));
    }
}
