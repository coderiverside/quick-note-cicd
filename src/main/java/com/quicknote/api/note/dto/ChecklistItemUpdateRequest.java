package com.quicknote.api.note.dto;

import jakarta.validation.constraints.Size;

/**
 * Body of {@code PATCH /api/v1/notes/{noteId}/items/{itemId}}.
 *
 * <p>Like the note PATCH, this needs to distinguish an omitted field from one
 * explicitly set, so it is a bean with setter-tracked presence rather than a
 * record.
 */
public class ChecklistItemUpdateRequest {

    @Size(max = 1000, message = "must be at most 1000 characters")
    private String text;

    private Boolean checked;

    private boolean textPresent;
    private boolean checkedPresent;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
        this.textPresent = true;
    }

    public Boolean getChecked() {
        return checked;
    }

    public void setChecked(Boolean checked) {
        this.checked = checked;
        this.checkedPresent = true;
    }

    public boolean isTextPresent() {
        return textPresent;
    }

    public boolean isCheckedPresent() {
        return checkedPresent;
    }
}
