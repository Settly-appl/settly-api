package pl.settly.settly_api.projects.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import pl.settly.settly_api.projects.model.Project;
import pl.settly.settly_api.projects.model.ProjectMember;

@Mapper(componentModel = "spring")
public interface ProjectMapper {

  @Mapping(source = "project.id", target = "id")
  @Mapping(source = "project.projectOwner.id", target = "ownerId")
  @Mapping(source = "memberCount", target = "memberCount")
  // Filled in by ProjectService from one grouped query over expenses.
  @Mapping(target = "expenseCount", ignore = true)
  @Mapping(target = "totalAmount", ignore = true)
  ProjectResponse toProjectResponse(Project project, long memberCount);

  @Mapping(source = "member.user.id", target = "userId")
  @Mapping(source = "member.user.displayName", target = "displayName")
  @Mapping(source = "member.user.username", target = "username")
  @Mapping(source = "member.user.avatarUrl", target = "avatarUrl")
  @Mapping(source = "owner", target = "owner")
  ProjectMemberResponse toProjectMemberResponse(ProjectMember member, boolean owner);
}
