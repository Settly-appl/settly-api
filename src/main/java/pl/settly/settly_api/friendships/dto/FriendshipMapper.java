package pl.settly.settly_api.friendships.dto;

import org.mapstruct.Mapper;
import pl.settly.settly_api.friendships.model.Friendship;

@Mapper(componentModel = "spring")
public interface FriendshipMapper {
    RequestFriendshipResponse toFriendshipResponse(Friendship friendship);
}
