package com.squadron.common.feign;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignConfig {

    private static final Logger log = LoggerFactory.getLogger(FeignConfig.class);

    @Bean
    public ErrorDecoder feignErrorDecoder() {
        return new FeignErrorDecoder();
    }

    /**
     * Forwards the Authorization header from the current HTTP request to downstream Feign calls.
     * This ensures inter-service calls carry the user's JWT when triggered from authenticated endpoints.
     * If there is no current HTTP request (e.g., NATS-triggered or scheduled calls), no header is added.
     */
    @Bean
    public RequestInterceptor authorizationForwardingInterceptor() {
        return requestTemplate -> {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    requestTemplate.header("Authorization", authHeader);
                    log.debug("Forwarded Authorization header to Feign request: {}",
                            requestTemplate.feignTarget().url());
                }
            }
        };
    }
}
