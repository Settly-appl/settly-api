package pl.settly.settly_api.projects.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddProjectMemberRequest(@NotNull UUID userId) {}
