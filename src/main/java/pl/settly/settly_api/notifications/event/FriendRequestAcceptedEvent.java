package pl.settly.settly_api.notifications.event;

import java.util.UUID;

public record FriendRequestAcceptedEvent(UUID recipientId, UUID actorId) {}
