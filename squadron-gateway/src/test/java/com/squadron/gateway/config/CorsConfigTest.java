package com.squadron.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.reactive.CorsWebFilter;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CorsConfig.
 */
class CorsConfigTest {

    @Test
    void should_beAnnotatedWithConfiguration() {
        var annotation = CorsConfig.class
                .getAnnotation(org.springframework.context.annotation.Configuration.class);
        assertThat(annotation).isNotNull();
    }

    @Test
    void should_createCorsWebFilter_when_defaultOrigins() throws Exception {
        CorsConfig config = createCorsConfigWithOrigins("http://localhost:4200,https://localhost:4200");

        CorsWebFilter filter = config.corsWebFilter();
        assertThat(filter).isNotNull();
    }

    @Test
    void should_createCorsWebFilter_when_singleOrigin() throws Exception {
        CorsConfig config = createCorsConfigWithOrigins("https://myapp.example.com");

        CorsWebFilter filter = config.corsWebFilter();
        assertThat(filter).isNotNull();
    }

    @Test
    void should_createCorsWebFilter_when_multipleOrigins() throws Exception {
        CorsConfig config = createCorsConfigWithOrigins("https://app1.example.com,https://app2.example.com,https://app3.example.com");

        CorsWebFilter filter = config.corsWebFilter();
        assertThat(filter).isNotNull();
    }

    @Test
    void should_haveCorsWebFilterBeanAnnotation() throws NoSuchMethodException {
        var method = CorsConfig.class.getMethod("corsWebFilter");
        var beanAnnotation = method.getAnnotation(org.springframework.context.annotation.Bean.class);
        assertThat(beanAnnotation).isNotNull();
    }

    @Test
    void should_haveAllowedOriginsField() throws NoSuchFieldException {
        Field field = CorsConfig.class.getDeclaredField("allowedOrigins");
        assertThat(field).isNotNull();
        assertThat(field.getType()).isEqualTo(String.class);
    }

    private CorsConfig createCorsConfigWithOrigins(String origins) throws Exception {
        CorsConfig config = new CorsConfig();
        Field field = CorsConfig.class.getDeclaredField("allowedOrigins");
        field.setAccessible(true);
        field.set(config, origins);
        return config;
    }
}
