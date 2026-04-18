package com.squadron.common.feign;

import com.squadron.common.exception.ResourceNotFoundException;
import feign.Response;
import feign.codec.ErrorDecoder;

public class FeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.status() == 401) {
            return new SecurityException("Unauthorized: " + methodKey);
        }
        if (response.status() == 403) {
            return new SecurityException("Forbidden: " + methodKey);
        }
        if (response.status() == 404) {
            return new ResourceNotFoundException("Resource not found: " + methodKey);
        }
        if (response.status() == 409) {
            return new IllegalStateException("Conflict: " + methodKey);
        }
        return defaultDecoder.decode(methodKey, response);
    }
}
