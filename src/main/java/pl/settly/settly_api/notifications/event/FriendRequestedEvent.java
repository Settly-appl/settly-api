package pl.settly.settly_api.notifications.event;

import java.util.UUID;

public record FriendRequestedEvent(UUID recipientId, UUID actorId) {}
