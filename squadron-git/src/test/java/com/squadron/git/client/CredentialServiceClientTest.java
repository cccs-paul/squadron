package com.squadron.git.client;

import com.squadron.common.dto.ApiResponse;
import com.squadron.common.dto.CredentialResolutionResult;
import com.squadron.common.dto.ResolveCredentialRequest;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.junit.jupiter.api.Assertions.*;

class CredentialServiceClientTest {

    @Test
    void should_haveFeignClientAnnotation() {
        FeignClient annotation = CredentialServiceClient.class.getAnnotation(FeignClient.class);

        assertNotNull(annotation, "CredentialServiceClient must have @FeignClient annotation");
        assertEquals("squadron-platform-credentials", annotation.name());
        assertEquals("${squadron.platform.service-url}", annotation.url());
    }

    @Test
    void should_haveResolveCredentialsMethod_withPostMapping() throws NoSuchMethodException {
        Method method = CredentialServiceClient.class.getMethod("resolveCredentials",
                ResolveCredentialRequest.class);

        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        assertNotNull(postMapping, "resolveCredentials must have @PostMapping");
        assertArrayEquals(new String[]{"/api/platforms/credentials/resolve"}, postMapping.value());
    }

    @Test
    void should_haveRequestBodyAnnotation_onResolveCredentialsParameter() throws NoSuchMethodException {
        Method method = CredentialServiceClient.class.getMethod("resolveCredentials",
                ResolveCredentialRequest.class);

        Parameter param = method.getParameters()[0];
        RequestBody requestBody = param.getAnnotation(RequestBody.class);
        assertNotNull(requestBody, "resolveCredentials parameter must have @RequestBody annotation");
    }
}
