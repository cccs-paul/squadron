package com.squadron.agent.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UsageSummaryDtoTest {

    @Test
    void should_buildUsageSummaryDto_when_usingBuilder() {
        UsageSummaryDto dto = UsageSummaryDto.builder()
                .totalInputTokens(5000)
                .totalOutputTokens(2500)
                .totalTokens(7500)
                .totalCost(0.05)
                .invocations(3)
                .build();

        assertEquals(5000, dto.getTotalInputTokens());
        assertEquals(2500, dto.getTotalOutputTokens());
        assertEquals(7500, dto.getTotalTokens());
        assertEquals(0.05, dto.getTotalCost(), 0.001);
        assertEquals(3, dto.getInvocations());
    }

    @Test
    void should_createUsageSummaryDto_when_usingNoArgsConstructor() {
        UsageSummaryDto dto = new UsageSummaryDto();

        assertEquals(0, dto.getTotalInputTokens());
        assertEquals(0, dto.getTotalOutputTokens());
        assertEquals(0, dto.getTotalTokens());
        assertEquals(0.0, dto.getTotalCost());
        assertEquals(0, dto.getInvocations());
    }

    @Test
    void should_createUsageSummaryDto_when_usingAllArgsConstructor() {
        UsageSummaryDto dto = new UsageSummaryDto(1000, 500, 1500, 0.01, 2);

        assertEquals(1000, dto.getTotalInputTokens());
        assertEquals(500, dto.getTotalOutputTokens());
        assertEquals(1500, dto.getTotalTokens());
        assertEquals(0.01, dto.getTotalCost(), 0.001);
        assertEquals(2, dto.getInvocations());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        UsageSummaryDto dto = new UsageSummaryDto();

        dto.setTotalInputTokens(3000);
        dto.setTotalOutputTokens(1500);
        dto.setTotalTokens(4500);
        dto.setTotalCost(0.03);
        dto.setInvocations(5);

        assertEquals(3000, dto.getTotalInputTokens());
        assertEquals(1500, dto.getTotalOutputTokens());
        assertEquals(4500, dto.getTotalTokens());
        assertEquals(0.03, dto.getTotalCost(), 0.001);
        assertEquals(5, dto.getInvocations());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UsageSummaryDto d1 = UsageSummaryDto.builder()
                .totalInputTokens(1000).totalOutputTokens(500).totalTokens(1500).totalCost(0.01).invocations(1).build();
        UsageSummaryDto d2 = UsageSummaryDto.builder()
                .totalInputTokens(1000).totalOutputTokens(500).totalTokens(1500).totalCost(0.01).invocations(1).build();

        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        UsageSummaryDto d1 = UsageSummaryDto.builder().totalTokens(1000).invocations(1).build();
        UsageSummaryDto d2 = UsageSummaryDto.builder().totalTokens(2000).invocations(2).build();

        assertNotEquals(d1, d2);
    }

    @Test
    void should_haveToString_when_called() {
        UsageSummaryDto dto = UsageSummaryDto.builder()
                .totalInputTokens(100)
                .totalOutputTokens(50)
                .totalTokens(150)
                .totalCost(0.001)
                .invocations(1)
                .build();

        String toString = dto.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("150"));
        assertTrue(toString.contains("100"));
    }
}
