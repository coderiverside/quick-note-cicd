package com.quicknote.api.note;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.quicknote.api.checklist.ChecklistItem;
import com.quicknote.api.common.exception.ConflictException;
import com.quicknote.api.common.exception.ResourceNotFoundException;
import com.quicknote.api.common.exception.ValidationException;
import com.quicknote.api.common.pagination.PageRequest;
import com.quicknote.api.common.pagination.PageResponse;
import com.quicknote.api.label.Label;
import com.quicknote.api.label.LabelService;
import com.quicknote.api.note.dto.ChecklistItemCreateRequest;
import com.quicknote.api.note.dto.ChecklistItemUpdateRequest;
import com.quicknote.api.note.dto.NoteCreateRequest;
import com.quicknote.api.note.dto.NoteResponse;
import com.quicknote.api.note.dto.NoteUpdateRequest;
import com.quicknote.api.security.CurrentUserProvider;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Business rules for notes.
 *
 * <p>Every read and write goes through {@link #requireOwned} so that ownership is
 * checked in exactly one place: a note belonging to someone else is reported as
 * missing, never as forbidden (FR-015, research.md 2).
 */
@ApplicationScoped
public class NoteService {

    @Inject
    NoteRepository noteRepository;

    @Inject
    NoteMapper noteMapper;

    @Inject
    CurrentUserProvider currentUser;

    @Inject
    LabelService labelService;

    @Inject
    MeterRegistry meterRegistry;

    @Transactional
    public NoteResponse create(NoteCreateRequest request) {
        List<String> itemTexts = request.items().stream()
                .map(item -> item == null ? null : item.text())
                .filter(text -> !isBlank(text))
                .map(String::trim)
                .toList();

        // Items count towards "not empty": a bare shopping list is a valid note.
        requireNotEmpty(request.title(), request.content(), itemTexts.size());

        Note note = new Note();
        note.setOwnerId(currentUser.ownerId());
        note.setTitle(normalize(request.title()));
        note.setContent(normalize(request.content()));
        note.setColor(storedColor(request.color()));
        itemTexts.forEach(text -> note.addItem(newItem(text)));

        noteRepository.persist(note);
        meterRegistry.counter("quicknote.notes.created",
                "kind", itemTexts.isEmpty() ? "text" : "checklist").increment();
        return noteMapper.toResponse(note);
    }

    @Transactional
    public NoteResponse addItem(UUID noteId, ChecklistItemCreateRequest request) {
        Note note = requireOwned(noteId);
        String text = requireItemText(request == null ? null : request.text());

        note.addItem(newItem(text));
        return noteMapper.toResponse(note);
    }

    @Transactional
    public NoteResponse updateItem(UUID noteId, UUID itemId, ChecklistItemUpdateRequest request) {
        Note note = requireOwned(noteId);
        ChecklistItem item = requireItem(note, itemId);

        if (!request.isTextPresent() && !request.isCheckedPresent()) {
            throw new ValidationException("Provide text, checked, or both");
        }
        if (request.isTextPresent()) {
            item.setText(requireItemText(request.getText()));
        }
        if (request.isCheckedPresent()) {
            item.setChecked(Boolean.TRUE.equals(request.getChecked()));
        }

        return noteMapper.toResponse(note);
    }

    @Transactional
    public void removeItem(UUID noteId, UUID itemId) {
        Note note = requireOwned(noteId);
        ChecklistItem item = requireItem(note, itemId);

        // orphanRemoval turns this into a delete; positions of the remaining
        // items are left as they are, since only their relative order matters.
        note.getItems().remove(item);
    }

    /**
     * Finds an item within a note the caller already owns.
     *
     * <p>Scoped to the note rather than looked up globally, so an item id from
     * one note cannot be used against another.
     */
    private static ChecklistItem requireItem(Note note, UUID itemId) {
        return note.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Checklist item not found"));
    }

    private static ChecklistItem newItem(String text) {
        ChecklistItem item = new ChecklistItem();
        item.setText(text);
        return item;
    }

    private static String requireItemText(String raw) {
        if (isBlank(raw)) {
            throw new ValidationException("text: must not be blank");
        }
        return raw.trim();
    }

    public NoteResponse get(UUID id) {
        return noteMapper.toResponse(requireOwned(id));
    }

    @Transactional
    public PageResponse<NoteResponse> list(NoteView view, UUID labelId, String search, PageRequest pageRequest) {
        String ownerId = currentUser.ownerId();

        // Filtering by a label the caller does not own is reported as a missing
        // label, not as an empty result, so the two cases stay distinguishable.
        if (labelId != null) {
            labelService.requireOwned(labelId);
        }

        // A blank term is not a search for nothing, it is no search at all.
        String searchTerm = isBlank(search) ? null : search.trim();

        // Search is the query most sensitive to a user's note count growing, so
        // it is timed separately from a plain listing (constitution Principle V).
        Timer.Sample sample = Timer.start(meterRegistry);
        List<Note> notes =
                noteRepository.listByView(ownerId, view, labelId, searchTerm, pageRequest.page(), pageRequest.size());
        long total = noteRepository.countByView(ownerId, view, labelId, searchTerm);
        sample.stop(meterRegistry.timer("quicknote.notes.query", "mode", searchTerm == null ? "list" : "search"));

        return PageResponse.of(noteMapper.toResponses(notes), pageRequest, total);
    }

    @Transactional
    public NoteResponse applyLabel(UUID noteId, UUID labelId) {
        Note note = requireOwned(noteId);
        note.getLabels().add(labelService.requireOwned(labelId));
        return noteMapper.toResponse(note);
    }

    @Transactional
    public NoteResponse removeLabel(UUID noteId, UUID labelId) {
        Note note = requireOwned(noteId);
        Label label = labelService.requireOwned(labelId);
        note.getLabels().remove(label);
        return noteMapper.toResponse(note);
    }

    @Transactional
    public NoteResponse update(UUID id, NoteUpdateRequest request) {
        Note note = requireOwned(id);

        String title = request.isTitlePresent() ? normalize(request.getTitle()) : note.getTitle();
        String content = request.isContentPresent() ? normalize(request.getContent()) : note.getContent();

        // An edit must not be able to leave the note in the empty state that
        // FR-001 forbids creating.
        requireNotEmpty(title, content, note.getItems().size());

        note.setTitle(title);
        note.setContent(content);
        if (request.isColorPresent()) {
            note.setColor(storedColor(request.getColor()));
        }

        return noteMapper.toResponse(note);
    }

    @Transactional
    public NoteResponse setPinned(UUID id, boolean pinned) {
        Note note = requireOwned(id);
        note.setPinned(pinned);
        return noteMapper.toResponse(note);
    }

    @Transactional
    public NoteResponse setArchived(UUID id, boolean archived) {
        Note note = requireOwned(id);

        // FR-010: archiving is a transition out of the active/archived world, not
        // a way to leave the trash. Unarchiving a trashed note is equally
        // meaningless, so both directions are refused while it is trashed.
        if (note.isTrashed()) {
            throw new ConflictException("Restore the note from trash before changing its archived state");
        }

        note.setArchived(archived);
        return noteMapper.toResponse(note);
    }

    @Transactional
    public NoteResponse trash(UUID id) {
        Note note = requireOwned(id);
        if (!note.isTrashed()) {
            note.setTrashed(true);
            note.setTrashedAt(Instant.now());
        }
        return noteMapper.toResponse(note);
    }

    /**
     * Restores a note from the trash.
     *
     * <p>The archived flag is left exactly as it was, so a note trashed from the
     * archive returns to the archive rather than reappearing in the active list
     * (FR-011).
     */
    @Transactional
    public NoteResponse restore(UUID id) {
        Note note = requireOwned(id);
        if (note.isTrashed()) {
            note.setTrashed(false);
            note.setTrashedAt(null);
        }
        return noteMapper.toResponse(note);
    }

    @Transactional
    public void deletePermanently(UUID id) {
        Note note = requireOwned(id);

        // FR-004: permanent deletion is reachable only from the trash, so one
        // mistaken call cannot destroy a live note.
        if (!note.isTrashed()) {
            throw new ConflictException("Only a trashed note can be permanently deleted");
        }

        noteRepository.delete(note);
    }

    /**
     * Loads a note owned by the caller, or reports it as missing.
     *
     * <p>The owner filter is part of the query rather than a check performed after
     * loading, so there is no path that reads another user's row at all.
     */
    Note requireOwned(UUID id) {
        return noteRepository.findOwnedById(currentUser.ownerId(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Note not found"));
    }

    private static void requireNotEmpty(String title, String content, int itemCount) {
        if (isBlank(title) && isBlank(content) && itemCount == 0) {
            throw new ValidationException("A note must have a title, content, or at least one checklist item");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Blank strings are stored as NULL so "empty" has exactly one representation. */
    private static String normalize(String value) {
        return isBlank(value) ? null : value;
    }

    /** DEFAULT is the absence of a colour, so it is stored as NULL. */
    private static NoteColor storedColor(NoteColor color) {
        return color == null || color == NoteColor.DEFAULT ? null : color;
    }
}
