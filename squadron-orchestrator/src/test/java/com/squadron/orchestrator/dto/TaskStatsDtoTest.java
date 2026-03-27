package com.squadron.orchestrator.dto;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TaskStatsDtoTest {

    @Test
    void should_createWithBuilder() {
        Map<String, Long> byState = Map.of("OPEN", 5L, "IN_PROGRESS", 3L, "DONE", 10L);
        Map<String, Long> byPriority = Map.of("HIGH", 2L, "MEDIUM", 8L, "LOW", 8L);

        TaskStatsDto dto = TaskStatsDto.builder()
                .total(18)
                .byState(byState)
                .byPriority(byPriority)
                .build();

        assertEquals(18, dto.getTotal());
        assertEquals(byState, dto.getByState());
        assertEquals(byPriority, dto.getByPriority());
    }

    @Test
    void should_createWithNoArgsConstructor() {
        TaskStatsDto dto = new TaskStatsDto();
        assertEquals(0, dto.getTotal());
        assertNull(dto.getByState());
        assertNull(dto.getByPriority());
    }

    @Test
    void should_setAndGetFields() {
        TaskStatsDto dto = new TaskStatsDto();
        Map<String, Long> byState = Map.of("OPEN", 10L);
        Map<String, Long> byPriority = Map.of("HIGH", 4L);

        dto.setTotal(10);
        dto.setByState(byState);
        dto.setByPriority(byPriority);

        assertEquals(10, dto.getTotal());
        assertEquals(byState, dto.getByState());
        assertEquals(byPriority, dto.getByPriority());
    }

    @Test
    void should_implementEquals() {
        Map<String, Long> byState = Map.of("OPEN", 5L);
        Map<String, Long> byPriority = Map.of("HIGH", 2L);

        TaskStatsDto d1 = TaskStatsDto.builder().total(5).byState(byState).byPriority(byPriority).build();
        TaskStatsDto d2 = TaskStatsDto.builder().total(5).byState(byState).byPriority(byPriority).build();

        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentTotal() {
        TaskStatsDto d1 = TaskStatsDto.builder().total(5).build();
        TaskStatsDto d2 = TaskStatsDto.builder().total(10).build();

        assertNotEquals(d1, d2);
    }

    @Test
    void should_notBeEqual_when_differentByState() {
        TaskStatsDto d1 = TaskStatsDto.builder().total(5).byState(Map.of("OPEN", 5L)).build();
        TaskStatsDto d2 = TaskStatsDto.builder().total(5).byState(Map.of("DONE", 5L)).build();

        assertNotEquals(d1, d2);
    }

    @Test
    void should_haveToString() {
        TaskStatsDto dto = TaskStatsDto.builder().total(42).build();
        String str = dto.toString();
        assertNotNull(str);
        assertTrue(str.contains("42"));
    }

    @Test
    void should_createWithAllArgsConstructor() {
        Map<String, Long> byState = Map.of("OPEN", 3L);
        Map<String, Long> byPriority = Map.of("LOW", 3L);

        TaskStatsDto dto = new TaskStatsDto(3, byState, byPriority);

        assertEquals(3, dto.getTotal());
        assertEquals(byState, dto.getByState());
        assertEquals(byPriority, dto.getByPriority());
    }

    @Test
    void should_handleEmptyMaps() {
        TaskStatsDto dto = TaskStatsDto.builder()
                .total(0)
                .byState(Collections.emptyMap())
                .byPriority(Collections.emptyMap())
                .build();

        assertEquals(0, dto.getTotal());
        assertTrue(dto.getByState().isEmpty());
        assertTrue(dto.getByPriority().isEmpty());
    }

    @Test
    void should_handleNullMaps() {
        TaskStatsDto dto = TaskStatsDto.builder()
                .total(0)
                .byState(null)
                .byPriority(null)
                .build();

        assertNull(dto.getByState());
        assertNull(dto.getByPriority());
    }
}
