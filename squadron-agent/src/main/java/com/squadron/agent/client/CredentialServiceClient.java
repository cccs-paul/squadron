package com.squadron.agent.client;

import com.squadron.common.dto.ApiResponse;
import com.squadron.common.dto.CredentialResolutionResult;
import com.squadron.common.dto.ResolveCredentialRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "squadron-platform-credentials", url = "${squadron.platform.service-url}")
public interface CredentialServiceClient {

    @PostMapping("/api/platforms/credentials/resolve")
    ApiResponse<CredentialResolutionResult> resolveCredentials(@RequestBody ResolveCredentialRequest request);
}
