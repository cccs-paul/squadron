package com.squadron.agent.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "squadron-review", url = "${squadron.review.url}")
public interface ReviewServiceClient {

    @PostMapping("/api/reviews")
    Map<String, Object> createReview(@RequestBody Map<String, Object> request);

    @PostMapping("/api/reviews/submit")
    Map<String, Object> submitReview(@RequestBody Map<String, Object> request);

    @GetMapping("/api/reviews/task/{taskId}/gate")
    Map<String, Object> checkReviewGate(@PathVariable("taskId") String taskId,
                                         @RequestParam("tenantId") String tenantId,
                                         @RequestParam("teamId") String teamId);
}
