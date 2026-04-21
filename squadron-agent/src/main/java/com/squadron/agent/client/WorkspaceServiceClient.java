package com.squadron.agent.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "squadron-workspace", url = "${squadron.workspace.url}")
public interface WorkspaceServiceClient {

    @PostMapping("/api/workspaces")
    Map<String, Object> createWorkspace(@RequestBody Map<String, Object> request);

    @GetMapping("/api/workspaces/{workspaceId}")
    Map<String, Object> getWorkspace(@PathVariable("workspaceId") String workspaceId);

    @DeleteMapping("/api/workspaces/{workspaceId}")
    void destroyWorkspace(@PathVariable("workspaceId") String workspaceId);

    @PostMapping("/api/workspaces/{workspaceId}/exec")
    Map<String, Object> exec(@PathVariable("workspaceId") String workspaceId,
                              @RequestBody Map<String, Object> request);

    @PostMapping("/api/workspaces/{workspaceId}/copy-to")
    void writeFile(@PathVariable("workspaceId") String workspaceId,
                   @RequestParam("path") String path,
                   @RequestBody byte[] content);

    @GetMapping("/api/workspaces/{workspaceId}/copy-from")
    byte[] readFile(@PathVariable("workspaceId") String workspaceId,
                    @RequestParam("path") String path);
}
