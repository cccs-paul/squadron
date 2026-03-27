package com.squadron.common.dto;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PagedResponseTest {

    @Test
    void should_buildWithAllFields_when_builderUsed() {
        List<String> content = List.of("item1", "item2", "item3");

        PagedResponse<String> response = PagedResponse.<String>builder()
                .content(content)
                .page(0)
                .size(10)
                .totalElements(3L)
                .totalPages(1)
                .build();

        assertEquals(content, response.getContent());
        assertEquals(0, response.getPage());
        assertEquals(10, response.getSize());
        assertEquals(3L, response.getTotalElements());
        assertEquals(1, response.getTotalPages());
    }

    @Test
    void should_createEmptyInstance_when_noArgsConstructorUsed() {
        PagedResponse<String> response = new PagedResponse<>();

        assertNull(response.getContent());
        assertEquals(0, response.getPage());
        assertEquals(0, response.getSize());
        assertEquals(0L, response.getTotalElements());
        assertEquals(0, response.getTotalPages());
    }

    @Test
    void should_createInstance_when_allArgsConstructorUsed() {
        List<Integer> content = List.of(1, 2, 3, 4, 5);

        PagedResponse<Integer> response = new PagedResponse<>(content, 2, 5, 25L, 5);

        assertEquals(content, response.getContent());
        assertEquals(2, response.getPage());
        assertEquals(5, response.getSize());
        assertEquals(25L, response.getTotalElements());
        assertEquals(5, response.getTotalPages());
    }

    @Test
    void should_setAndGetFields_when_settersCalled() {
        PagedResponse<String> response = new PagedResponse<>();
        List<String> content = List.of("a", "b");

        response.setContent(content);
        response.setPage(1);
        response.setSize(20);
        response.setTotalElements(42L);
        response.setTotalPages(3);

        assertEquals(content, response.getContent());
        assertEquals(1, response.getPage());
        assertEquals(20, response.getSize());
        assertEquals(42L, response.getTotalElements());
        assertEquals(3, response.getTotalPages());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        List<String> content = List.of("x", "y");

        PagedResponse<String> response1 = PagedResponse.<String>builder()
                .content(content)
                .page(0)
                .size(10)
                .totalElements(2L)
                .totalPages(1)
                .build();

        PagedResponse<String> response2 = PagedResponse.<String>builder()
                .content(content)
                .page(0)
                .size(10)
                .totalElements(2L)
                .totalPages(1)
                .build();

        assertEquals(response1, response2);
        assertEquals(response1.hashCode(), response2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        PagedResponse<String> response1 = PagedResponse.<String>builder()
                .page(0)
                .totalElements(10L)
                .build();

        PagedResponse<String> response2 = PagedResponse.<String>builder()
                .page(1)
                .totalElements(20L)
                .build();

        assertNotEquals(response1, response2);
    }

    @Test
    void should_includeFieldsInToString_when_toStringCalled() {
        PagedResponse<String> response = PagedResponse.<String>builder()
                .content(List.of("hello"))
                .page(3)
                .size(25)
                .build();

        String str = response.toString();
        assertTrue(str.contains("hello"));
        assertTrue(str.contains("3"));
        assertTrue(str.contains("25"));
    }

    @Test
    void should_handleNullContent_when_contentIsNull() {
        PagedResponse<String> response = PagedResponse.<String>builder()
                .content(null)
                .page(0)
                .size(10)
                .totalElements(0L)
                .totalPages(0)
                .build();

        assertNull(response.getContent());
    }

    @Test
    void should_handleEmptyContent_when_emptyListProvided() {
        PagedResponse<String> response = PagedResponse.<String>builder()
                .content(Collections.emptyList())
                .page(0)
                .size(10)
                .totalElements(0L)
                .totalPages(0)
                .build();

        assertNotNull(response.getContent());
        assertTrue(response.getContent().isEmpty());
    }

    @Test
    void should_supportGenericTypes_when_usedWithDifferentTypes() {
        PagedResponse<Integer> intResponse = PagedResponse.<Integer>builder()
                .content(List.of(1, 2, 3))
                .totalElements(3L)
                .build();
        assertEquals(3, intResponse.getContent().size());

        PagedResponse<UserDto> dtoResponse = PagedResponse.<UserDto>builder()
                .content(List.of(UserDto.builder().email("a@b.com").build()))
                .totalElements(1L)
                .build();
        assertEquals(1, dtoResponse.getContent().size());
        assertEquals("a@b.com", dtoResponse.getContent().get(0).getEmail());
    }
}
