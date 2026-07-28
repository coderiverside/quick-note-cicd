package com.quicknote.api.common.exception;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The single error envelope used by every endpoint (constitution Principle I,
 * contracts/openapi.yaml {@code Error} schema):
 *
 * <pre>{@code { "error": { "code": "...", "message": "...", "details": [...] } }}</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(Detail error) {

    public static ApiError of(String code, String message) {
        return new ApiError(new Detail(code, message, null));
    }

    public static ApiError of(String code, String message, List<String> details) {
        return new ApiError(new Detail(code, message, details == null || details.isEmpty() ? null : details));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Detail(String code, String message, List<String> details) {
    }
}
