package com.squadron.orchestrator.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowDefinitionDtoTest {

    @Test
    void should_createWithBuilder() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Instant now = Instant.now();

        WorkflowDefinitionDto dto = WorkflowDefinitionDto.builder()
                .id(id)
                .tenantId(tenantId)
                .teamId(teamId)
                .name("Default Workflow")
                .states("[\"BACKLOG\",\"DONE\"]")
                .transitions("[{\"from\":\"BACKLOG\",\"to\":\"DONE\"}]")
                .hooks("{}")
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(teamId, dto.getTeamId());
        assertEquals("Default Workflow", dto.getName());
        assertEquals("[\"BACKLOG\",\"DONE\"]", dto.getStates());
        assertEquals("[{\"from\":\"BACKLOG\",\"to\":\"DONE\"}]", dto.getTransitions());
        assertEquals("{}", dto.getHooks());
        assertTrue(dto.getActive());
        assertEquals(now, dto.getCreatedAt());
        assertEquals(now, dto.getUpdatedAt());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        WorkflowDefinitionDto dto = new WorkflowDefinitionDto();
        assertNull(dto.getId());
        assertNull(dto.getName());
    }

    @Test
    void should_setAndGetFields() {
        WorkflowDefinitionDto dto = new WorkflowDefinitionDto();
        dto.setName("Custom");
        dto.setActive(false);

        assertEquals("Custom", dto.getName());
        assertEquals(false, dto.getActive());
    }

    @Test
    void should_implementEquals() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        WorkflowDefinitionDto d1 = WorkflowDefinitionDto.builder()
                .id(id).name("WF").createdAt(now).build();
        WorkflowDefinitionDto d2 = WorkflowDefinitionDto.builder()
                .id(id).name("WF").createdAt(now).build();

        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentValues() {
        WorkflowDefinitionDto d1 = WorkflowDefinitionDto.builder().name("A").build();
        WorkflowDefinitionDto d2 = WorkflowDefinitionDto.builder().name("B").build();

        assertNotEquals(d1, d2);
    }

    @Test
    void should_haveToString() {
        WorkflowDefinitionDto dto = WorkflowDefinitionDto.builder().name("WF").build();
        assertNotNull(dto.toString());
        assert dto.toString().contains("WF");
    }

    @Test
    void should_createWithAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Instant now = Instant.now();

        WorkflowDefinitionDto dto = new WorkflowDefinitionDto(
                id, tenantId, teamId, "WF", "[]", "[]", "{}", true, now, now
        );

        assertEquals(id, dto.getId());
        assertEquals("WF", dto.getName());
        assertTrue(dto.getActive());
    }
}
