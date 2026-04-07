package com.squadron.orchestrator.client;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class PlatformServiceClientTest {

    @Test
    void should_haveFeignClientAnnotation() {
        FeignClient annotation = PlatformServiceClient.class.getAnnotation(FeignClient.class);

        assertNotNull(annotation, "PlatformServiceClient must have @FeignClient annotation");
        assertEquals("squadron-platform", annotation.name());
        assertEquals("${squadron.platform.service-url}", annotation.url());
    }

    @Test
    void should_haveFetchTasksMethod_withPostMapping() throws NoSuchMethodException {
        Method method = PlatformServiceClient.class.getMethod("fetchTasks",
                String.class, String.class);

        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        assertNotNull(postMapping, "fetchTasks must have @PostMapping");
        assertArrayEquals(new String[]{"/api/platforms/sync/{connectionId}/tasks"}, postMapping.value());

        PathVariable pathVariable = method.getParameters()[0].getAnnotation(PathVariable.class);
        assertNotNull(pathVariable, "connectionId parameter must have @PathVariable");
        assertEquals("connectionId", pathVariable.value());

        RequestParam projectKeyParam = method.getParameters()[1].getAnnotation(RequestParam.class);
        assertNotNull(projectKeyParam, "projectKey parameter must have @RequestParam");
        assertEquals("projectKey", projectKeyParam.value());
    }
}
