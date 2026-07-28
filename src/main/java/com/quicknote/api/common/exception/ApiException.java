package com.quicknote.api.common.exception;

import jakarta.ws.rs.core.Response;

/**
 * Base for the application's own error conditions, each carrying the HTTP status
 * and stable error code that the mapper turns into an {@link ApiError}.
 */
public abstract class ApiException extends RuntimeException {

    private final Response.Status status;
    private final String code;

    protected ApiException(Response.Status status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public Response.Status getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
