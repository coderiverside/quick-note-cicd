package com.quicknote.api.note.dto;

import jakarta.validation.constraints.Size;

import com.quicknote.api.note.NoteColor;

/**
 * Body of {@code PATCH /api/v1/notes/{noteId}}.
 *
 * <p>A PATCH has to tell "field omitted" (leave it alone) apart from "field
 * explicitly null" (clear it). A record cannot express that — both arrive as
 * null — so this is a mutable bean whose setters record which fields the client
 * actually sent. Jackson calls a setter only for a property present in the body.
 */
public class NoteUpdateRequest {

    @Size(max = 500, message = "must be at most 500 characters")
    private String title;

    private String content;

    private NoteColor color;

    private boolean titlePresent;
    private boolean contentPresent;
    private boolean colorPresent;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.titlePresent = true;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
        this.contentPresent = true;
    }

    public NoteColor getColor() {
        return color;
    }

    public void setColor(NoteColor color) {
        this.color = color;
        this.colorPresent = true;
    }

    public boolean isTitlePresent() {
        return titlePresent;
    }

    public boolean isContentPresent() {
        return contentPresent;
    }

    public boolean isColorPresent() {
        return colorPresent;
    }
}
