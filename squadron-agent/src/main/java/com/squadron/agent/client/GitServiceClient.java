package com.squadron.agent.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "squadron-git", url = "${squadron.git.url}")
public interface GitServiceClient {

    @GetMapping("/api/git/branch-strategies/resolve")
    Map<String, Object> resolveStrategy(@RequestParam("tenantId") String tenantId,
                                         @RequestParam("projectId") String projectId);

    @GetMapping("/api/git/branch-strategies/generate-name")
    Map<String, Object> generateBranchName(@RequestParam("tenantId") String tenantId,
                                            @RequestParam("taskId") String taskId,
                                            @RequestParam("taskTitle") String taskTitle,
                                            @RequestParam("projectId") String projectId);

    @PostMapping("/api/git/pull-requests")
    Map<String, Object> createPullRequest(@RequestBody Map<String, Object> request);

    @GetMapping("/api/git/pull-requests/task/{taskId}")
    Map<String, Object> getPullRequestByTaskId(@PathVariable("taskId") String taskId);

    @GetMapping("/api/git/pull-requests/{id}/mergeability")
    Map<String, Object> checkMergeability(@PathVariable("id") String id);

    @PostMapping("/api/git/pull-requests/{id}/merge")
    Map<String, Object> mergePullRequest(@PathVariable("id") String id);
}
