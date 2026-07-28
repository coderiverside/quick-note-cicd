package com.quicknote.api.note.dto;

import java.util.UUID;

/**
 * A single checkable line within a note
 * (contracts/openapi.yaml {@code ChecklistItem} schema).
 */
public record ChecklistItemResponse(UUID id, String text, boolean checked, int position) {
}
