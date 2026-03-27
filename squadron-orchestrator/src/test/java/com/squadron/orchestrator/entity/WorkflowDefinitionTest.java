package com.squadron.orchestrator.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowDefinitionTest {

    @Test
    void should_createWithBuilder() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        WorkflowDefinition def = WorkflowDefinition.builder()
                .id(id)
                .tenantId(tenantId)
                .teamId(teamId)
                .name("Custom Workflow")
                .states("[\"BACKLOG\",\"DONE\"]")
                .transitions("[{\"from\":\"BACKLOG\",\"to\":\"DONE\"}]")
                .hooks("{\"onDone\":\"notify\"}")
                .active(true)
                .build();

        assertEquals(id, def.getId());
        assertEquals(tenantId, def.getTenantId());
        assertEquals(teamId, def.getTeamId());
        assertEquals("Custom Workflow", def.getName());
        assertEquals("[\"BACKLOG\",\"DONE\"]", def.getStates());
        assertEquals("[{\"from\":\"BACKLOG\",\"to\":\"DONE\"}]", def.getTransitions());
        assertEquals("{\"onDone\":\"notify\"}", def.getHooks());
        assertTrue(def.getActive());
    }

    @Test
    void should_haveDefaultValues() {
        WorkflowDefinition def = WorkflowDefinition.builder()
                .tenantId(UUID.randomUUID())
                .name("WF")
                .states("[]")
                .transitions("[]")
                .build();

        assertEquals("{}", def.getHooks());
        assertTrue(def.getActive());
    }

    @Test
    void should_setTimestampsOnPrePersist() {
        WorkflowDefinition def = new WorkflowDefinition();
        def.onCreate();

        assertNotNull(def.getCreatedAt());
        assertNotNull(def.getUpdatedAt());
        assertEquals(def.getCreatedAt(), def.getUpdatedAt());
    }

    @Test
    void should_updateTimestampOnPreUpdate() {
        WorkflowDefinition def = new WorkflowDefinition();
        def.onCreate();

        def.onUpdate();

        assertNotNull(def.getUpdatedAt());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        WorkflowDefinition def = new WorkflowDefinition();
        assertNull(def.getId());
        assertNull(def.getName());
    }

    @Test
    void should_allowNullTeamId() {
        WorkflowDefinition def = WorkflowDefinition.builder()
                .tenantId(UUID.randomUUID())
                .teamId(null)
                .name("System Default")
                .states("[]")
                .transitions("[]")
                .build();

        assertNull(def.getTeamId());
    }

    @Test
    void should_setAndGetFields() {
        WorkflowDefinition def = new WorkflowDefinition();
        def.setName("Updated");
        def.setActive(false);

        assertEquals("Updated", def.getName());
        assertEquals(false, def.getActive());
    }

    @Test
    void should_haveToString() {
        WorkflowDefinition def = WorkflowDefinition.builder().name("WF").build();
        assertNotNull(def.toString());
        assert def.toString().contains("WF");
    }
}
