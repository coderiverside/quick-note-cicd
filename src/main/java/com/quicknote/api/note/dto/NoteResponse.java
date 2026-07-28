package com.quicknote.api.note.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.quicknote.api.note.NoteColor;

/**
 * Note representation returned by every note endpoint
 * (contracts/openapi.yaml {@code Note} schema).
 *
 * <p>{@code color} is never null on the wire: an uncoloured note reports
 * {@link NoteColor#DEFAULT}, which is stored as NULL.
 */
public record NoteResponse(
        UUID id,
        String title,
        String content,
        NoteColor color,
        boolean pinned,
        boolean archived,
        boolean trashed,
        List<UUID> labelIds,
        List<ChecklistItemResponse> items,
        Instant createdAt,
        Instant updatedAt) {
}
