package pl.settly.settly_api.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.common.exception.ResourceNotFoundException;
import pl.settly.settly_api.friendships.service.FriendshipService;
import pl.settly.settly_api.projects.dto.AddProjectMemberRequest;
import pl.settly.settly_api.projects.dto.CreateProjectRequest;
import pl.settly.settly_api.projects.dto.ProjectMapper;
import pl.settly.settly_api.projects.dto.ProjectMemberResponse;
import pl.settly.settly_api.projects.dto.ProjectResponse;
import pl.settly.settly_api.projects.dto.UpdateProjectRequest;
import pl.settly.settly_api.projects.model.Project;
import pl.settly.settly_api.projects.model.ProjectMember;
import pl.settly.settly_api.projects.model.ProjectStatus;
import pl.settly.settly_api.projects.repository.ProjectMemberRepository;
import pl.settly.settly_api.projects.repository.ProjectRepository;
import pl.settly.settly_api.projects.service.ProjectService;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

  @Mock ProjectRepository projectRepository;
  @Mock ProjectMemberRepository projectMemberRepository;
  @Mock UserRepository userRepository;
  @Mock FriendshipService friendshipService;
  @Mock ProjectMapper projectMapper;

  @InjectMocks ProjectService projectService;

  @Captor ArgumentCaptor<ProjectMember> memberCaptor;
  @Captor ArgumentCaptor<Project> projectCaptor;

  private final UUID userId = UUID.randomUUID();
  private final UUID friendId = UUID.randomUUID();
  private final UUID projectId = UUID.randomUUID();

  private User user(UUID id) {
    User u = new User();
    u.setId(id);
    return u;
  }

  private Project projectOwnedBy(UUID ownerId) {
    return Project.builder().id(projectId).projectOwner(user(ownerId)).build();
  }

  private ProjectResponse dummyProjectResponse() {
    return new ProjectResponse(
        projectId, "Trip", null, userId, ProjectStatus.ACTIVE, 1, null, null);
  }

  private ProjectMemberResponse dummyMemberResponse() {
    return new ProjectMemberResponse(friendId, "Alice", "alice", null, false, null);
  }

  // region createProject

  @Test
  void should_create_project_and_add_owner_as_member() {
    given(userRepository.getReferenceById(userId)).willReturn(user(userId));
    given(projectRepository.save(any(Project.class))).willAnswer(inv -> inv.getArgument(0));
    given(projectMapper.toProjectResponse(any(Project.class), anyLong()))
        .willReturn(dummyProjectResponse());

    CreateProjectRequest request = new CreateProjectRequest("Trip", "Ski trip");
    ProjectResponse result = projectService.createProject(request, userId);

    assertThat(result).isNotNull();
    verify(projectRepository).save(projectCaptor.capture());
    assertThat(projectCaptor.getValue().getName()).isEqualTo("Trip");
    assertThat(projectCaptor.getValue().getProjectOwner().getId()).isEqualTo(userId);

    verify(projectMemberRepository).save(memberCaptor.capture());
    assertThat(memberCaptor.getValue().getUser().getId()).isEqualTo(userId);
    verify(projectMapper)
        .toProjectResponse(any(Project.class), org.mockito.ArgumentMatchers.eq(1L));
  }

  // endregion

  // region getProject / access

  @Test
  void should_return_project_for_member() {
    given(projectRepository.findById(projectId)).willReturn(Optional.of(projectOwnedBy(userId)));
    given(projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)).willReturn(true);
    given(projectMemberRepository.countByProjectId(projectId)).willReturn(3L);
    given(projectMapper.toProjectResponse(any(Project.class), anyLong()))
        .willReturn(dummyProjectResponse());

    assertThat(projectService.getProject(projectId, userId)).isNotNull();
  }

  @Test
  void should_throw_when_non_member_gets_project() {
    given(projectRepository.findById(projectId)).willReturn(Optional.of(projectOwnedBy(userId)));
    given(projectMemberRepository.existsByProjectIdAndUserId(projectId, friendId))
        .willReturn(false);

    assertThatThrownBy(() -> projectService.getProject(projectId, friendId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Project does not exist");
  }

  // endregion

  // region updateProject

  @Test
  void should_update_project_fields_for_owner() {
    Project project = projectOwnedBy(userId);
    given(projectRepository.findById(projectId)).willReturn(Optional.of(project));
    given(projectRepository.save(project)).willReturn(project);
    given(projectMemberRepository.countByProjectId(projectId)).willReturn(1L);
    given(projectMapper.toProjectResponse(any(Project.class), anyLong()))
        .willReturn(dummyProjectResponse());

    projectService.updateProject(
        projectId, new UpdateProjectRequest("New name", "desc", ProjectStatus.SETTLED), userId);

    assertThat(project.getName()).isEqualTo("New name");
    assertThat(project.getDescription()).isEqualTo("desc");
    assertThat(project.getStatus()).isEqualTo(ProjectStatus.SETTLED);
  }

  @Test
  void should_throw_when_non_owner_updates() {
    given(projectRepository.findById(projectId)).willReturn(Optional.of(projectOwnedBy(userId)));

    assertThatThrownBy(
            () ->
                projectService.updateProject(
                    projectId, new UpdateProjectRequest("x", null, null), friendId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Only the project owner can perform this action");
  }

  // endregion

  // region deleteProject

  @Test
  void should_delete_project_and_members_for_owner() {
    given(projectRepository.findById(projectId)).willReturn(Optional.of(projectOwnedBy(userId)));

    projectService.deleteProject(projectId, userId);

    verify(projectMemberRepository).deleteByProjectId(projectId);
    verify(projectRepository).deleteById(projectId);
  }

  // endregion

  // region addMember

  @Test
  void should_add_friend_as_member() {
    given(projectRepository.findById(projectId)).willReturn(Optional.of(projectOwnedBy(userId)));
    given(friendshipService.areFriends(userId, friendId)).willReturn(true);
    given(projectMemberRepository.existsByProjectIdAndUserId(projectId, friendId))
        .willReturn(false);
    given(userRepository.getReferenceById(friendId)).willReturn(user(friendId));
    given(projectMemberRepository.save(any(ProjectMember.class)))
        .willAnswer(inv -> inv.getArgument(0));
    given(
            projectMapper.toProjectMemberResponse(
                any(ProjectMember.class), org.mockito.ArgumentMatchers.anyBoolean()))
        .willReturn(dummyMemberResponse());

    ProjectMemberResponse result =
        projectService.addMember(projectId, new AddProjectMemberRequest(friendId), userId);

    assertThat(result).isNotNull();
    verify(projectMemberRepository).save(memberCaptor.capture());
    assertThat(memberCaptor.getValue().getUser().getId()).isEqualTo(friendId);
  }

  @Test
  void should_throw_when_adding_non_friend() {
    given(projectRepository.findById(projectId)).willReturn(Optional.of(projectOwnedBy(userId)));
    given(friendshipService.areFriends(userId, friendId)).willReturn(false);

    assertThatThrownBy(
            () ->
                projectService.addMember(projectId, new AddProjectMemberRequest(friendId), userId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("You can only add friends to a project");
  }

  @Test
  void should_throw_when_adding_existing_member() {
    given(projectRepository.findById(projectId)).willReturn(Optional.of(projectOwnedBy(userId)));
    given(friendshipService.areFriends(userId, friendId)).willReturn(true);
    given(projectMemberRepository.existsByProjectIdAndUserId(projectId, friendId)).willReturn(true);

    assertThatThrownBy(
            () ->
                projectService.addMember(projectId, new AddProjectMemberRequest(friendId), userId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("User is already a member of this project");
  }

  @Test
  void should_throw_when_owner_adds_self() {
    given(projectRepository.findById(projectId)).willReturn(Optional.of(projectOwnedBy(userId)));

    assertThatThrownBy(
            () -> projectService.addMember(projectId, new AddProjectMemberRequest(userId), userId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("You are already a member of this project");
  }

  // endregion

  // region removeMember / leaveProject

  @Test
  void should_remove_member_for_owner() {
    Project project = projectOwnedBy(userId);
    ProjectMember member = ProjectMember.builder().project(project).user(user(friendId)).build();
    given(projectRepository.findById(projectId)).willReturn(Optional.of(project));
    given(projectMemberRepository.findByProjectIdAndUserId(projectId, friendId))
        .willReturn(Optional.of(member));

    projectService.removeMember(projectId, friendId, userId);

    verify(projectMemberRepository).delete(member);
  }

  @Test
  void should_throw_when_removing_owner() {
    given(projectRepository.findById(projectId)).willReturn(Optional.of(projectOwnedBy(userId)));

    assertThatThrownBy(() -> projectService.removeMember(projectId, userId, userId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("The project owner cannot be removed");
  }

  @Test
  void should_let_member_leave() {
    Project project = projectOwnedBy(userId);
    ProjectMember member = ProjectMember.builder().project(project).user(user(friendId)).build();
    given(projectRepository.findById(projectId)).willReturn(Optional.of(project));
    given(projectMemberRepository.findByProjectIdAndUserId(projectId, friendId))
        .willReturn(Optional.of(member));

    projectService.leaveProject(projectId, friendId);

    verify(projectMemberRepository).delete(member);
  }

  @Test
  void should_throw_when_owner_leaves() {
    given(projectRepository.findById(projectId)).willReturn(Optional.of(projectOwnedBy(userId)));

    assertThatThrownBy(() -> projectService.leaveProject(projectId, userId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("The owner cannot leave the project; delete it instead");

    verify(projectMemberRepository, never()).delete(any());
  }

  // endregion
}
