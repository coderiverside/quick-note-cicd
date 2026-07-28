package com.quicknote.api.label.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code PATCH /api/v1/labels/{labelId}}. */
public record LabelUpdateRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 255, message = "must be at most 255 characters")
        String name) {
}
