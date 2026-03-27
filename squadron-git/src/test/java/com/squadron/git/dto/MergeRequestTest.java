package com.squadron.git.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MergeRequestTest {

    @Test
    void should_createWithBuilder_when_allFieldsSet() {
        UUID prId = UUID.randomUUID();
        MergeRequest request = MergeRequest.builder()
                .pullRequestRecordId(prId)
                .mergeStrategy("SQUASH")
                .accessToken("token123")
                .build();

        assertEquals(prId, request.getPullRequestRecordId());
        assertEquals("SQUASH", request.getMergeStrategy());
        assertEquals("token123", request.getAccessToken());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        MergeRequest request = new MergeRequest();
        assertNull(request.getPullRequestRecordId());
        // Field initializer "MERGE" runs for no-args constructor too
        assertEquals("MERGE", request.getMergeStrategy());
        assertNull(request.getAccessToken());
    }

    @Test
    void should_createWithAllArgsConstructor() {
        UUID prId = UUID.randomUUID();
        MergeRequest request = new MergeRequest(prId, "REBASE", "secret");

        assertEquals(prId, request.getPullRequestRecordId());
        assertEquals("REBASE", request.getMergeStrategy());
        assertEquals("secret", request.getAccessToken());
    }

    @Test
    void should_useDefaultMergeStrategy_when_notExplicitlySet() {
        UUID prId = UUID.randomUUID();
        MergeRequest request = MergeRequest.builder()
                .pullRequestRecordId(prId)
                .accessToken("token")
                .build();

        assertEquals(prId, request.getPullRequestRecordId());
        assertEquals("MERGE", request.getMergeStrategy());
        assertEquals("token", request.getAccessToken());
    }

    @Test
    void should_supportSettersAndGetters() {
        MergeRequest request = new MergeRequest();
        UUID prId = UUID.randomUUID();
        request.setPullRequestRecordId(prId);
        request.setMergeStrategy("SQUASH");
        request.setAccessToken("ghp_token");

        assertEquals(prId, request.getPullRequestRecordId());
        assertEquals("SQUASH", request.getMergeStrategy());
        assertEquals("ghp_token", request.getAccessToken());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        UUID prId = UUID.randomUUID();
        MergeRequest request1 = MergeRequest.builder()
                .pullRequestRecordId(prId)
                .mergeStrategy("MERGE")
                .accessToken("token")
                .build();
        MergeRequest request2 = MergeRequest.builder()
                .pullRequestRecordId(prId)
                .mergeStrategy("MERGE")
                .accessToken("token")
                .build();

        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentMergeStrategy() {
        UUID prId = UUID.randomUUID();
        MergeRequest request1 = MergeRequest.builder()
                .pullRequestRecordId(prId)
                .mergeStrategy("MERGE")
                .build();
        MergeRequest request2 = MergeRequest.builder()
                .pullRequestRecordId(prId)
                .mergeStrategy("SQUASH")
                .build();

        assertNotEquals(request1, request2);
    }

    @Test
    void should_supportToString() {
        UUID prId = UUID.randomUUID();
        MergeRequest request = MergeRequest.builder()
                .pullRequestRecordId(prId)
                .mergeStrategy("MERGE")
                .accessToken("token")
                .build();

        String str = request.toString();
        assertNotNull(str);
        assertTrue(str.contains("pullRequestRecordId"));
        assertTrue(str.contains("mergeStrategy"));
        assertTrue(str.contains("accessToken"));
    }

    @Test
    void should_allowNullAccessToken() {
        UUID prId = UUID.randomUUID();
        MergeRequest request = MergeRequest.builder()
                .pullRequestRecordId(prId)
                .build();

        assertNull(request.getAccessToken());
    }
}
