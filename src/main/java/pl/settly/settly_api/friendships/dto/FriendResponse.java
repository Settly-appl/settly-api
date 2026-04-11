package pl.settly.settly_api.friendships.dto;

import java.util.UUID;

public record FriendResponse(UUID friendshipId, FriendshipUserDto user) {}
