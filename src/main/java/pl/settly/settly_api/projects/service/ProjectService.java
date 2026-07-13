package pl.settly.settly_api.projects.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.common.exception.ResourceNotFoundException;
import pl.settly.settly_api.debts.repository.DebtRepository;
import pl.settly.settly_api.expenses.dto.ProjectExpenseTotals;
import pl.settly.settly_api.expenses.repository.ExpenseRepository;
import pl.settly.settly_api.friendships.service.FriendshipService;
import pl.settly.settly_api.projects.dto.AddProjectMemberRequest;
import pl.settly.settly_api.projects.dto.CreateProjectRequest;
import pl.settly.settly_api.projects.dto.ProjectMapper;
import pl.settly.settly_api.projects.dto.ProjectMemberResponse;
import pl.settly.settly_api.projects.dto.ProjectResponse;
import pl.settly.settly_api.projects.dto.UpdateProjectRequest;
import pl.settly.settly_api.projects.model.Project;
import pl.settly.settly_api.projects.model.ProjectMember;
import pl.settly.settly_api.projects.repository.ProjectMemberRepository;
import pl.settly.settly_api.projects.repository.ProjectRepository;

@Service
public class ProjectService {

  private final ProjectRepository projectRepository;
  private final ProjectMemberRepository projectMemberRepository;
  private final UserRepository userRepository;
  private final FriendshipService friendshipService;
  private final ProjectMapper projectMapper;
  private final ExpenseRepository expenseRepository;
  private final DebtRepository debtRepository;

  public ProjectService(
      ProjectRepository projectRepository,
      ProjectMemberRepository projectMemberRepository,
      UserRepository userRepository,
      FriendshipService friendshipService,
      ProjectMapper projectMapper,
      ExpenseRepository expenseRepository,
      DebtRepository debtRepository) {
    this.projectRepository = projectRepository;
    this.projectMemberRepository = projectMemberRepository;
    this.userRepository = userRepository;
    this.friendshipService = friendshipService;
    this.projectMapper = projectMapper;
    this.expenseRepository = expenseRepository;
    this.debtRepository = debtRepository;
  }

  @Transactional
  public ProjectResponse createProject(CreateProjectRequest request, UUID userId) {
    Project project =
        projectRepository.save(
            Project.builder()
                .name(request.name())
                .description(request.description())
                .projectOwner(userRepository.getReferenceById(userId))
                .build());

    projectMemberRepository.save(
        ProjectMember.builder()
            .project(project)
            .user(userRepository.getReferenceById(userId))
            .build());

    return projectMapper.toProjectResponse(project, 1);
  }

  @Transactional(readOnly = true)
  public List<ProjectResponse> getMyProjects(UUID userId) {
    List<Project> projects = projectRepository.findAllForMember(userId);
    if (projects.isEmpty()) {
      return List.of();
    }

    // What each project has cost, in one grouped query rather than per project.
    Map<UUID, ProjectExpenseTotals> totals =
        expenseRepository.sumByProject(projects.stream().map(Project::getId).toList()).stream()
            .collect(Collectors.toMap(ProjectExpenseTotals::getProjectId, t -> t));

    return projects.stream()
        .map(
            p ->
                withTotals(
                    projectMapper.toProjectResponse(
                        p, projectMemberRepository.countByProjectId(p.getId())),
                    totals.get(p.getId())))
        .toList();
  }

  /** Attaches what a project has cost; a project with no expenses reads as 0. */
  private ProjectResponse withTotals(ProjectResponse response, ProjectExpenseTotals totals) {
    return totals == null
        ? response.withExpenses(0, BigDecimal.ZERO)
        : response.withExpenses(totals.getExpenseCount(), totals.getTotal());
  }

  @Transactional(readOnly = true)
  public ProjectResponse getProject(UUID projectId, UUID userId) {
    Project project = requireMember(projectId, userId);
    ProjectResponse response =
        projectMapper.toProjectResponse(
            project, projectMemberRepository.countByProjectId(projectId));

    List<ProjectExpenseTotals> totals = expenseRepository.sumByProject(List.of(projectId));
    return withTotals(response, totals.isEmpty() ? null : totals.get(0));
  }

