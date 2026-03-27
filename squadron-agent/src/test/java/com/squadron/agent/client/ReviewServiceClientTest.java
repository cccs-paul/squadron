package com.squadron.agent.client;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class ReviewServiceClientTest {

    @Test
    void should_haveFeignClientAnnotation() {
        FeignClient annotation = ReviewServiceClient.class.getAnnotation(FeignClient.class);

        assertNotNull(annotation, "ReviewServiceClient must have @FeignClient annotation");
        assertEquals("squadron-review", annotation.name());
        assertEquals("${squadron.review.url}", annotation.url());
    }

    @Test
    void should_haveCreateReviewMethod_withPostMapping() throws NoSuchMethodException {
        Method method = ReviewServiceClient.class.getMethod("createReview", java.util.Map.class);

        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        assertNotNull(postMapping, "createReview must have @PostMapping");
        assertArrayEquals(new String[]{"/api/reviews"}, postMapping.value());

        RequestBody requestBody = method.getParameters()[0].getAnnotation(RequestBody.class);
        assertNotNull(requestBody, "request parameter must have @RequestBody");
    }

    @Test
    void should_haveSubmitReviewMethod_withPostMapping() throws NoSuchMethodException {
        Method method = ReviewServiceClient.class.getMethod("submitReview", java.util.Map.class);

        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        assertNotNull(postMapping, "submitReview must have @PostMapping");
        assertArrayEquals(new String[]{"/api/reviews/submit"}, postMapping.value());
    }

    @Test
    void should_haveCheckReviewGateMethod_withGetMapping() throws NoSuchMethodException {
        Method method = ReviewServiceClient.class.getMethod("checkReviewGate",
                String.class, String.class, String.class);

        GetMapping getMapping = method.getAnnotation(GetMapping.class);
        assertNotNull(getMapping, "checkReviewGate must have @GetMapping");
        assertArrayEquals(new String[]{"/api/reviews/task/{taskId}/gate"}, getMapping.value());

        PathVariable pathVariable = method.getParameters()[0].getAnnotation(PathVariable.class);
        assertNotNull(pathVariable, "taskId parameter must have @PathVariable");
        assertEquals("taskId", pathVariable.value());

        RequestParam tenantParam = method.getParameters()[1].getAnnotation(RequestParam.class);
        assertNotNull(tenantParam, "tenantId parameter must have @RequestParam");
        assertEquals("tenantId", tenantParam.value());

        RequestParam teamParam = method.getParameters()[2].getAnnotation(RequestParam.class);
        assertNotNull(teamParam, "teamId parameter must have @RequestParam");
        assertEquals("teamId", teamParam.value());
    }
}
