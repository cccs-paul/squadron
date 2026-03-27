package com.squadron.agent.tool.builtin;

import com.squadron.agent.tool.ToolDefinition;
import com.squadron.agent.tool.ToolExecutionContext;
import com.squadron.agent.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchToolTest {

    @Mock
    private WorkspaceClient workspaceClient;

    private SearchTool searchTool;

    @BeforeEach
    void setUp() {
        searchTool = new SearchTool(workspaceClient);
    }

    @Test
    void should_returnToolName() {
        assertEquals("search", searchTool.getName());
    }

    @Test
    void should_returnToolDescription() {
        assertNotNull(searchTool.getDescription());
        assertTrue(searchTool.getDescription().contains("Search for text patterns"));
    }

    @Test
    void should_returnToolDefinition_with_parameters() {
        ToolDefinition definition = searchTool.getDefinition();
        assertNotNull(definition);
        assertEquals("search", definition.getName());
        assertEquals(4, definition.getParameters().size());
        assertEquals("pattern", definition.getParameters().get(0).getName());
        assertTrue(definition.getParameters().get(0).isRequired());
        assertEquals("path", definition.getParameters().get(1).getName());
        assertFalse(definition.getParameters().get(1).isRequired());
        assertEquals("include", definition.getParameters().get(2).getName());
        assertFalse(definition.getParameters().get(2).isRequired());
        assertEquals("maxResults", definition.getParameters().get(3).getName());
        assertFalse(definition.getParameters().get(3).isRequired());
    }

    @Test
    void should_searchFiles_when_validPattern() {
        UUID workspaceId = UUID.randomUUID();

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("pattern", "TODO"))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"),
                eq("grep -rn 'TODO' '/workspace' | head -50")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(0)
                        .stdout("/workspace/Main.java:10:// TODO fix this\n/workspace/App.java:5:// TODO refactor")
                        .stderr("")
                        .build());

        ToolResult result = searchTool.execute(context);

        assertTrue(result.isSuccess());
        assertEquals("search", result.getToolName());
        assertTrue(result.getOutput().contains("TODO fix this"));
        assertTrue(result.getOutput().contains("TODO refactor"));
    }

    @Test
    void should_searchWithInclude_when_fileGlobProvided() {
        UUID workspaceId = UUID.randomUUID();

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("pattern", "class", "include", "*.java"))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"),
                eq("grep -rn --include='*.java' 'class' '/workspace' | head -50")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(0)
                        .stdout("/workspace/Main.java:1:public class Main {}")
                        .stderr("")
                        .build());

        ToolResult result = searchTool.execute(context);

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("public class Main"));
    }

    @Test
    void should_searchWithCustomPath_when_pathProvided() {
        UUID workspaceId = UUID.randomUUID();

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("pattern", "import", "path", "/workspace/src"))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"),
                eq("grep -rn 'import' '/workspace/src' | head -50")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(0)
                        .stdout("/workspace/src/Main.java:1:import java.util.List;")
                        .stderr("")
                        .build());

        ToolResult result = searchTool.execute(context);

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("import java.util.List"));
    }

    @Test
    void should_searchWithCustomMaxResults_when_maxResultsProvided() {
        UUID workspaceId = UUID.randomUUID();

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("pattern", "TODO", "maxResults", 10))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"),
                eq("grep -rn 'TODO' '/workspace' | head -10")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(0)
                        .stdout("match1\nmatch2")
                        .stderr("")
                        .build());

        ToolResult result = searchTool.execute(context);

        assertTrue(result.isSuccess());
    }

    @Test
    void should_returnNoMatches_when_grepFindsNothing() {
        UUID workspaceId = UUID.randomUUID();

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("pattern", "NONEXISTENT_STRING"))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"),
                eq("grep -rn 'NONEXISTENT_STRING' '/workspace' | head -50")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(1) // grep returns 1 when no matches
                        .stdout("")
                        .stderr("")
                        .build());

        ToolResult result = searchTool.execute(context);

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("No matches found"));
    }

    @Test
    void should_returnError_when_patternIsMissing() {
        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of())
                .build();

        ToolResult result = searchTool.execute(context);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("'pattern' is missing"));
    }

    @Test
    void should_returnError_when_patternIsBlank() {
        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("pattern", "  "))
                .build();

        ToolResult result = searchTool.execute(context);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("'pattern' is missing"));
    }

    @Test
    void should_returnError_when_grepExitsWithCodeGreaterThan1() {
        UUID workspaceId = UUID.randomUUID();

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("pattern", "[invalid"))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"),
                eq("grep -rn '[invalid' '/workspace' | head -50")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(2)
                        .stdout("")
                        .stderr("grep: Invalid regular expression")
                        .build());

        ToolResult result = searchTool.execute(context);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Invalid regular expression"));
    }

    @Test
    void should_returnError_when_workspaceClientThrowsException() {
        UUID workspaceId = UUID.randomUUID();

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("pattern", "test"))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"),
                eq("grep -rn 'test' '/workspace' | head -50")))
                .thenThrow(new WorkspaceClient.WorkspaceClientException("Service unavailable"));

        ToolResult result = searchTool.execute(context);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Service unavailable"));
    }

    @Test
    void should_useDefaultMaxResults_when_invalidMaxResultsProvided() {
        assertEquals(50, SearchTool.resolveMaxResults(null));
        assertEquals(50, SearchTool.resolveMaxResults("not_a_number"));
        assertEquals(10, SearchTool.resolveMaxResults(10));
        assertEquals(25, SearchTool.resolveMaxResults("25"));
        assertEquals(100, SearchTool.resolveMaxResults(100L));
    }

    @Test
    void should_useDefaultPath_when_pathNotProvided() {
        UUID workspaceId = UUID.randomUUID();

        Map<String, Object> params = new HashMap<>();
        params.put("pattern", "test");
        params.put("path", null);

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(params)
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"),
                eq("grep -rn 'test' '/workspace' | head -50")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(1)
                        .stdout("")
                        .stderr("")
                        .build());

        ToolResult result = searchTool.execute(context);

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("No matches found"));
    }
}
