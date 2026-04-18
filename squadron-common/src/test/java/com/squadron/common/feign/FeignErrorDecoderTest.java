package com.squadron.common.feign;

import com.squadron.common.exception.ResourceNotFoundException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class FeignErrorDecoderTest {

    private FeignErrorDecoder decoder;

    @BeforeEach
    void setUp() {
        decoder = new FeignErrorDecoder();
    }

    @Test
    void should_returnResourceNotFoundException_when_status404() {
        Response response = buildResponse(404);

        Exception ex = decoder.decode("SomeClient#someMethod()", response);

        assertInstanceOf(ResourceNotFoundException.class, ex);
        assertTrue(ex.getMessage().contains("Resource not found"));
        assertTrue(ex.getMessage().contains("SomeClient#someMethod()"));
    }

    @Test
    void should_returnIllegalStateException_when_status409() {
        Response response = buildResponse(409);

        Exception ex = decoder.decode("SomeClient#conflictMethod()", response);

        assertInstanceOf(IllegalStateException.class, ex);
        assertTrue(ex.getMessage().contains("Conflict"));
        assertTrue(ex.getMessage().contains("SomeClient#conflictMethod()"));
    }

    @Test
    void should_returnSecurityException_when_status401() {
        Response response = buildResponse(401);

        Exception ex = decoder.decode("SomeClient#authMethod()", response);

        assertInstanceOf(SecurityException.class, ex);
        assertTrue(ex.getMessage().contains("Unauthorized"));
        assertTrue(ex.getMessage().contains("SomeClient#authMethod()"));
    }

    @Test
    void should_returnSecurityException_when_status403() {
        Response response = buildResponse(403);

        Exception ex = decoder.decode("SomeClient#forbiddenMethod()", response);

        assertInstanceOf(SecurityException.class, ex);
        assertTrue(ex.getMessage().contains("Forbidden"));
        assertTrue(ex.getMessage().contains("SomeClient#forbiddenMethod()"));
    }

    @Test
    void should_delegateToDefaultDecoder_when_otherStatus() {
        Response response = buildResponse(500);

        Exception ex = decoder.decode("SomeClient#errorMethod()", response);

        assertNotNull(ex);
        assertFalse(ex instanceof ResourceNotFoundException);
        assertFalse(ex instanceof IllegalStateException);
        assertFalse(ex instanceof SecurityException);
    }

    @Test
    void should_delegateToDefaultDecoder_when_status400() {
        Response response = buildResponse(400);

        Exception ex = decoder.decode("SomeClient#badRequest()", response);

        assertNotNull(ex);
        assertFalse(ex instanceof ResourceNotFoundException);
        assertFalse(ex instanceof IllegalStateException);
        assertFalse(ex instanceof SecurityException);
    }

    @Test
    void should_delegateToDefaultDecoder_when_status503() {
        Response response = buildResponse(503);

        Exception ex = decoder.decode("SomeClient#unavailable()", response);

        assertNotNull(ex);
        assertFalse(ex instanceof ResourceNotFoundException);
        assertFalse(ex instanceof IllegalStateException);
        assertFalse(ex instanceof SecurityException);
    }

    private Response buildResponse(int status) {
        Request request = Request.create(
                Request.HttpMethod.GET,
                "http://localhost",
                Collections.emptyMap(),
                null,
                null,
                null
        );
        return Response.builder()
                .status(status)
                .reason("Test")
                .request(request)
                .headers(Collections.emptyMap())
                .build();
    }
}
