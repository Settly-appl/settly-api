package pl.settly.settly_api.suggestions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSuggestionRequest(
    @NotBlank(message = "Suggestion cannot be empty")
        @Size(max = 2000, message = "Suggestion cannot exceed 2000 characters")
        String content) {}
