package com.quicknote.api.common.web;

import java.io.IOException;
import java.util.UUID;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import org.jboss.logmanager.MDC;

/**
 * Propagates a correlation id across the request.
 *
 * <p>An inbound {@code X-Request-Id} is honoured so a trace started by an upstream
 * caller stays intact; otherwise one is generated. The id is put in the MDC (so
 * every structured log line for this request carries it) and echoed back on the
 * response, giving a caller who reports a problem an id an operator can search on
 * (constitution Principle V).
 */
@Provider
public class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String HEADER = "X-Request-Id";
    private static final String MDC_KEY = "requestId";
    private static final int MAX_LENGTH = 128;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String incoming = requestContext.getHeaderString(HEADER);
        String correlationId = sanitize(incoming);
        requestContext.setProperty(MDC_KEY, correlationId);
        MDC.put(MDC_KEY, correlationId);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        Object correlationId = requestContext.getProperty(MDC_KEY);
        if (correlationId != null) {
            responseContext.getHeaders().putSingle(HEADER, correlationId);
        }
        // The request thread is pooled, so the MDC must not outlive the request.
        MDC.remove(MDC_KEY);
    }

    /**
     * Accepts a caller-supplied id only if it is short and alphanumeric, so an
     * attacker cannot inject newlines or control characters into the log stream.
     */
    private static String sanitize(String incoming) {
        if (incoming == null || incoming.isBlank() || incoming.length() > MAX_LENGTH) {
            return UUID.randomUUID().toString();
        }
        String trimmed = incoming.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean allowed = Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.';
            if (!allowed) {
                return UUID.randomUUID().toString();
            }
        }
        return trimmed;
    }
}
