package com.quicknote.api.common.exception;

import jakarta.ws.rs.core.Response;

/** Raised when a request is well-formed but conflicts with current state. */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(Response.Status.CONFLICT, "CONFLICT", message);
    }
}
