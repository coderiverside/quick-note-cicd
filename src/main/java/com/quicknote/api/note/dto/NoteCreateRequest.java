package com.quicknote.api.note.dto;

import java.util.List;

import jakarta.validation.constraints.Size;

import com.quicknote.api.note.NoteColor;

/**
 * Body of {@code POST /api/v1/notes}.
 *
 * <p>Every field is individually optional; the rule that a note may not be
 * <em>entirely</em> empty spans all of them and so is enforced in the service
 * layer rather than by a field annotation (FR-001).
 */
public record NoteCreateRequest(

        @Size(max = 500, message = "must be at most 500 characters") String title,

        String content,

        NoteColor color,

        List<ChecklistItemCreateRequest> items) {

    public List<ChecklistItemCreateRequest> items() {
        return items == null ? List.of() : items;
    }
}
