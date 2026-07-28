package com.quicknote.api.note.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A checklist line supplied when creating a note or adding an item. */
public record ChecklistItemCreateRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 1000, message = "must be at most 1000 characters")
        String text) {
}
