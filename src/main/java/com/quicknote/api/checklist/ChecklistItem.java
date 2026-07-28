package com.quicknote.api.checklist;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.quicknote.api.note.Note;

/**
 * A single checkable line belonging to exactly one note (data-model.md).
 *
 * <p>An item has no life of its own: it is reached only through its note, and is
 * deleted with it.
 */
@Entity
@Table(name = "checklist_item")
public class ChecklistItem {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "note_id", nullable = false)
    private Note note;

    @Column(nullable = false, length = 1000)
    private String text;

    @Column(nullable = false)
    private boolean checked;

    /** Display order within the note. Column is named to avoid the SQL keyword. */
    @Column(name = "item_position", nullable = false)
    private int position;

    public UUID getId() {
        return id;
    }

    public Note getNote() {
        return note;
    }

    public void setNote(Note note) {
        this.note = note;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
