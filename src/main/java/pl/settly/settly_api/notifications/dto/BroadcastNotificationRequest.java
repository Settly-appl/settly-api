package pl.settly.settly_api.notifications.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BroadcastNotificationRequest(
    @NotBlank @Size(max = 100) String title, @NotBlank @Size(max = 500) String body) {}
