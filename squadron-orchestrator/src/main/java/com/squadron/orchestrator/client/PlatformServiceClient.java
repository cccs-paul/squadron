package com.squadron.orchestrator.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "squadron-platform", url = "${squadron.platform.service-url}")
public interface PlatformServiceClient {

    @GetMapping("/api/platform-sync/{connectionId}/tasks")
    List<Map<String, Object>> fetchTasks(@PathVariable("connectionId") String connectionId,
                                          @RequestParam("projectKey") String projectKey);
}
