package com.quicknote.api.common.exception;

import java.util.List;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Turns Bean Validation failures into the shared 400 envelope.
 *
 * <p>Only the property path and the validation message are exposed; the rejected
 * value itself is never echoed back, so a malformed request cannot be used to
 * reflect arbitrary content into a response.
 */
@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        List<String> details = exception.getConstraintViolations().stream()
                .map(ValidationExceptionMapper::describe)
                .sorted()
                .toList();

        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(ApiError.of("VALIDATION_ERROR", "Request failed validation", details))
                .build();
    }

    private static String describe(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
        // Strip the JAX-RS method prefix ("createNote.request.title" -> "title").
        int lastDot = path.lastIndexOf('.');
        String field = lastDot >= 0 ? path.substring(lastDot + 1) : path;
        return field.isBlank() ? violation.getMessage() : field + ": " + violation.getMessage();
    }
}
