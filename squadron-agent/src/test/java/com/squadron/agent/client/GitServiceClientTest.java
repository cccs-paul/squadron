package com.squadron.agent.client;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class GitServiceClientTest {

    @Test
    void should_haveFeignClientAnnotation() {
        FeignClient annotation = GitServiceClient.class.getAnnotation(FeignClient.class);

        assertNotNull(annotation, "GitServiceClient must have @FeignClient annotation");
        assertEquals("squadron-git", annotation.name());
        assertEquals("${squadron.git.url}", annotation.url());
    }

    @Test
    void should_haveResolveStrategyMethod_withGetMapping() throws NoSuchMethodException {
        Method method = GitServiceClient.class.getMethod("resolveStrategy",
                String.class, String.class);

        GetMapping getMapping = method.getAnnotation(GetMapping.class);
        assertNotNull(getMapping, "resolveStrategy must have @GetMapping");
        assertArrayEquals(new String[]{"/api/git/branch-strategies/resolve"}, getMapping.value());

        RequestParam tenantParam = method.getParameters()[0].getAnnotation(RequestParam.class);
        assertNotNull(tenantParam);
        assertEquals("tenantId", tenantParam.value());

        RequestParam projectParam = method.getParameters()[1].getAnnotation(RequestParam.class);
        assertNotNull(projectParam);
        assertEquals("projectId", projectParam.value());
    }

    @Test
    void should_haveGenerateBranchNameMethod_withGetMapping() throws NoSuchMethodException {
        Method method = GitServiceClient.class.getMethod("generateBranchName",
                String.class, String.class, String.class, String.class);

        GetMapping getMapping = method.getAnnotation(GetMapping.class);
        assertNotNull(getMapping, "generateBranchName must have @GetMapping");
        assertArrayEquals(new String[]{"/api/git/branch-strategies/generate-name"}, getMapping.value());
    }

    @Test
    void should_haveCreatePullRequestMethod_withPostMapping() throws NoSuchMethodException {
        Method method = GitServiceClient.class.getMethod("createPullRequest", java.util.Map.class);

        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        assertNotNull(postMapping, "createPullRequest must have @PostMapping");
        assertArrayEquals(new String[]{"/api/git/pull-requests"}, postMapping.value());
    }

    @Test
    void should_haveGetPullRequestByTaskIdMethod_withGetMapping() throws NoSuchMethodException {
        Method method = GitServiceClient.class.getMethod("getPullRequestByTaskId", String.class);

        GetMapping getMapping = method.getAnnotation(GetMapping.class);
        assertNotNull(getMapping);
        assertArrayEquals(new String[]{"/api/git/pull-requests/task/{taskId}"}, getMapping.value());

        PathVariable pathVariable = method.getParameters()[0].getAnnotation(PathVariable.class);
        assertNotNull(pathVariable);
        assertEquals("taskId", pathVariable.value());
    }

    @Test
    void should_haveCheckMergeabilityMethod_withGetMapping() throws NoSuchMethodException {
        Method method = GitServiceClient.class.getMethod("checkMergeability", String.class);

        GetMapping getMapping = method.getAnnotation(GetMapping.class);
        assertNotNull(getMapping);
        assertArrayEquals(new String[]{"/api/git/pull-requests/{id}/mergeability"}, getMapping.value());

        PathVariable pathVariable = method.getParameters()[0].getAnnotation(PathVariable.class);
        assertNotNull(pathVariable);
        assertEquals("id", pathVariable.value());
    }

    @Test
    void should_haveMergePullRequestMethod_withPostMapping() throws NoSuchMethodException {
        Method method = GitServiceClient.class.getMethod("mergePullRequest", String.class);

        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        assertNotNull(postMapping);
        assertArrayEquals(new String[]{"/api/git/pull-requests/{id}/merge"}, postMapping.value());

        PathVariable pathVariable = method.getParameters()[0].getAnnotation(PathVariable.class);
        assertNotNull(pathVariable);
        assertEquals("id", pathVariable.value());
    }
}
