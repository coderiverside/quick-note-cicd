package com.quicknote.api.common.exception;

import jakarta.ws.rs.core.Response;

/**
 * Raised when a resource does not exist <em>for the calling user</em>.
 *
 * <p>Deliberately also used when the resource exists but belongs to someone else,
 * so the response cannot be used to probe for other users' data (FR-015). Named
 * to avoid confusion with {@code jakarta.ws.rs.NotFoundException}.
 */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(Response.Status.NOT_FOUND, "NOT_FOUND", message);
    }
}
