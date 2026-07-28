package com.quicknote.api.common.pagination;

import com.quicknote.api.common.exception.ValidationException;

/**
 * A validated, bounded page request.
 *
 * <p>The upper bound on {@code size} is what keeps every collection endpoint from
 * being able to return an unbounded result set (constitution Principle VII); it
 * is enforced here rather than per-endpoint so no listing can opt out of it.
 */
public record PageRequest(int page, int size) {

    public static PageRequest of(Integer page, Integer size, int defaultSize, int maxSize) {
        int resolvedPage = page == null ? 0 : page;
        int resolvedSize = size == null ? defaultSize : size;

        if (resolvedPage < 0) {
            throw new ValidationException("page must be 0 or greater");
        }
        if (resolvedSize < 1) {
            throw new ValidationException("size must be 1 or greater");
        }
        if (resolvedSize > maxSize) {
            throw new ValidationException("size must not exceed " + maxSize);
        }
        return new PageRequest(resolvedPage, resolvedSize);
    }
}
