package com.quicknote.api.note;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import com.quicknote.api.label.Label;
import com.quicknote.api.note.dto.ChecklistItemResponse;
import com.quicknote.api.note.dto.NoteResponse;

/** Translates {@link Note} entities into the wire representation. */
@ApplicationScoped
public class NoteMapper {

    public NoteResponse toResponse(Note note) {
        return new NoteResponse(
                note.getId(),
                note.getTitle(),
                note.getContent(),
                note.getColor() == null ? NoteColor.DEFAULT : note.getColor(),
                note.isPinned(),
                note.isArchived(),
                note.isTrashed(),
                note.getLabels().stream().map(Label::getId).toList(),
                note.getItems().stream()
                        .map(item -> new ChecklistItemResponse(
                                item.getId(), item.getText(), item.isChecked(), item.getPosition()))
                        .toList(),
                note.getCreatedAt(),
                note.getUpdatedAt());
    }

    public List<NoteResponse> toResponses(List<Note> notes) {
        return notes.stream().map(this::toResponse).toList();
    }
}
