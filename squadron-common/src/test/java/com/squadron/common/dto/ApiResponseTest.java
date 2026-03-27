package com.squadron.common.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    @Test
    void should_createSuccessResponse_when_successCalled() {
        ApiResponse<String> response = ApiResponse.success("hello");

        assertTrue(response.isSuccess());
        assertEquals("hello", response.getData());
        assertNull(response.getMessage());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void should_createSuccessResponse_when_nullData() {
        ApiResponse<Void> response = ApiResponse.success(null);

        assertTrue(response.isSuccess());
        assertNull(response.getData());
    }

    @Test
    void should_createErrorResponse_when_errorCalled() {
        ApiResponse<Void> response = ApiResponse.error("Something went wrong");

        assertFalse(response.isSuccess());
        assertNull(response.getData());
        assertEquals("Something went wrong", response.getMessage());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void should_supportGenericTypes_when_usedWithDifferentTypes() {
        ApiResponse<Integer> intResponse = ApiResponse.success(42);
        assertEquals(42, intResponse.getData());

        ApiResponse<java.util.List<String>> listResponse = ApiResponse.success(java.util.List.of("a", "b"));
        assertEquals(2, listResponse.getData().size());
    }

    @Test
    void should_buildWithAllFields_when_builderUsed() {
        var now = java.time.Instant.now();
        ApiResponse<String> response = ApiResponse.<String>builder()
                .success(true)
                .data("test")
                .message("ok")
                .timestamp(now)
                .build();

        assertTrue(response.isSuccess());
        assertEquals("test", response.getData());
        assertEquals("ok", response.getMessage());
        assertEquals(now, response.getTimestamp());
    }
}
