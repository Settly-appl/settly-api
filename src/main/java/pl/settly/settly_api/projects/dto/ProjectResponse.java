package pl.settly.settly_api.projects.dto;

import java.time.Instant;
import java.util.UUID;
import pl.settly.settly_api.projects.model.ProjectStatus;

public record ProjectResponse(
    UUID id,
    String name,
    String description,
    UUID ownerId,
    ProjectStatus status,
    long memberCount,
    Instant createdAt,
    Instant updatedAt) {}
