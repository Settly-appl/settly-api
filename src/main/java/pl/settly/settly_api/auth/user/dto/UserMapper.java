package pl.settly.settly_api.auth.user.dto;

import org.mapstruct.Mapper;
import pl.settly.settly_api.auth.user.model.User;

@Mapper(componentModel = "spring")
public interface UserMapper {
  UserSearchResponse toUserSearchResponse(User user);
}
