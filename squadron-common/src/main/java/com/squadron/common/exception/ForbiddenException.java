package com.squadron.common.exception;

public class ForbiddenException extends SquadronException {

    public ForbiddenException(String message) {
        super(message, "FORBIDDEN");
    }
}
