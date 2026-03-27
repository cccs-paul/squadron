package com.squadron.workspace.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExecRequestTest {

    @Test
    void should_buildExecRequest() {
        UUID workspaceId = UUID.randomUUID();
        List<String> command = List.of("ls", "-la");

        ExecRequest request = ExecRequest.builder()
                .workspaceId(workspaceId)
                .command(command)
                .build();

        assertEquals(workspaceId, request.getWorkspaceId());
        assertEquals(command, request.getCommand());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        ExecRequest request = new ExecRequest();
        assertNull(request.getWorkspaceId());
        assertNull(request.getCommand());
    }

    @Test
    void should_setAndGetFields() {
        ExecRequest request = new ExecRequest();
        UUID id = UUID.randomUUID();
        request.setWorkspaceId(id);
        request.setCommand(List.of("echo", "hello"));

        assertEquals(id, request.getWorkspaceId());
        assertEquals(List.of("echo", "hello"), request.getCommand());
    }
}
