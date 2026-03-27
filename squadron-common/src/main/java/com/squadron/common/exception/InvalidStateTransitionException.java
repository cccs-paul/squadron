package com.squadron.common.exception;

import lombok.Getter;

@Getter
public class InvalidStateTransitionException extends SquadronException {

    private final String fromState;
    private final String toState;

    public InvalidStateTransitionException(String fromState, String toState) {
        super("Invalid state transition from " + fromState + " to " + toState, "INVALID_TRANSITION");
        this.fromState = fromState;
        this.toState = toState;
    }
}
