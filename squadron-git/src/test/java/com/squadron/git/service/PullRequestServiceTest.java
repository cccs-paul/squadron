package com.squadron.git.service;

import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.git.adapter.GitPlatformAdapter;
import com.squadron.git.adapter.GitPlatformAdapterRegistry;
import com.squadron.git.dto.CreatePullRequestRequest;
import com.squadron.git.dto.DiffFile;
import com.squadron.git.dto.DiffResult;
import com.squadron.git.dto.MergeRequest;
import com.squadron.git.dto.MergeabilityDto;
import com.squadron.git.dto.PullRequestDto;
import com.squadron.git.entity.PullRequestRecord;
import com.squadron.git.repository.PullRequestRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PullRequestServiceTest {

    @Mock
    private GitPlatformAdapterRegistry adapterRegistry;

    @Mock
    private PullRequestRecordRepository pullRequestRecordRepository;

    @Mock
    private NatsEventPublisher natsEventPublisher;

    @Mock
    private GitPlatformAdapter gitPlatformAdapter;

    private PullRequestService pullRequestService;

    @BeforeEach
    void setUp() {
        pullRequestService = new PullRequestService(adapterRegistry, pullRequestRecordRepository, natsEventPublisher);
    }

    @Test
    void should_createPullRequest_successfully() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();

        CreatePullRequestRequest request = CreatePullRequestRequest.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .platform("GITHUB")
                .repoOwner("owner")
                .repoName("repo")
                .title("My PR")
                .description("Description")
                .sourceBranch("feature")
                .targetBranch("main")
                .accessToken("token")
                .build();

        PullRequestDto adapterDto = PullRequestDto.builder()
                .externalPrId("42")
                .externalPrUrl("https://github.com/owner/repo/pull/42")
                .title("My PR")
                .sourceBranch("feature")
                .targetBranch("main")
                .status("OPEN")
                .build();

        when(adapterRegistry.getAdapter("GITHUB")).thenReturn(gitPlatformAdapter);
        when(gitPlatformAdapter.createPullRequest(request)).thenReturn(adapterDto);
        when(pullRequestRecordRepository.save(any(PullRequestRecord.class))).thenAnswer(invocation -> {
            PullRequestRecord r = invocation.getArgument(0);
            r.setId(recordId);
            r.setCreatedAt(Instant.now());
            r.setUpdatedAt(Instant.now());
            return r;
        });

        PullRequestDto result = pullRequestService.createPullRequest(request);

        assertNotNull(result);
        assertEquals(recordId, result.getId());
        assertEquals("42", result.getExternalPrId());
        assertEquals("https://github.com/owner/repo/pull/42", result.getExternalPrUrl());
        assertEquals("My PR", result.getTitle());

        verify(adapterRegistry).getAdapter("GITHUB");
        verify(gitPlatformAdapter).createPullRequest(request);
        verify(pullRequestRecordRepository).save(any(PullRequestRecord.class));
        verify(natsEventPublisher).publish(eq("squadron.git.events"), any());
    }

    @Test
    void should_mergePullRequest_successfully() {
        UUID recordId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        PullRequestRecord record = PullRequestRecord.builder()
                .id(recordId)
                .tenantId(tenantId)
                .taskId(UUID.randomUUID())
                .platform("GITHUB")
                .externalPrId("42")
                .externalPrUrl("https://github.com/owner/repo/pull/42")
                .title("My PR")
                .sourceBranch("feature")
                .targetBranch("main")
                .status("OPEN")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        MergeRequest request = MergeRequest.builder()
                .pullRequestRecordId(recordId)
                .mergeStrategy("SQUASH")
                .accessToken("token")
                .build();

        when(pullRequestRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(adapterRegistry.getAdapter("GITHUB")).thenReturn(gitPlatformAdapter);
        when(pullRequestRecordRepository.save(any(PullRequestRecord.class))).thenAnswer(i -> i.getArgument(0));

        pullRequestService.mergePullRequest(request);

        assertEquals("MERGED", record.getStatus());
        verify(gitPlatformAdapter).mergePullRequest("owner", "repo", "42", "SQUASH", "token");
        verify(pullRequestRecordRepository).save(record);
    }

    @Test
    void should_throwResourceNotFound_whenMergingNonExistent() {
        UUID recordId = UUID.randomUUID();
        MergeRequest request = MergeRequest.builder()
                .pullRequestRecordId(recordId)
                .accessToken("token")
                .build();

        when(pullRequestRecordRepository.findById(recordId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> pullRequestService.mergePullRequest(request));
    }

    @Test
    void should_getPullRequest_successfully() {
        UUID recordId = UUID.randomUUID();
        PullRequestRecord record = PullRequestRecord.builder()
                .id(recordId)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .platform("GITLAB")
                .externalPrId("10")
                .externalPrUrl("https://gitlab.com/owner/repo/-/merge_requests/10")
                .title("MR Title")
                .sourceBranch("dev")
                .targetBranch("main")
                .status("OPEN")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(pullRequestRecordRepository.findById(recordId)).thenReturn(Optional.of(record));

        PullRequestDto result = pullRequestService.getPullRequest(recordId);

        assertNotNull(result);
        assertEquals(recordId, result.getId());
        assertEquals("GITLAB", result.getPlatform());
        assertEquals("10", result.getExternalPrId());
    }

    @Test
    void should_throwResourceNotFound_whenGettingNonExistent() {
        UUID recordId = UUID.randomUUID();
        when(pullRequestRecordRepository.findById(recordId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> pullRequestService.getPullRequest(recordId));
    }

    @Test
    void should_listPullRequestsByTenant_withStatus() {
        UUID tenantId = UUID.randomUUID();
        PullRequestRecord record = PullRequestRecord.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(UUID.randomUUID())
                .platform("GITHUB")
                .externalPrId("1")
                .title("PR 1")
                .sourceBranch("feat")
                .targetBranch("main")
                .status("OPEN")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(pullRequestRecordRepository.findByTenantIdAndStatus(tenantId, "OPEN")).thenReturn(List.of(record));

        List<PullRequestDto> results = pullRequestService.listPullRequestsByTenant(tenantId, "OPEN");

        assertEquals(1, results.size());
        assertEquals("OPEN", results.get(0).getStatus());
    }

    @Test
    void should_listPullRequestsByTenant_withoutStatus() {
        UUID tenantId = UUID.randomUUID();
        when(pullRequestRecordRepository.findAll()).thenReturn(List.of());

        List<PullRequestDto> results = pullRequestService.listPullRequestsByTenant(tenantId, null);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void should_requestReviewers_successfully() {
        UUID recordId = UUID.randomUUID();
        PullRequestRecord record = PullRequestRecord.builder()
                .id(recordId)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .platform("GITHUB")
                .externalPrId("42")
                .externalPrUrl("https://github.com/owner/repo/pull/42")
                .title("PR")
                .sourceBranch("feat")
                .targetBranch("main")
                .status("OPEN")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(pullRequestRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(adapterRegistry.getAdapter("GITHUB")).thenReturn(gitPlatformAdapter);

        pullRequestService.requestReviewers(recordId, List.of("user1"), "token");

        verify(gitPlatformAdapter).requestReviewers("owner", "repo", "42", List.of("user1"), "token");
    }

    @Test
    void should_getDiff_successfully() {
        UUID recordId = UUID.randomUUID();
        PullRequestRecord record = PullRequestRecord.builder()
                .id(recordId)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .platform("GITHUB")
                .externalPrId("42")
                .externalPrUrl("https://github.com/owner/repo/pull/42")
                .title("PR")
                .sourceBranch("feat")
                .targetBranch("main")
                .status("OPEN")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        DiffResult expectedDiff = DiffResult.builder()
                .files(List.of(DiffFile.builder().filename("test.java").status("modified").additions(5).deletions(2).build()))
                .totalAdditions(5)
                .totalDeletions(2)
                .build();

        when(pullRequestRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(adapterRegistry.getAdapter("GITHUB")).thenReturn(gitPlatformAdapter);
        when(gitPlatformAdapter.getDiff("owner", "repo", "42", "token")).thenReturn(expectedDiff);

        DiffResult result = pullRequestService.getDiff(recordId, "token");

        assertNotNull(result);
        assertEquals(5, result.getTotalAdditions());
        assertEquals(2, result.getTotalDeletions());
        assertEquals(1, result.getFiles().size());
    }

    @Test
    void should_handleNatsPublishFailure_gracefully() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        CreatePullRequestRequest request = CreatePullRequestRequest.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .platform("GITHUB")
                .repoOwner("owner")
                .repoName("repo")
                .title("PR")
                .sourceBranch("feat")
                .targetBranch("main")
                .accessToken("token")
                .build();

        PullRequestDto adapterDto = PullRequestDto.builder()
                .externalPrId("1")
                .externalPrUrl("https://github.com/owner/repo/pull/1")
                .title("PR")
                .sourceBranch("feat")
                .targetBranch("main")
                .status("OPEN")
                .build();

        when(adapterRegistry.getAdapter("GITHUB")).thenReturn(gitPlatformAdapter);
        when(gitPlatformAdapter.createPullRequest(request)).thenReturn(adapterDto);
        when(pullRequestRecordRepository.save(any(PullRequestRecord.class))).thenAnswer(invocation -> {
            PullRequestRecord r = invocation.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setCreatedAt(Instant.now());
            r.setUpdatedAt(Instant.now());
            return r;
        });
        doThrow(new RuntimeException("NATS down")).when(natsEventPublisher).publish(anyString(), any());

        // Should not throw
        assertDoesNotThrow(() -> pullRequestService.createPullRequest(request));
    }

    // ---- Tests for getByTaskId ----

    @Test
    void should_getByTaskId_successfully() {
        UUID taskId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        PullRequestRecord record = PullRequestRecord.builder()
                .id(recordId)
                .tenantId(UUID.randomUUID())
                .taskId(taskId)
                .platform("GITHUB")
                .externalPrId("42")
                .externalPrUrl("https://github.com/owner/repo/pull/42")
                .title("My PR")
                .sourceBranch("feature")
                .targetBranch("main")
                .status("OPEN")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(pullRequestRecordRepository.findByTaskId(taskId)).thenReturn(Optional.of(record));

        PullRequestDto result = pullRequestService.getByTaskId(taskId);

        assertNotNull(result);
        assertEquals(recordId, result.getId());
        assertEquals(taskId, result.getTaskId());
        assertEquals("42", result.getExternalPrId());
    }

    @Test
    void should_throwResourceNotFound_when_getByTaskIdNotFound() {
        UUID taskId = UUID.randomUUID();
        when(pullRequestRecordRepository.findByTaskId(taskId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> pullRequestService.getByTaskId(taskId));
    }

    // ---- Tests for checkMergeability ----

    @Test
    void should_checkMergeability_successfully_when_open() {
        UUID recordId = UUID.randomUUID();
        PullRequestRecord record = PullRequestRecord.builder()
                .id(recordId)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .platform("GITHUB")
                .externalPrId("42")
                .externalPrUrl("https://github.com/owner/repo/pull/42")
                .title("PR")
                .sourceBranch("feat")
                .targetBranch("main")
                .status("OPEN")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        PullRequestDto prDto = PullRequestDto.builder()
                .externalPrId("42")
                .status("OPEN")
                .build();

        when(pullRequestRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(adapterRegistry.getAdapter("GITHUB")).thenReturn(gitPlatformAdapter);
        when(gitPlatformAdapter.getPullRequest("owner", "repo", "42", null)).thenReturn(prDto);

        MergeabilityDto result = pullRequestService.checkMergeability(recordId);

        assertNotNull(result);
        assertTrue(result.isMergeable());
        assertTrue(result.getConflictFiles().isEmpty());
    }

    @Test
    void should_checkMergeability_returnNotMergeable_when_adapterFails() {
        UUID recordId = UUID.randomUUID();
        PullRequestRecord record = PullRequestRecord.builder()
                .id(recordId)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .platform("GITHUB")
                .externalPrId("42")
                .externalPrUrl("https://github.com/owner/repo/pull/42")
                .title("PR")
                .sourceBranch("feat")
                .targetBranch("main")
                .status("OPEN")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(pullRequestRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(adapterRegistry.getAdapter("GITHUB")).thenReturn(gitPlatformAdapter);
        when(gitPlatformAdapter.getPullRequest("owner", "repo", "42", null))
                .thenThrow(new RuntimeException("API error"));

        MergeabilityDto result = pullRequestService.checkMergeability(recordId);

        assertNotNull(result);
        assertFalse(result.isMergeable());
    }

    @Test
    void should_throwResourceNotFound_when_checkMergeabilityRecordMissing() {
        UUID recordId = UUID.randomUUID();
        when(pullRequestRecordRepository.findById(recordId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> pullRequestService.checkMergeability(recordId));
    }
}
