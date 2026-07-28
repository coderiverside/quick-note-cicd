package com.quicknote.api.common.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Renders the application's own error conditions into the shared envelope. */
@Provider
public class ApiExceptionMapper implements ExceptionMapper<ApiException> {

    @Override
    public Response toResponse(ApiException exception) {
        return Response.status(exception.getStatus())
                .type(MediaType.APPLICATION_JSON)
                .entity(ApiError.of(exception.getCode(), exception.getMessage()))
                .build();
    }
}
