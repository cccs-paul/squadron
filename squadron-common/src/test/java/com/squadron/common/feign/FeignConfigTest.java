package com.squadron.common.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Target;
import feign.codec.ErrorDecoder;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeignConfigTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void should_createFeignErrorDecoderBean() {
        FeignConfig config = new FeignConfig();

        ErrorDecoder decoder = config.feignErrorDecoder();

        assertNotNull(decoder);
        assertInstanceOf(FeignErrorDecoder.class, decoder);
    }

    @Test
    void should_createAuthorizationForwardingInterceptor() {
        FeignConfig config = new FeignConfig();

        RequestInterceptor interceptor = config.authorizationForwardingInterceptor();

        assertNotNull(interceptor);
    }

    @Test
    void should_forwardAuthorizationHeader_when_bearerTokenPresent() {
        FeignConfig config = new FeignConfig();
        RequestInterceptor interceptor = config.authorizationForwardingInterceptor();

        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getHeader("Authorization")).thenReturn("Bearer jwt-token-value");
        ServletRequestAttributes attributes = new ServletRequestAttributes(mockRequest);
        RequestContextHolder.setRequestAttributes(attributes);

        RequestTemplate template = new RequestTemplate();
        Target<?> target = mock(Target.class);
        when(target.url()).thenReturn("http://localhost:8084");
        template.feignTarget(target);

        interceptor.apply(template);

        assertTrue(template.headers().containsKey("Authorization"));
        assertTrue(template.headers().get("Authorization").contains("Bearer jwt-token-value"));
    }

    @Test
    void should_notAddHeader_when_noAuthorizationHeader() {
        FeignConfig config = new FeignConfig();
        RequestInterceptor interceptor = config.authorizationForwardingInterceptor();

        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getHeader("Authorization")).thenReturn(null);
        ServletRequestAttributes attributes = new ServletRequestAttributes(mockRequest);
        RequestContextHolder.setRequestAttributes(attributes);

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertFalse(template.headers().containsKey("Authorization"));
    }

    @Test
    void should_notAddHeader_when_nonBearerAuthorization() {
        FeignConfig config = new FeignConfig();
        RequestInterceptor interceptor = config.authorizationForwardingInterceptor();

        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");
        ServletRequestAttributes attributes = new ServletRequestAttributes(mockRequest);
        RequestContextHolder.setRequestAttributes(attributes);

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertFalse(template.headers().containsKey("Authorization"));
    }

    @Test
    void should_notAddHeader_when_noRequestContext() {
        FeignConfig config = new FeignConfig();
        RequestInterceptor interceptor = config.authorizationForwardingInterceptor();

        // No RequestContextHolder set — simulates NATS-triggered or scheduled calls
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertFalse(template.headers().containsKey("Authorization"));
    }
}
