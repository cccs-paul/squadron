package com.squadron.orchestrator.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.orchestrator.entity.WorkflowDefinition;
import com.squadron.orchestrator.repository.WorkflowDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultWorkflowInitializerTest {

    @Mock
    private WorkflowDefinitionRepository workflowDefinitionRepository;

    private ObjectMapper objectMapper;
    private DefaultWorkflowInitializer initializer;

    private static final UUID SYSTEM_TENANT_ID = new UUID(0L, 0L);

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        initializer = new DefaultWorkflowInitializer(workflowDefinitionRepository, objectMapper);
    }

    @Test
    void should_createDefaultDefinition_when_noneExists() {
        when(workflowDefinitionRepository.findByTenantIdAndTeamIdIsNullAndActiveTrue(SYSTEM_TENANT_ID))
                .thenReturn(Optional.empty());
        when(workflowDefinitionRepository.save(any(WorkflowDefinition.class)))
                .thenAnswer(inv -> {
                    WorkflowDefinition def = inv.getArgument(0);
                    def.setId(UUID.randomUUID());
                    return def;
                });

        ApplicationArguments args = mock(ApplicationArguments.class);
        initializer.run(args);

        ArgumentCaptor<WorkflowDefinition> captor = ArgumentCaptor.forClass(WorkflowDefinition.class);
        verify(workflowDefinitionRepository).save(captor.capture());

        WorkflowDefinition saved = captor.getValue();
        assertEquals(SYSTEM_TENANT_ID, saved.getTenantId());
        assertNull(saved.getTeamId());
        assertEquals("System Default Workflow", saved.getName());
        assertTrue(saved.getActive());
        assertEquals("{}", saved.getHooks());
        assertNotNull(saved.getStates());
        assertNotNull(saved.getTransitions());

        // Verify states contain all TaskState values
        String statesJson = saved.getStates();
        for (TaskState state : TaskState.values()) {
            assertTrue(statesJson.contains(state.name()),
                    "States should contain " + state.name());
        }

        // Verify transitions contain expected structure
        String transitionsJson = saved.getTransitions();
        assertTrue(transitionsJson.contains("BACKLOG"));
        assertTrue(transitionsJson.contains("PRIORITIZED"));
        assertTrue(transitionsJson.contains("DONE"));
    }

    @Test
    void should_skipCreation_when_definitionAlreadyExists() {
        WorkflowDefinition existing = WorkflowDefinition.builder()
                .id(UUID.randomUUID())
                .tenantId(SYSTEM_TENANT_ID)
                .name("System Default Workflow")
                .states("[]")
                .transitions("[]")
                .active(true)
                .build();

        when(workflowDefinitionRepository.findByTenantIdAndTeamIdIsNullAndActiveTrue(SYSTEM_TENANT_ID))
                .thenReturn(Optional.of(existing));

        ApplicationArguments args = mock(ApplicationArguments.class);
        initializer.run(args);

        verify(workflowDefinitionRepository, never()).save(any());
    }

    @Test
    void should_haveCorrectTransitionCount() {
        when(workflowDefinitionRepository.findByTenantIdAndTeamIdIsNullAndActiveTrue(SYSTEM_TENANT_ID))
                .thenReturn(Optional.empty());
        when(workflowDefinitionRepository.save(any(WorkflowDefinition.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ApplicationArguments args = mock(ApplicationArguments.class);
        initializer.run(args);

        ArgumentCaptor<WorkflowDefinition> captor = ArgumentCaptor.forClass(WorkflowDefinition.class);
        verify(workflowDefinitionRepository).save(captor.capture());

        String transitionsJson = captor.getValue().getTransitions();
        // Count occurrences of "from" to determine number of transitions
        long transitionCount = transitionsJson.chars()
                .filter(ch -> ch == '{')
                .count();
        // Should have 13 transitions as defined in the initializer
        assertEquals(13, transitionCount);
    }
}
