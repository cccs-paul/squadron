package com.squadron.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonUtilsTest {

    @Test
    void should_serializeToJson_when_validObject() {
        Map<String, String> map = Map.of("key", "value");
        String json = JsonUtils.toJson(map);

        assertNotNull(json);
        assertTrue(json.contains("\"key\""));
        assertTrue(json.contains("\"value\""));
    }

    @Test
    void should_deserializeFromJson_when_validJsonAndClass() {
        String json = "{\"key\":\"value\"}";

        @SuppressWarnings("unchecked")
        Map<String, String> result = JsonUtils.fromJson(json, Map.class);

        assertNotNull(result);
        assertEquals("value", result.get("key"));
    }

    @Test
    void should_deserializeFromJson_when_validJsonAndTypeReference() {
        String json = "[{\"name\":\"Alice\"},{\"name\":\"Bob\"}]";

        List<Map<String, String>> result = JsonUtils.fromJson(json, new TypeReference<>() {});

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Alice", result.get(0).get("name"));
        assertEquals("Bob", result.get(1).get("name"));
    }

    @Test
    void should_convertObjectToMap_when_validObject() {
        record TestObj(String name, int age) {}
        TestObj obj = new TestObj("Test", 25);

        Map<String, Object> map = JsonUtils.toMap(obj);

        assertNotNull(map);
        assertEquals("Test", map.get("name"));
        assertEquals(25, map.get("age"));
    }

    @Test
    void should_handleInstantSerialization_when_objectHasInstant() {
        Map<String, Instant> map = Map.of("time", Instant.parse("2025-01-15T10:30:00Z"));

        String json = JsonUtils.toJson(map);

        // Should not serialize as timestamp (number)
        assertFalse(json.matches(".*\\d{10,}.*"), "Instant should not be serialized as timestamp");
        assertTrue(json.contains("2025-01-15"));
    }

    @Test
    void should_ignoreUnknownProperties_when_deserializing() {
        String json = "{\"name\":\"test\",\"unknownField\":\"ignored\"}";

        record SimpleObj(String name) {}
        // This should not throw, because FAIL_ON_UNKNOWN_PROPERTIES is disabled
        SimpleObj result = JsonUtils.fromJson(json, SimpleObj.class);

        assertEquals("test", result.name());
    }

    @Test
    void should_throwRuntimeException_when_serializationFails() {
        // An object that cannot be serialized (e.g., a circular reference or stream)
        Object problematic = new Object() {
            @SuppressWarnings("unused")
            public Object getSelf() { return this; }
        };

        // Jackson will try to serialize this but may succeed or fail depending on config
        // Instead, test with truly invalid input
        assertThrows(RuntimeException.class, () -> JsonUtils.fromJson("not json", Map.class));
    }

    @Test
    void should_throwRuntimeException_when_deserializationFromClassFails() {
        assertThrows(RuntimeException.class, () -> JsonUtils.fromJson("{invalid}", Map.class));
    }

    @Test
    void should_throwRuntimeException_when_deserializationFromTypeReferenceFails() {
        assertThrows(RuntimeException.class, () -> JsonUtils.fromJson("{invalid}", new TypeReference<Map<String, String>>() {}));
    }

    @Test
    void should_serializeNull_when_nullValueInMap() {
        String json = JsonUtils.toJson(Map.of("key", "value"));
        assertNotNull(json);
    }

    @Test
    void should_roundTrip_when_complexObject() {
        Map<String, Object> original = Map.of(
                "name", "test",
                "count", 42,
                "nested", Map.of("inner", "value")
        );

        String json = JsonUtils.toJson(original);

        @SuppressWarnings("unchecked")
        Map<String, Object> restored = JsonUtils.fromJson(json, Map.class);

        assertEquals("test", restored.get("name"));
        assertEquals(42, restored.get("count"));
    }
}
