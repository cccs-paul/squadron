package com.squadron.workspace.client;

import com.squadron.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * Feign client for inter-service communication with squadron-platform.
 * Used to retrieve SSH keys for Git operations.
 */
@FeignClient(name = "squadron-platform", url = "${squadron.platform.url}")
public interface PlatformServiceClient {

    @GetMapping("/api/platforms/ssh-keys/{id}/private-key")
    ApiResponse<String> getDecryptedPrivateKey(@PathVariable("id") UUID sshKeyId);
}
