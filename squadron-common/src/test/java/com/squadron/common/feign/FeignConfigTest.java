package com.squadron.common.feign;

import feign.codec.ErrorDecoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeignConfigTest {

    @Test
    void should_createFeignErrorDecoderBean() {
        FeignConfig config = new FeignConfig();

        ErrorDecoder decoder = config.feignErrorDecoder();

        assertNotNull(decoder);
        assertInstanceOf(FeignErrorDecoder.class, decoder);
    }
}
