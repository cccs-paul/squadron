package com.squadron.git.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PullRequestRecordTest {

    @Test
    void should_createPullRequestRecord_withBuilder() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        PullRequestRecord record = PullRequestRecord.builder()
                .id(id)
                .tenantId(tenantId)
                .taskId(taskId)
                .platform("GITHUB")
                .externalPrId("42")
                .externalPrUrl("https://github.com/owner/repo/pull/42")
                .title("Fix authentication bug")
                .sourceBranch("feature/auth-fix")
                .targetBranch("main")
                .status("OPEN")
                .build();

        assertEquals(id, record.getId());
        assertEquals(tenantId, record.getTenantId());
        assertEquals(taskId, record.getTaskId());
        assertEquals("GITHUB", record.getPlatform());
        assertEquals("42", record.getExternalPrId());
        assertEquals("https://github.com/owner/repo/pull/42", record.getExternalPrUrl());
        assertEquals("Fix authentication bug", record.getTitle());
        assertEquals("feature/auth-fix", record.getSourceBranch());
        assertEquals("main", record.getTargetBranch());
        assertEquals("OPEN", record.getStatus());
    }

    @Test
    void should_haveDefaultStatus_whenUsingBuilder() {
        PullRequestRecord record = PullRequestRecord.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .platform("GITLAB")
                .externalPrId("1")
                .title("Test")
                .sourceBranch("dev")
                .targetBranch("main")
                .build();

        assertEquals("OPEN", record.getStatus());
    }

    @Test
    void should_setAndGetAllFields() {
        PullRequestRecord record = new PullRequestRecord();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.now();

        record.setId(id);
        record.setTenantId(tenantId);
        record.setTaskId(taskId);
        record.setPlatform("BITBUCKET");
        record.setExternalPrId("99");
        record.setExternalPrUrl("https://bitbucket.org/workspace/repo/pull-requests/99");
        record.setTitle("Add feature");
        record.setSourceBranch("feature/new");
        record.setTargetBranch("develop");
        record.setStatus("MERGED");
        record.setCreatedAt(now);
        record.setUpdatedAt(now);

        assertEquals(id, record.getId());
        assertEquals(tenantId, record.getTenantId());
        assertEquals(taskId, record.getTaskId());
        assertEquals("BITBUCKET", record.getPlatform());
        assertEquals("99", record.getExternalPrId());
        assertEquals("https://bitbucket.org/workspace/repo/pull-requests/99", record.getExternalPrUrl());
        assertEquals("Add feature", record.getTitle());
        assertEquals("feature/new", record.getSourceBranch());
        assertEquals("develop", record.getTargetBranch());
        assertEquals("MERGED", record.getStatus());
        assertEquals(now, record.getCreatedAt());
        assertEquals(now, record.getUpdatedAt());
    }

    @Test
    void should_supportAllPlatforms() {
        for (String platform : new String[]{"GITHUB", "GITLAB", "BITBUCKET"}) {
            PullRequestRecord record = PullRequestRecord.builder()
                    .tenantId(UUID.randomUUID())
                    .taskId(UUID.randomUUID())
                    .platform(platform)
                    .externalPrId("1")
                    .title("Test")
                    .sourceBranch("dev")
                    .targetBranch("main")
                    .build();
            assertEquals(platform, record.getPlatform());
        }
    }

    @Test
    void should_supportAllStatuses() {
        for (String status : new String[]{"OPEN", "CLOSED", "MERGED"}) {
            PullRequestRecord record = PullRequestRecord.builder()
                    .tenantId(UUID.randomUUID())
                    .taskId(UUID.randomUUID())
                    .platform("GITHUB")
                    .externalPrId("1")
                    .title("Test")
                    .sourceBranch("dev")
                    .targetBranch("main")
                    .status(status)
                    .build();
            assertEquals(status, record.getStatus());
        }
    }
}
