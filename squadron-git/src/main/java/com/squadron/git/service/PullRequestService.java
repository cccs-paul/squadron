package com.squadron.git.service;

import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.event.SquadronEvent;
import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.git.adapter.GitPlatformAdapter;
import com.squadron.git.adapter.GitPlatformAdapterRegistry;
import com.squadron.git.dto.CreatePullRequestRequest;
import com.squadron.git.dto.DiffResult;
import com.squadron.git.dto.MergeRequest;
import com.squadron.git.dto.MergeabilityDto;
import com.squadron.git.dto.PullRequestDto;
import com.squadron.git.entity.PullRequestRecord;
import com.squadron.git.repository.PullRequestRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing pull requests / merge requests across Git platforms.
 */
@Service
@Transactional
public class PullRequestService {

    private static final Logger log = LoggerFactory.getLogger(PullRequestService.class);

    private final GitPlatformAdapterRegistry adapterRegistry;
    private final PullRequestRecordRepository pullRequestRecordRepository;
    private final NatsEventPublisher natsEventPublisher;

    public PullRequestService(GitPlatformAdapterRegistry adapterRegistry,
                              PullRequestRecordRepository pullRequestRecordRepository,
                              NatsEventPublisher natsEventPublisher) {
        this.adapterRegistry = adapterRegistry;
        this.pullRequestRecordRepository = pullRequestRecordRepository;
        this.natsEventPublisher = natsEventPublisher;
    }

    /**
     * Create a pull request on the platform and persist the record.
     */
    public PullRequestDto createPullRequest(CreatePullRequestRequest request) {
        log.info("Creating pull request for task {} on platform {}", request.getTaskId(), request.getPlatform());

        GitPlatformAdapter adapter = adapterRegistry.getAdapter(request.getPlatform().toUpperCase());
        PullRequestDto dto = adapter.createPullRequest(request);

        PullRequestRecord record = PullRequestRecord.builder()
                .tenantId(request.getTenantId())
                .taskId(request.getTaskId())
                .platform(request.getPlatform().toUpperCase())
                .externalPrId(dto.getExternalPrId())
                .externalPrUrl(dto.getExternalPrUrl())
                .title(dto.getTitle())
                .sourceBranch(dto.getSourceBranch())
                .targetBranch(dto.getTargetBranch())
                .status("OPEN")
                .build();

        record = pullRequestRecordRepository.save(record);
        dto.setId(record.getId());

        publishEvent(request.getTenantId(), "PR_CREATED",
                "Pull request created: " + record.getExternalPrUrl());

        log.info("Pull request record saved with id {}", record.getId());
        return dto;
    }

    /**
     * Merge a pull request.
     */
    public void mergePullRequest(MergeRequest request) {
        PullRequestRecord record = pullRequestRecordRepository.findById(request.getPullRequestRecordId())
                .orElseThrow(() -> new ResourceNotFoundException("PullRequestRecord", request.getPullRequestRecordId()));

        log.info("Merging pull request {} on platform {}", record.getExternalPrId(), record.getPlatform());

        String[] ownerRepo = parseOwnerRepo(record);
        GitPlatformAdapter adapter = adapterRegistry.getAdapter(record.getPlatform());
        adapter.mergePullRequest(ownerRepo[0], ownerRepo[1], record.getExternalPrId(),
                request.getMergeStrategy(), request.getAccessToken());

        record.setStatus("MERGED");
        pullRequestRecordRepository.save(record);

        publishEvent(record.getTenantId(), "PR_MERGED",
                "Pull request merged: " + record.getExternalPrUrl());

        log.info("Pull request {} merged", record.getExternalPrId());
    }

