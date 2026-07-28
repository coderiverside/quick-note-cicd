package com.quicknote.api.common.pagination;

import java.util.List;

/**
 * Paginated response body shared by every collection endpoint
 * (contracts/openapi.yaml {@code NotePage} / {@code LabelPage}).
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {

    public static <T> PageResponse<T> of(List<T> content, PageRequest request, long totalElements) {
        return new PageResponse<>(content, request.page(), request.size(), totalElements);
    }
}
