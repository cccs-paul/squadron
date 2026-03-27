package com.squadron.orchestrator.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CreateWorkflowDefinitionRequestTest {

    @Test
    void should_createWithBuilder() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        CreateWorkflowDefinitionRequest request = CreateWorkflowDefinitionRequest.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .name("My Workflow")
                .states("[\"OPEN\",\"IN_PROGRESS\",\"DONE\"]")
                .transitions("{\"OPEN\":[\"IN_PROGRESS\"],\"IN_PROGRESS\":[\"DONE\"]}")
                .hooks("{\"onEnter\":{\"DONE\":\"notifySlack\"}}")
                .build();

        assertEquals(tenantId, request.getTenantId());
        assertEquals(teamId, request.getTeamId());
        assertEquals("My Workflow", request.getName());
        assertEquals("[\"OPEN\",\"IN_PROGRESS\",\"DONE\"]", request.getStates());
        assertEquals("{\"OPEN\":[\"IN_PROGRESS\"],\"IN_PROGRESS\":[\"DONE\"]}", request.getTransitions());
        assertEquals("{\"onEnter\":{\"DONE\":\"notifySlack\"}}", request.getHooks());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        CreateWorkflowDefinitionRequest request = new CreateWorkflowDefinitionRequest();
        assertNull(request.getTenantId());
        assertNull(request.getTeamId());
        assertNull(request.getName());
        assertNull(request.getStates());
        assertNull(request.getTransitions());
    }

    @Test
    void should_haveDefaultHooksValue_when_builtWithBuilder() {
        CreateWorkflowDefinitionRequest request = CreateWorkflowDefinitionRequest.builder()
                .name("Test")
                .states("[]")
                .transitions("{}")
                .build();

        assertEquals("{}", request.getHooks());
    }

    @Test
    void should_overrideDefaultHooks_when_explicitlySet() {
        CreateWorkflowDefinitionRequest request = CreateWorkflowDefinitionRequest.builder()
                .hooks("{\"onEnter\":{}}")
                .build();

        assertEquals("{\"onEnter\":{}}", request.getHooks());
    }

    @Test
    void should_setAndGetFields() {
        CreateWorkflowDefinitionRequest request = new CreateWorkflowDefinitionRequest();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        request.setTenantId(tenantId);
        request.setTeamId(teamId);
        request.setName("Workflow");
        request.setStates("[\"TODO\"]");
        request.setTransitions("{}");
        request.setHooks("{\"hook\":true}");

        assertEquals(tenantId, request.getTenantId());
        assertEquals(teamId, request.getTeamId());
        assertEquals("Workflow", request.getName());
        assertEquals("[\"TODO\"]", request.getStates());
        assertEquals("{}", request.getTransitions());
        assertEquals("{\"hook\":true}", request.getHooks());
    }

    @Test
    void should_implementEquals() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        CreateWorkflowDefinitionRequest r1 = CreateWorkflowDefinitionRequest.builder()
                .tenantId(tenantId).teamId(teamId).name("WF").states("[]").transitions("{}").build();
        CreateWorkflowDefinitionRequest r2 = CreateWorkflowDefinitionRequest.builder()
                .tenantId(tenantId).teamId(teamId).name("WF").states("[]").transitions("{}").build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentValues() {
        CreateWorkflowDefinitionRequest r1 = CreateWorkflowDefinitionRequest.builder().name("A").build();
        CreateWorkflowDefinitionRequest r2 = CreateWorkflowDefinitionRequest.builder().name("B").build();

        assertNotEquals(r1, r2);
    }

    @Test
    void should_haveToString() {
        CreateWorkflowDefinitionRequest request = CreateWorkflowDefinitionRequest.builder()
                .name("MyWorkflow")
                .build();
        String str = request.toString();
        assertNotNull(str);
        assertTrue(str.contains("MyWorkflow"));
    }

    @Test
    void should_createWithAllArgsConstructor() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        CreateWorkflowDefinitionRequest request = new CreateWorkflowDefinitionRequest(
                tenantId, teamId, "Name", "states", "transitions", "hooks"
        );

        assertEquals(tenantId, request.getTenantId());
        assertEquals(teamId, request.getTeamId());
        assertEquals("Name", request.getName());
        assertEquals("states", request.getStates());
        assertEquals("transitions", request.getTransitions());
        assertEquals("hooks", request.getHooks());
    }

    @Test
    void should_allowNullTeamId() {
        CreateWorkflowDefinitionRequest request = CreateWorkflowDefinitionRequest.builder()
                .tenantId(UUID.randomUUID())
                .teamId(null)
                .name("Global Workflow")
                .states("[]")
                .transitions("{}")
                .build();

        assertNull(request.getTeamId());
        assertNotNull(request.getTenantId());
    }
}
