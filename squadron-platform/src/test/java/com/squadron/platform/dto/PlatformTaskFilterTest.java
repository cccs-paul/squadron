package com.squadron.platform.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlatformTaskFilterTest {

    @Test
    void should_buildWithAllFields() {
        PlatformTaskFilter filter = PlatformTaskFilter.builder()
                .projectKey("PROJ")
                .status("OPEN")
                .assignee("john.doe")
                .maxResults(100)
                .build();

        assertEquals("PROJ", filter.getProjectKey());
        assertEquals("OPEN", filter.getStatus());
        assertEquals("john.doe", filter.getAssignee());
        assertEquals(100, filter.getMaxResults());
    }

    @Test
    void should_haveDefaultMaxResults() {
        PlatformTaskFilter filter = PlatformTaskFilter.builder()
                .projectKey("PROJ")
                .build();

        assertEquals(50, filter.getMaxResults());
    }

    @Test
    void should_supportNoArgsConstructor() {
        PlatformTaskFilter filter = new PlatformTaskFilter();
        assertNull(filter.getProjectKey());
        assertEquals(50, filter.getMaxResults());
    }

    @Test
    void should_supportSetters() {
        PlatformTaskFilter filter = new PlatformTaskFilter();
        filter.setProjectKey("MY-PROJ");
        filter.setStatus("CLOSED");
        filter.setAssignee("jane.doe");
        filter.setMaxResults(25);

        assertEquals("MY-PROJ", filter.getProjectKey());
        assertEquals("CLOSED", filter.getStatus());
        assertEquals("jane.doe", filter.getAssignee());
        assertEquals(25, filter.getMaxResults());
    }

    @Test
    void should_implementEqualsAndHashCode() {
        PlatformTaskFilter f1 = PlatformTaskFilter.builder()
                .projectKey("PROJ")
                .status("OPEN")
                .build();
        PlatformTaskFilter f2 = PlatformTaskFilter.builder()
                .projectKey("PROJ")
                .status("OPEN")
                .build();

        assertEquals(f1, f2);
        assertEquals(f1.hashCode(), f2.hashCode());
    }

    @Test
    void should_implementToString() {
        PlatformTaskFilter filter = PlatformTaskFilter.builder()
                .projectKey("PROJ")
                .build();
        assertNotNull(filter.toString());
        assertTrue(filter.toString().contains("PROJ"));
    }
}
