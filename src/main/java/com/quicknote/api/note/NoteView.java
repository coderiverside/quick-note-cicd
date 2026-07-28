package com.quicknote.api.note;

import java.util.Locale;

import com.quicknote.api.common.exception.ValidationException;

/**
 * Which lifecycle slice of a user's notes to list (FR-008, FR-009, FR-012).
 *
 * <p>The three views are mutually exclusive and together cover every note: an
 * archived note never appears in {@link #ACTIVE}, and a trashed note appears
 * only in {@link #TRASHED} regardless of its retained archived flag.
 */
public enum NoteView {
    ACTIVE,
    ARCHIVED,
    TRASHED;

    public static NoteView from(String raw) {
        if (raw == null || raw.isBlank()) {
            return ACTIVE;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unsupported view '" + raw + "'. Expected one of: active, archived, trashed.");
        }
    }
}
