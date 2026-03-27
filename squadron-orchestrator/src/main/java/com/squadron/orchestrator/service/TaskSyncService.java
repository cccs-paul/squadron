package com.squadron.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.orchestrator.dto.TaskSyncRequest;
import com.squadron.orchestrator.dto.TaskSyncResult;
import com.squadron.orchestrator.entity.Task;
import com.squadron.orchestrator.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class TaskSyncService {

    private static final Logger log = LoggerFactory.getLogger(TaskSyncService.class);

    private final TaskRepository taskRepository;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public TaskSyncService(TaskRepository taskRepository,
                           @Value("${squadron.platform.service-url:http://localhost:8084}") String platformServiceUrl,
                           ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.restClient = RestClient.builder().baseUrl(platformServiceUrl).build();
        this.objectMapper = objectMapper;
    }

    // Constructor for testing with injected RestClient
    TaskSyncService(TaskRepository taskRepository, RestClient restClient, ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TaskSyncResult syncTasks(TaskSyncRequest request) {
        log.info("Starting task sync for project {} from platform connection {}",
                request.getProjectKey(), request.getPlatformConnectionId());

        int created = 0, updated = 0, unchanged = 0, failed = 0;
        List<String> errors = new ArrayList<>();

        try {
            // Fetch tasks from platform service
            // The platform service returns ApiResponse<List<PlatformTaskDto>>
            // We need to extract from the "data" field
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri("/api/platform-sync/{connectionId}/tasks?projectKey={key}",
                            request.getPlatformConnectionId(), request.getProjectKey())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response == null || response.get("data") == null) {
                log.warn("No tasks returned from platform service");
                return TaskSyncResult.builder()
                        .created(0).updated(0).unchanged(0).failed(0)
                        .errors(List.of())
                        .build();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> externalTasks = (List<Map<String, Object>>) response.get("data");

            for (Map<String, Object> extTask : externalTasks) {
                try {
                    String externalId = (String) extTask.get("externalId");
                    String externalUrl = (String) extTask.get("externalUrl");
                    String title = (String) extTask.get("title");
                    String description = (String) extTask.get("description");
                    String priority = (String) extTask.get("priority");
                    Object labelsObj = extTask.get("labels");
                    String labelsJson = labelsObj != null ? objectMapper.writeValueAsString(labelsObj) : null;

                    Optional<Task> existing = taskRepository.findByExternalId(externalId);

                    if (existing.isPresent()) {
                        Task task = existing.get();
                        boolean changed = false;

                        if (title != null && !title.equals(task.getTitle())) {
                            task.setTitle(title);
                            changed = true;
                        }
                        if (description != null && !Objects.equals(description, task.getDescription())) {
                            task.setDescription(description);
                            changed = true;
                        }
                        if (priority != null && !Objects.equals(priority, task.getPriority())) {
                            task.setPriority(priority);
                            changed = true;
                        }
                        if (labelsJson != null && !Objects.equals(labelsJson, task.getLabels())) {
                            task.setLabels(labelsJson);
                            changed = true;
                        }
                        if (externalUrl != null && !Objects.equals(externalUrl, task.getExternalUrl())) {
                            task.setExternalUrl(externalUrl);
                            changed = true;
                        }

                        if (changed) {
                            taskRepository.save(task);
                            updated++;
                        } else {
                            unchanged++;
                        }
                    } else {
                        Task newTask = Task.builder()
                                .tenantId(request.getTenantId())
                                .teamId(request.getTeamId())
                                .projectId(request.getProjectId())
                                .externalId(externalId)
                                .externalUrl(externalUrl)
                                .title(title)
                                .description(description)
                                .priority(priority)
                                .labels(labelsJson)
                                .build();
                        taskRepository.save(newTask);
                        created++;
                    }
                } catch (Exception e) {
                    failed++;
                    errors.add("Failed to sync task: " + e.getMessage());
                    log.warn("Failed to sync individual task: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch tasks from platform service: {}", e.getMessage());
            errors.add("Platform service call failed: " + e.getMessage());
            failed++;
        }

        TaskSyncResult result = TaskSyncResult.builder()
                .created(created)
                .updated(updated)
                .unchanged(unchanged)
                .failed(failed)
                .errors(errors)
                .build();

        log.info("Task sync completed: created={}, updated={}, unchanged={}, failed={}",
                created, updated, unchanged, failed);

        return result;
    }
}