  @Transactional
  public ProjectResponse updateProject(UUID projectId, UpdateProjectRequest request, UUID userId) {
    Project project = requireOwner(projectId, userId);

    if (request.name() != null && !request.name().isBlank()) {
      project.setName(request.name());
    }
    if (request.description() != null) {
      project.setDescription(request.description());
    }
    if (request.status() != null) {
      project.setStatus(request.status());
    }

    return projectMapper.toProjectResponse(
        projectRepository.save(project), projectMemberRepository.countByProjectId(projectId));
  }

  @Transactional
  public void deleteProject(UUID projectId, UUID userId) {
    requireOwner(projectId, userId);

    // Expenses and settlements are real money and outlive the project: detach them
    // (drop the grouping) rather than delete them. This is also load-bearing —
    // debts.project_id carries a FK, so a project that had ever been settled up
    // could not be deleted at all without unlinking it first.
    expenseRepository.detachFromProject(projectId);
    debtRepository.detachFromProject(projectId);

    projectMemberRepository.deleteByProjectId(projectId);
    projectRepository.deleteById(projectId);
  }

  @Transactional(readOnly = true)
  public List<ProjectMemberResponse> getMembers(UUID projectId, UUID userId) {
    Project project = requireMember(projectId, userId);
    UUID ownerId = project.getProjectOwner() == null ? null : project.getProjectOwner().getId();

    return projectMemberRepository.findByProjectId(projectId).stream()
        .map(m -> projectMapper.toProjectMemberResponse(m, m.getUser().getId().equals(ownerId)))
        .toList();
  }

  @Transactional
  public ProjectMemberResponse addMember(
      UUID projectId, AddProjectMemberRequest request, UUID userId) {
    Project project = requireOwner(projectId, userId);
    UUID newMemberId = request.userId();

    if (newMemberId.equals(userId)) {
      throw new IllegalArgumentException("You are already a member of this project");
    }
    if (!friendshipService.areFriends(userId, newMemberId)) {
      throw new IllegalArgumentException("You can only add friends to a project");
    }
    if (projectMemberRepository.existsByProjectIdAndUserId(projectId, newMemberId)) {
      throw new IllegalArgumentException("User is already a member of this project");
    }

    ProjectMember member =
        projectMemberRepository.save(
            ProjectMember.builder()
                .project(project)
                .user(userRepository.getReferenceById(newMemberId))
                .build());

    return projectMapper.toProjectMemberResponse(member, false);
  }

  @Transactional
  public void removeMember(UUID projectId, UUID memberUserId, UUID userId) {
    Project project = requireOwner(projectId, userId);

    if (memberUserId.equals(project.getProjectOwner().getId())) {
      throw new IllegalArgumentException("The project owner cannot be removed");
    }

    ProjectMember member =
        projectMemberRepository
            .findByProjectIdAndUserId(projectId, memberUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Member not found"));

    projectMemberRepository.delete(member);
  }

  @Transactional
  public void leaveProject(UUID projectId, UUID userId) {
    Project project =
        projectRepository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project does not exist"));

    if (project.getProjectOwner() != null && project.getProjectOwner().getId().equals(userId)) {
      throw new IllegalArgumentException("The owner cannot leave the project; delete it instead");
    }

    ProjectMember member =
        projectMemberRepository
            .findByProjectIdAndUserId(projectId, userId)
            .orElseThrow(
                () -> new ResourceNotFoundException("You are not a member of this project"));

    projectMemberRepository.delete(member);
  }

  private Project requireMember(UUID projectId, UUID userId) {
    Project project =
        projectRepository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project does not exist"));
    if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
      throw new ResourceNotFoundException("Project does not exist");
    }
    return project;
  }

  private Project requireOwner(UUID projectId, UUID userId) {
    Project project =
        projectRepository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project does not exist"));
    if (project.getProjectOwner() == null || !project.getProjectOwner().getId().equals(userId)) {
      throw new IllegalArgumentException("Only the project owner can perform this action");
    }
    return project;
  }
}
