package com.quicknote.api.label.dto;

import java.time.Instant;
import java.util.UUID;

/** Label representation (contracts/openapi.yaml {@code Label} schema). */
public record LabelResponse(UUID id, String name, Instant createdAt) {
}