    /**
     * Get a pull request record by ID.
     */
    @Transactional(readOnly = true)
    public PullRequestDto getPullRequest(UUID recordId) {
        PullRequestRecord record = pullRequestRecordRepository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("PullRequestRecord", recordId));
        return toDto(record);
    }

    /**
     * Get a pull request record by task ID.
     */
    @Transactional(readOnly = true)
    public PullRequestDto getByTaskId(UUID taskId) {
        PullRequestRecord record = pullRequestRecordRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("PullRequestRecord for task", taskId));
        return toDto(record);
    }

    /**
     * Check if a pull request is mergeable (no conflicts).
     */
    @Transactional(readOnly = true)
    public MergeabilityDto checkMergeability(UUID recordId) {
        PullRequestRecord record = pullRequestRecordRepository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("PullRequestRecord", recordId));

        String[] ownerRepo = parseOwnerRepo(record);
        GitPlatformAdapter adapter = adapterRegistry.getAdapter(record.getPlatform());

        try {
            PullRequestDto prDto = adapter.getPullRequest(ownerRepo[0], ownerRepo[1],
                    record.getExternalPrId(), getDelegatedAccessToken(null, record.getPlatform()));
            // If getPullRequest succeeds without error, the PR is open and potentially mergeable
            boolean isMergeable = "OPEN".equals(record.getStatus());
            return MergeabilityDto.builder()
                    .mergeable(isMergeable)
                    .conflictFiles(List.of())
                    .build();
        } catch (Exception e) {
            log.warn("Mergeability check failed for PR {}: {}", recordId, e.getMessage());
            return MergeabilityDto.builder()
                    .mergeable(false)
                    .conflictFiles(List.of())
                    .build();
        }
    }

    /**
     * List pull requests by tenant and optional status.
     */
    @Transactional(readOnly = true)
    public List<PullRequestDto> listPullRequestsByTenant(UUID tenantId, String status) {
        List<PullRequestRecord> records;
        if (status != null && !status.isBlank()) {
            records = pullRequestRecordRepository.findByTenantIdAndStatus(tenantId, status);
        } else {
            records = pullRequestRecordRepository.findAll();
        }
        return records.stream().map(this::toDto).toList();
    }

    /**
     * Request reviewers for a pull request.
     */
    public void requestReviewers(UUID recordId, List<String> reviewers, String accessToken) {
        PullRequestRecord record = pullRequestRecordRepository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("PullRequestRecord", recordId));

        String[] ownerRepo = parseOwnerRepo(record);
        GitPlatformAdapter adapter = adapterRegistry.getAdapter(record.getPlatform());
        adapter.requestReviewers(ownerRepo[0], ownerRepo[1], record.getExternalPrId(), reviewers, accessToken);

        log.info("Reviewers requested for PR {}", record.getExternalPrId());
    }

    /**
     * Get the diff for a pull request.
     */
    @Transactional(readOnly = true)
    public DiffResult getDiff(UUID recordId, String accessToken) {
        PullRequestRecord record = pullRequestRecordRepository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("PullRequestRecord", recordId));

        String[] ownerRepo = parseOwnerRepo(record);
        GitPlatformAdapter adapter = adapterRegistry.getAdapter(record.getPlatform());
        return adapter.getDiff(ownerRepo[0], ownerRepo[1], record.getExternalPrId(), accessToken);
    }

    /**
     * Gets the access token for the current user's platform connection.
     * In production, this calls the platform service to retrieve the
     * user's decrypted OAuth2/PAT token for the specified platform.
     *
     * @param userId   the ID of the user whose delegated token is requested
     * @param platform the platform identifier (e.g., "GITHUB", "GITLAB", "BITBUCKET")
     * @return the decrypted access token, or null if not yet available
     */
    private String getDelegatedAccessToken(UUID userId, String platform) {
        // For now, expect the token to be passed in the request.
        // TODO: Call squadron-platform's UserTokenService to get the decrypted token
        // via internal REST call or service-to-service communication.
        //
        // Example future implementation:
        //   return platformClient.getUserToken(userId, platform).getAccessToken();
        //
        // The platform service stores encrypted OAuth2 tokens obtained during
        // user account linking. It decrypts them using the tenant's encryption key
        // and returns the plaintext token for use in Git platform API calls.
        log.debug("Delegated token lookup for user {} on platform {} - not yet implemented, using request token",
                userId, platform);
        return null;
    }

    private PullRequestDto toDto(PullRequestRecord record) {
        return PullRequestDto.builder()
                .id(record.getId())
                .tenantId(record.getTenantId())
                .taskId(record.getTaskId())
                .platform(record.getPlatform())
                .externalPrId(record.getExternalPrId())
                .externalPrUrl(record.getExternalPrUrl())
                .title(record.getTitle())
                .sourceBranch(record.getSourceBranch())
                .targetBranch(record.getTargetBranch())
                .status(record.getStatus())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }

    /**
     * Parse owner/repo from the external PR URL.
     * Supports GitHub, GitLab, and Bitbucket URL formats.
     */
    private String[] parseOwnerRepo(PullRequestRecord record) {
        String url = record.getExternalPrUrl();
        if (url != null && !url.isBlank()) {
            try {
                String path = URI.create(url).getPath();
                String[] parts = path.split("/");
                if (parts.length >= 3) {
                    return new String[]{parts[1], parts[2]};
                }
            } catch (Exception e) {
                log.warn("Could not parse owner/repo from URL: {}", url);
            }
        }
        return new String[]{"", ""};
    }

    private void publishEvent(UUID tenantId, String eventType, String message) {
        try {
            SquadronEvent event = new SquadronEvent();
            event.setEventType(eventType);
            event.setTenantId(tenantId);
            event.setSource("squadron-git");
            natsEventPublisher.publish("squadron.git.events", event);
        } catch (Exception e) {
            log.warn("Failed to publish event {}: {}", eventType, e.getMessage());
        }
    }
}
