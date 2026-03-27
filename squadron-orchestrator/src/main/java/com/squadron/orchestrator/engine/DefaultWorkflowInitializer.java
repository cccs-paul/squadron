package com.squadron.orchestrator.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.orchestrator.entity.WorkflowDefinition;
import com.squadron.orchestrator.repository.WorkflowDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class DefaultWorkflowInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultWorkflowInitializer.class);
    private static final UUID SYSTEM_TENANT_ID = new UUID(0L, 0L);

    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final ObjectMapper objectMapper;

    public DefaultWorkflowInitializer(WorkflowDefinitionRepository workflowDefinitionRepository,
                                       ObjectMapper objectMapper) {
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean exists = workflowDefinitionRepository
                .findByTenantIdAndTeamIdIsNullAndActiveTrue(SYSTEM_TENANT_ID)
                .isPresent();

        if (exists) {
            log.info("System default workflow definition already exists, skipping initialization");
            return;
        }

        log.info("Creating system default workflow definition");

        List<String> states = Arrays.stream(TaskState.values())
                .map(Enum::name)
                .collect(Collectors.toList());

        List<Map<String, String>> transitions = new ArrayList<>();
        transitions.add(Map.of("from", "BACKLOG", "to", "PRIORITIZED"));
        transitions.add(Map.of("from", "PRIORITIZED", "to", "PLANNING"));
        transitions.add(Map.of("from", "PRIORITIZED", "to", "BACKLOG"));
        transitions.add(Map.of("from", "PLANNING", "to", "PROPOSE_CODE"));
        transitions.add(Map.of("from", "PLANNING", "to", "PRIORITIZED"));
        transitions.add(Map.of("from", "PROPOSE_CODE", "to", "REVIEW"));
        transitions.add(Map.of("from", "PROPOSE_CODE", "to", "PLANNING"));
        transitions.add(Map.of("from", "REVIEW", "to", "QA"));
        transitions.add(Map.of("from", "REVIEW", "to", "PROPOSE_CODE"));
        transitions.add(Map.of("from", "QA", "to", "MERGE"));
        transitions.add(Map.of("from", "QA", "to", "REVIEW"));
        transitions.add(Map.of("from", "MERGE", "to", "DONE"));
        transitions.add(Map.of("from", "MERGE", "to", "QA"));

        try {
            WorkflowDefinition definition = WorkflowDefinition.builder()
                    .tenantId(SYSTEM_TENANT_ID)
                    .teamId(null)
                    .name("System Default Workflow")
                    .states(objectMapper.writeValueAsString(states))
                    .transitions(objectMapper.writeValueAsString(transitions))
                    .hooks("{}")
                    .active(true)
                    .build();

            workflowDefinitionRepository.save(definition);
            log.info("System default workflow definition created successfully");
        } catch (JsonProcessingException e) {
            log.error("Failed to create system default workflow definition", e);
            throw new RuntimeException("Failed to serialize default workflow definition", e);
        }
    }
}
