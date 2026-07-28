package com.quicknote.api.common.exception;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.jboss.logging.Logger;

/**
 * Last-resort mapper: anything not handled above becomes a generic envelope.
 *
 * <p>The original exception is logged in full (with the request's correlation id
 * in the MDC, so an operator can find it) but never returned to the caller —
 * stack traces, SQL, and internal hostnames must not cross the API boundary
 * (constitution Principle V).
 */
@Provider
@Priority(Priorities.USER + 100)
public class UnhandledExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(UnhandledExceptionMapper.class);

    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof WebApplicationException wae) {
            int status = wae.getResponse().getStatus();
            if (status < 500) {
                // Framework-level client errors (unmatched route, unreadable body,
                // unsupported media type). Describe the class of problem only.
                LOG.debugf(exception, "Client error %d on request", status);
                return Response.status(status)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(ApiError.of(codeFor(status), messageFor(status)))
                        .build();
            }
        }

        LOG.error("Unhandled exception while processing request", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(ApiError.of("INTERNAL_ERROR", "An unexpected internal error occurred"))
                .build();
    }

    private static String codeFor(int status) {
        return switch (status) {
            case 400 -> "BAD_REQUEST";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> "CLIENT_ERROR";
        };
    }

    private static String messageFor(int status) {
        return switch (status) {
            case 400 -> "The request could not be understood";
            case 404 -> "Resource not found";
            case 405 -> "HTTP method not supported for this resource";
            case 406 -> "Requested representation is not available";
            case 415 -> "Unsupported request media type";
            default -> "The request could not be processed";
        };
    }
}
