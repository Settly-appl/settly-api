package pl.settly.settly_api.suggestions.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A suggestion as an admin reads it. The author is carried as a display name rather than an id so
 * the list is readable without a second lookup; it is null for a deleted account.
 */
public record SuggestionResponse(
    UUID id, String content, String authorName, Instant createdAt) {}
