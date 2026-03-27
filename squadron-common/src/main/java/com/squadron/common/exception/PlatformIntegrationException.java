package com.squadron.common.exception;

import lombok.Getter;

@Getter
public class PlatformIntegrationException extends SquadronException {

    private final String platform;

    public PlatformIntegrationException(String platform, String message) {
        super(message, "PLATFORM_ERROR");
        this.platform = platform;
    }

    public PlatformIntegrationException(String platform, String message, Throwable cause) {
        super(message, "PLATFORM_ERROR", cause);
        this.platform = platform;
    }
}
