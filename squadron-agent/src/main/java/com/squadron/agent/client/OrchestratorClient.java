package com.squadron.agent.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "squadron-orchestrator", url = "${squadron.orchestrator.url}")
public interface OrchestratorClient {

    @PostMapping("/api/tasks/{taskId}/transition")
    Map<String, Object> transitionTask(@PathVariable("taskId") String taskId,
                                        @RequestBody Map<String, Object> request);

    @PutMapping("/api/tasks/{taskId}/ticketless-status")
    Map<String, Object> updateTicketlessStatus(@PathVariable("taskId") String taskId,
                                                @RequestBody Map<String, String> request);
}
