package pl.settly.settly_api.friendships.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.friendships.model.Friendship;

@Mapper(componentModel = "spring")
public interface FriendshipMapper {
  RequestFriendshipResponse toFriendshipResponse(Friendship friendship);

  @Mapping(source = "friendship.id", target = "friendshipId")
  @Mapping(source = "user", target = "user")
  @Mapping(source = "friendship.createdAt", target = "createdAt")
  PendingFriendshipRequestsResponse toPendingResponse(
      Friendship friendship, FriendshipUserDto user);

  @Mapping(source = "friendship.id", target = "friendshipId")
  @Mapping(source = "user", target = "user")
  FriendResponse toFriendResponse(Friendship friendship, FriendshipUserDto user);

  FriendshipUserDto toFriendshipUserDto(User user);
}
