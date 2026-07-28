package com.quicknote.api.common.exception;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;

/**
 * Renders authentication and authorization failures in the same envelope as
 * every other error, instead of Quarkus's default empty-bodied responses.
 *
 * <p>The 401/403 split is load-bearing and deliberate (research.md 1b): 401 means
 * the token is missing or not valid, 403 means the token is valid but lacks the
 * {@code user} realm role. Neither is used for "this resource isn't yours" —
 * that is always a 404 (FR-015).
 */
public final class SecurityExceptionMappers {

    private SecurityExceptionMappers() {
    }

    private static Response unauthorized() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .type(MediaType.APPLICATION_JSON)
                .entity(ApiError.of("UNAUTHORIZED", "Authentication required"))
                .build();
    }

    @Provider
    @Priority(Priorities.USER)
    public static class AuthenticationFailedMapper implements ExceptionMapper<AuthenticationFailedException> {
        @Override
        public Response toResponse(AuthenticationFailedException exception) {
            return unauthorized();
        }
    }

    @Provider
    @Priority(Priorities.USER)
    public static class UnauthorizedMapper implements ExceptionMapper<UnauthorizedException> {
        @Override
        public Response toResponse(UnauthorizedException exception) {
            return unauthorized();
        }
    }

    @Provider
    @Priority(Priorities.USER)
    public static class ForbiddenMapper implements ExceptionMapper<ForbiddenException> {
        @Override
        public Response toResponse(ForbiddenException exception) {
            return Response.status(Response.Status.FORBIDDEN)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ApiError.of("FORBIDDEN", "The 'user' role is required to access this API"))
                    .build();
        }
    }
}
