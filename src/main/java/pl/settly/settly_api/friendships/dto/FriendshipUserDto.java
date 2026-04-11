package pl.settly.settly_api.friendships.dto;

import java.util.UUID;

public record FriendshipUserDto(UUID id, String displayName, String avatarUrl) {}
