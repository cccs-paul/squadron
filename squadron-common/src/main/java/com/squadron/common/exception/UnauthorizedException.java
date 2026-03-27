package com.squadron.common.exception;

public class UnauthorizedException extends SquadronException {

    public UnauthorizedException(String message) {
        super(message, "UNAUTHORIZED");
    }
}
