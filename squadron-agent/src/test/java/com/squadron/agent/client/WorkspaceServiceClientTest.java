package com.squadron.agent.client;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceServiceClientTest {

    @Test
    void should_haveFeignClientAnnotation() {
        FeignClient annotation = WorkspaceServiceClient.class.getAnnotation(FeignClient.class);

        assertNotNull(annotation, "WorkspaceServiceClient must have @FeignClient annotation");
        assertEquals("squadron-workspace", annotation.name());
        assertEquals("${squadron.workspace.url}", annotation.url());
    }

    @Test
    void should_haveExecMethod_withPostMapping() throws NoSuchMethodException {
        Method method = WorkspaceServiceClient.class.getMethod("exec",
                String.class, java.util.Map.class);

        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        assertNotNull(postMapping, "exec must have @PostMapping");
        assertArrayEquals(new String[]{"/api/workspaces/{workspaceId}/exec"}, postMapping.value());

        PathVariable pathVariable = method.getParameters()[0].getAnnotation(PathVariable.class);
        assertNotNull(pathVariable);
        assertEquals("workspaceId", pathVariable.value());

        RequestBody requestBody = method.getParameters()[1].getAnnotation(RequestBody.class);
        assertNotNull(requestBody);
    }

    @Test
    void should_haveWriteFileMethod_withPostMapping() throws NoSuchMethodException {
        Method method = WorkspaceServiceClient.class.getMethod("writeFile",
                String.class, String.class, byte[].class);

        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        assertNotNull(postMapping, "writeFile must have @PostMapping");
        assertArrayEquals(new String[]{"/api/workspaces/{workspaceId}/copy-to"}, postMapping.value());

        PathVariable pathVariable = method.getParameters()[0].getAnnotation(PathVariable.class);
        assertNotNull(pathVariable);
        assertEquals("workspaceId", pathVariable.value());

        RequestParam pathParam = method.getParameters()[1].getAnnotation(RequestParam.class);
        assertNotNull(pathParam);
        assertEquals("path", pathParam.value());

        RequestBody requestBody = method.getParameters()[2].getAnnotation(RequestBody.class);
        assertNotNull(requestBody);
    }

    @Test
    void should_haveReadFileMethod_withGetMapping() throws NoSuchMethodException {
        Method method = WorkspaceServiceClient.class.getMethod("readFile",
                String.class, String.class);

        GetMapping getMapping = method.getAnnotation(GetMapping.class);
        assertNotNull(getMapping, "readFile must have @GetMapping");
        assertArrayEquals(new String[]{"/api/workspaces/{workspaceId}/copy-from"}, getMapping.value());

        PathVariable pathVariable = method.getParameters()[0].getAnnotation(PathVariable.class);
        assertNotNull(pathVariable);
        assertEquals("workspaceId", pathVariable.value());

        RequestParam pathParam = method.getParameters()[1].getAnnotation(RequestParam.class);
        assertNotNull(pathParam);
        assertEquals("path", pathParam.value());
    }
}
