package pl.settly.settly_api.notifications.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** An entry in the user's notification inbox (the bell). */
public record NotificationResponse(
    UUID id, String title, String body, String type, Map<String, String> data, Instant createdAt) {}
