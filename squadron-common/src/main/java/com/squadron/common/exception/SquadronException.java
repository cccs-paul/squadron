package com.squadron.common.exception;

import lombok.Getter;

@Getter
public class SquadronException extends RuntimeException {

    private final String errorCode;

    public SquadronException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public SquadronException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
