package com.quicknote.api.label;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.quicknote.api.common.exception.ConflictException;
import com.quicknote.api.common.exception.ResourceNotFoundException;
import com.quicknote.api.common.exception.ValidationException;
import com.quicknote.api.common.pagination.PageRequest;
import com.quicknote.api.common.pagination.PageResponse;
import com.quicknote.api.label.dto.LabelCreateRequest;
import com.quicknote.api.label.dto.LabelResponse;
import com.quicknote.api.label.dto.LabelUpdateRequest;
import com.quicknote.api.security.CurrentUserProvider;

/** Business rules for labels. */
@ApplicationScoped
public class LabelService {

    @Inject
    LabelRepository labelRepository;

    @Inject
    CurrentUserProvider currentUser;

    @Transactional
    public LabelResponse create(LabelCreateRequest request) {
        String ownerId = currentUser.ownerId();
        String name = requireName(request.name());
        requireNameAvailable(ownerId, name, null);

        Label label = new Label();
        label.setOwnerId(ownerId);
        label.setName(name);
        labelRepository.persist(label);

        return toResponse(label);
    }

    public PageResponse<LabelResponse> list(PageRequest pageRequest) {
        String ownerId = currentUser.ownerId();
        List<LabelResponse> labels = labelRepository.listOwned(ownerId, pageRequest.page(), pageRequest.size())
                .stream()
                .map(LabelService::toResponse)
                .toList();
        return PageResponse.of(labels, pageRequest, labelRepository.countOwned(ownerId));
    }

    @Transactional
    public LabelResponse rename(UUID id, LabelUpdateRequest request) {
        Label label = requireOwned(id);
        String name = requireName(request.name());
        requireNameAvailable(label.getOwnerId(), name, id);

        label.setName(name);
        return toResponse(label);
    }

    /**
     * Deletes a label.
     *
     * <p>The {@code note_label} rows go with it via ON DELETE CASCADE, so the
     * label disappears from every note that carried it while the notes themselves
     * are untouched (FR-018).
     */
    @Transactional
    public void delete(UUID id) {
        labelRepository.delete(requireOwned(id));
    }

    /** Resolves a label the caller owns, for use by the note endpoints. */
    public Label requireOwned(UUID id) {
        return labelRepository.findOwnedById(currentUser.ownerId(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Label not found"));
    }

    private void requireNameAvailable(String ownerId, String name, UUID allowedId) {
        Optional<Label> existing = labelRepository.findOwnedByName(ownerId, name);
        // Renaming a label to the name it already has must not collide with itself.
        if (existing.isPresent() && !existing.get().getId().equals(allowedId)) {
            throw new ConflictException("A label named '" + name + "' already exists");
        }
    }

    private static String requireName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("name: must not be blank");
        }
        return raw.trim();
    }

    private static LabelResponse toResponse(Label label) {
        return new LabelResponse(label.getId(), label.getName(), label.getCreatedAt());
    }
}
