package pl.settly.settly_api.friendships.dto;

import java.time.Instant;
import pl.settly.settly_api.friendships.model.FriendshipStatus;

public record RequestFriendshipResponse(FriendshipStatus status, Instant updatedAt) {}
