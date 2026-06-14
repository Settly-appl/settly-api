package pl.settly.settly_api.projects.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectMemberResponse(
    UUID userId,
    String displayName,
    String username,
    String avatarUrl,
    boolean owner,
    Instant joinedAt) {}
