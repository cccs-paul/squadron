package com.squadron.agent.client;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class OrchestratorClientTest {

    @Test
    void should_haveFeignClientAnnotation() {
        FeignClient annotation = OrchestratorClient.class.getAnnotation(FeignClient.class);

        assertNotNull(annotation, "OrchestratorClient must have @FeignClient annotation");
        assertEquals("squadron-orchestrator", annotation.name());
        assertEquals("${squadron.orchestrator.url}", annotation.url());
    }

    @Test
    void should_haveTransitionTaskMethod_withPostMapping() throws NoSuchMethodException {
        Method method = OrchestratorClient.class.getMethod("transitionTask", String.class, java.util.Map.class);

        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        assertNotNull(postMapping, "transitionTask must have @PostMapping");
        assertArrayEquals(new String[]{"/api/tasks/{taskId}/transition"}, postMapping.value());

        PathVariable pathVariable = method.getParameters()[0].getAnnotation(PathVariable.class);
        assertNotNull(pathVariable, "taskId parameter must have @PathVariable");
        assertEquals("taskId", pathVariable.value());

        RequestBody requestBody = method.getParameters()[1].getAnnotation(RequestBody.class);
        assertNotNull(requestBody, "request parameter must have @RequestBody");
    }
}
