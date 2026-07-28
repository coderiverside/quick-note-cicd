package com.quicknote.api.common.exception;

import jakarta.ws.rs.core.Response;

/**
 * Raised for validation rules that span multiple fields or child entities and
 * therefore cannot be expressed as a Bean Validation annotation on a single
 * field (for example FR-001's "a note may not be entirely empty").
 */
public class ValidationException extends ApiException {

    public ValidationException(String message) {
        super(Response.Status.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}
