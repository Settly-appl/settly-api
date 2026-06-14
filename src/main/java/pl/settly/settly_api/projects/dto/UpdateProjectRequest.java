package pl.settly.settly_api.projects.dto;

import jakarta.validation.constraints.Size;
import pl.settly.settly_api.projects.model.ProjectStatus;

public record UpdateProjectRequest(
    @Size(max = 255) String name, @Size(max = 500) String description, ProjectStatus status) {}
