package com.squadron.git.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PullRequestDtoTest {

    @Test
    void should_createWithBuilder_when_allFieldsSet() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Instant updatedAt = Instant.now();

        PullRequestDto dto = PullRequestDto.builder()
                .id(id)
                .tenantId(tenantId)
                .taskId(taskId)
                .platform("GITHUB")
                .externalPrId("42")
                .externalPrUrl("https://github.com/owner/repo/pull/42")
                .title("feat: Add new feature")
                .sourceBranch("feature/new")
                .targetBranch("main")
                .status("OPEN")
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(taskId, dto.getTaskId());
        assertEquals("GITHUB", dto.getPlatform());
        assertEquals("42", dto.getExternalPrId());
        assertEquals("https://github.com/owner/repo/pull/42", dto.getExternalPrUrl());
        assertEquals("feat: Add new feature", dto.getTitle());
        assertEquals("feature/new", dto.getSourceBranch());
        assertEquals("main", dto.getTargetBranch());
        assertEquals("OPEN", dto.getStatus());
        assertEquals(createdAt, dto.getCreatedAt());
        assertEquals(updatedAt, dto.getUpdatedAt());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        PullRequestDto dto = new PullRequestDto();
        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getTaskId());
        assertNull(dto.getPlatform());
        assertNull(dto.getExternalPrId());
        assertNull(dto.getExternalPrUrl());
        assertNull(dto.getTitle());
        assertNull(dto.getSourceBranch());
        assertNull(dto.getTargetBranch());
        assertNull(dto.getStatus());
        assertNull(dto.getCreatedAt());
        assertNull(dto.getUpdatedAt());
    }

    @Test
    void should_createWithAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2025-06-01T10:00:00Z");
        Instant updatedAt = Instant.parse("2025-06-01T12:00:00Z");

        PullRequestDto dto = new PullRequestDto(
                id, tenantId, taskId, "GITLAB", "99",
                "https://gitlab.com/repo/merge_requests/99",
                "Fix: resolve crash", "hotfix/crash", "main",
                "MERGED", createdAt, updatedAt);

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(taskId, dto.getTaskId());
        assertEquals("GITLAB", dto.getPlatform());
        assertEquals("99", dto.getExternalPrId());
        assertEquals("https://gitlab.com/repo/merge_requests/99", dto.getExternalPrUrl());
        assertEquals("Fix: resolve crash", dto.getTitle());
        assertEquals("hotfix/crash", dto.getSourceBranch());
        assertEquals("main", dto.getTargetBranch());
        assertEquals("MERGED", dto.getStatus());
        assertEquals(createdAt, dto.getCreatedAt());
        assertEquals(updatedAt, dto.getUpdatedAt());
    }

    @Test
    void should_supportSettersAndGetters() {
        PullRequestDto dto = new PullRequestDto();
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.now();

        dto.setId(id);
        dto.setTenantId(tenantId);
        dto.setTaskId(taskId);
        dto.setPlatform("BITBUCKET");
        dto.setExternalPrId("7");
        dto.setExternalPrUrl("https://bitbucket.org/repo/pull-requests/7");
        dto.setTitle("Update dependencies");
        dto.setSourceBranch("chore/deps");
        dto.setTargetBranch("develop");
        dto.setStatus("CLOSED");
        dto.setCreatedAt(now);
        dto.setUpdatedAt(now);

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(taskId, dto.getTaskId());
        assertEquals("BITBUCKET", dto.getPlatform());
        assertEquals("7", dto.getExternalPrId());
        assertEquals("https://bitbucket.org/repo/pull-requests/7", dto.getExternalPrUrl());
        assertEquals("Update dependencies", dto.getTitle());
        assertEquals("chore/deps", dto.getSourceBranch());
        assertEquals("develop", dto.getTargetBranch());
        assertEquals("CLOSED", dto.getStatus());
        assertEquals(now, dto.getCreatedAt());
        assertEquals(now, dto.getUpdatedAt());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2025-01-01T00:00:00Z");

        PullRequestDto dto1 = PullRequestDto.builder()
                .id(id)
                .tenantId(tenantId)
                .taskId(taskId)
                .platform("GITHUB")
                .status("OPEN")
                .createdAt(createdAt)
                .build();
        PullRequestDto dto2 = PullRequestDto.builder()
                .id(id)
                .tenantId(tenantId)
                .taskId(taskId)
                .platform("GITHUB")
                .status("OPEN")
                .createdAt(createdAt)
                .build();

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentStatus() {
        UUID id = UUID.randomUUID();
        PullRequestDto dto1 = PullRequestDto.builder()
                .id(id)
                .status("OPEN")
                .build();
        PullRequestDto dto2 = PullRequestDto.builder()
                .id(id)
                .status("MERGED")
                .build();

        assertNotEquals(dto1, dto2);
    }

    @Test
    void should_supportToString() {
        PullRequestDto dto = PullRequestDto.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .platform("GITHUB")
                .externalPrId("42")
                .title("Test PR")
                .status("OPEN")
                .build();

        String str = dto.toString();
        assertNotNull(str);
        assertTrue(str.contains("platform"));
        assertTrue(str.contains("externalPrId"));
        assertTrue(str.contains("title"));
        assertTrue(str.contains("status"));
    }
}
