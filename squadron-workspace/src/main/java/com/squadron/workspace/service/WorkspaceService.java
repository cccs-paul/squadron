package com.squadron.workspace.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.event.WorkspaceLifecycleEvent;
import com.squadron.common.exception.InvalidStateTransitionException;
import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.workspace.dto.CreateWorkspaceRequest;
import com.squadron.workspace.dto.ExecRequest;
import com.squadron.workspace.dto.ExecResult;
import com.squadron.workspace.dto.WorkspaceDto;
import com.squadron.workspace.dto.WorkspaceSpec;
import com.squadron.workspace.entity.Workspace;
import com.squadron.workspace.provider.WorkspaceProvider;
import com.squadron.workspace.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class WorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);
    private static final String NATS_SUBJECT = "squadron.workspace.lifecycle";
    private static final Set<String> EXEC_ALLOWED_STATUSES = Set.of("READY", "ACTIVE");

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceProvider workspaceProvider;
    private final NatsEventPublisher natsEventPublisher;
    private final ObjectMapper objectMapper;
    private final WorkspaceGitService workspaceGitService;

    public WorkspaceService(WorkspaceRepository workspaceRepository,
                            WorkspaceProvider workspaceProvider,
                            NatsEventPublisher natsEventPublisher,
                            ObjectMapper objectMapper,
                            WorkspaceGitService workspaceGitService) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceProvider = workspaceProvider;
        this.natsEventPublisher = natsEventPublisher;
        this.objectMapper = objectMapper;
        this.workspaceGitService = workspaceGitService;
    }

    public WorkspaceDto createWorkspace(CreateWorkspaceRequest request) {
        log.info("Creating workspace for task {} in tenant {}", request.getTaskId(), request.getTenantId());

        String resourceLimitsJson = serializeResourceLimits(request.getResourceLimits());

        Workspace workspace = Workspace.builder()
                .tenantId(request.getTenantId())
                .taskId(request.getTaskId())
                .userId(request.getUserId())
                .providerType(workspaceProvider.getProviderType())
                .status("CREATING")
                .repoUrl(request.getRepoUrl())
                .branch(request.getBranch())
                .baseImage(request.getBaseImage())
                .resourceLimits(resourceLimitsJson)
                .build();

        workspace = workspaceRepository.save(workspace);

        WorkspaceSpec spec = WorkspaceSpec.builder()
                .tenantId(request.getTenantId())
                .taskId(request.getTaskId())
                .userId(request.getUserId())
                .repoUrl(request.getRepoUrl())
                .branch(request.getBranch())
                .baseImage(request.getBaseImage())
                .resourceLimits(request.getResourceLimits())
                .providerType(workspaceProvider.getProviderType())
                .build();

        String containerId = workspaceProvider.createContainer(spec);

        workspace.setContainerId(containerId);
        workspace.setStatus("READY");
        workspace = workspaceRepository.save(workspace);

        publishLifecycleEvent(workspace, "CREATED");

        log.info("Workspace {} created successfully with container {}", workspace.getId(), containerId);

        // Auto-clone repo if repoUrl is set
        if (workspace.getRepoUrl() != null && !workspace.getRepoUrl().isBlank()) {
            try {
                workspaceGitService.cloneRepository(workspace.getId(), request.getAccessToken());
            } catch (Exception e) {
                log.warn("Auto-clone failed for workspace {}, workspace still usable", workspace.getId(), e);
            }
        }

        return toDto(workspace);
    }

    public void destroyWorkspace(UUID workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));

        log.info("Destroying workspace {}", workspaceId);

        if (workspace.getContainerId() != null) {
            try {
                workspaceProvider.destroyContainer(workspace.getContainerId());
            } catch (Exception e) {
                log.warn("Failed to destroy container {} for workspace {}, marking as terminated anyway",
                        workspace.getContainerId(), workspaceId, e);
            }
        }

        workspace.setStatus("TERMINATED");
        workspace.setTerminatedAt(Instant.now());
        workspaceRepository.save(workspace);

        publishLifecycleEvent(workspace, "TERMINATED");
        log.info("Workspace {} terminated", workspaceId);
    }

    public ExecResult execInWorkspace(ExecRequest request) {
        Workspace workspace = workspaceRepository.findById(request.getWorkspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", request.getWorkspaceId()));

        if (!EXEC_ALLOWED_STATUSES.contains(workspace.getStatus())) {
            throw new InvalidStateTransitionException(workspace.getStatus(), "EXEC");
        }

        String[] command = request.getCommand().toArray(new String[0]);
        return workspaceProvider.exec(workspace.getContainerId(), command);
    }

    public void copyToWorkspace(UUID workspaceId, byte[] content, String containerPath) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
        if (!EXEC_ALLOWED_STATUSES.contains(workspace.getStatus())) {
            throw new InvalidStateTransitionException(workspace.getStatus(), "COPY");
        }
        workspaceProvider.copyToContainer(workspace.getContainerId(), content, containerPath);
    }

    public byte[] copyFromWorkspace(UUID workspaceId, String containerPath) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
        if (!EXEC_ALLOWED_STATUSES.contains(workspace.getStatus())) {
            throw new InvalidStateTransitionException(workspace.getStatus(), "COPY");
        }
        return workspaceProvider.copyFromContainer(workspace.getContainerId(), containerPath);
    }

    @Transactional(readOnly = true)
    public WorkspaceDto getWorkspace(UUID workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
        return toDto(workspace);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceDto> listWorkspacesByTask(UUID taskId) {
        return workspaceRepository.findByTaskId(taskId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkspaceDto> listActiveWorkspaces(UUID tenantId) {
        return workspaceRepository.findByTenantIdAndStatus(tenantId, "READY").stream()
                .map(this::toDto)
                .toList();
    }

    private void publishLifecycleEvent(Workspace workspace, String action) {
        try {
            WorkspaceLifecycleEvent event = new WorkspaceLifecycleEvent();
            event.setWorkspaceId(workspace.getId());
            event.setTaskId(workspace.getTaskId());
            event.setAction(action);
            event.setTenantId(workspace.getTenantId());
            event.setSource("squadron-workspace");
            natsEventPublisher.publish(NATS_SUBJECT, event);
        } catch (Exception e) {
            log.warn("Failed to publish workspace lifecycle event for workspace {}", workspace.getId(), e);
        }
    }

    private WorkspaceDto toDto(Workspace workspace) {
        Map<String, Object> resourceLimits = deserializeResourceLimits(workspace.getResourceLimits());

        return WorkspaceDto.builder()
                .id(workspace.getId())
                .tenantId(workspace.getTenantId())
                .taskId(workspace.getTaskId())
                .userId(workspace.getUserId())
                .providerType(workspace.getProviderType())
                .containerId(workspace.getContainerId())
                .status(workspace.getStatus())
                .repoUrl(workspace.getRepoUrl())
                .branch(workspace.getBranch())
                .baseImage(workspace.getBaseImage())
                .resourceLimits(resourceLimits)
                .createdAt(workspace.getCreatedAt())
                .terminatedAt(workspace.getTerminatedAt())
                .build();
    }

    private String serializeResourceLimits(Map<String, Object> resourceLimits) {
        if (resourceLimits == null || resourceLimits.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(resourceLimits);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize resource limits", e);
            return null;
        }
    }

    private Map<String, Object> deserializeResourceLimits(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize resource limits", e);
            return null;
        }
    }
}
