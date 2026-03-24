package pl.settly.settly_api.friendships.dto;

import java.time.Instant;
import java.util.UUID;

public record PendingFriendshipRequestsResponse(
    UUID friendshipId, FriendshipUserDto user, Instant createdAt) {}
