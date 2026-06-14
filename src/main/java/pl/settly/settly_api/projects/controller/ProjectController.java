package pl.settly.settly_api.projects.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.settly.settly_api.projects.dto.AddProjectMemberRequest;
import pl.settly.settly_api.projects.dto.CreateProjectRequest;
import pl.settly.settly_api.projects.dto.ProjectMemberResponse;
import pl.settly.settly_api.projects.dto.ProjectResponse;
import pl.settly.settly_api.projects.dto.UpdateProjectRequest;
import pl.settly.settly_api.projects.service.ProjectService;

@RestController
@RequestMapping("/projects")
public class ProjectController {

  private final ProjectService projectService;

  public ProjectController(ProjectService projectService) {
    this.projectService = projectService;
  }

  private static UUID userId(Authentication authentication) {
    return UUID.fromString(authentication.getName());
  }

  @PostMapping
  public ResponseEntity<ProjectResponse> createProject(
      @Valid @RequestBody CreateProjectRequest request, Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(projectService.createProject(request, userId(authentication)));
  }

  @GetMapping
  public ResponseEntity<List<ProjectResponse>> getMyProjects(Authentication authentication) {
    return ResponseEntity.ok(projectService.getMyProjects(userId(authentication)));
  }

  @GetMapping("/{projectId}")
  public ResponseEntity<ProjectResponse> getProject(
      @PathVariable UUID projectId, Authentication authentication) {
    return ResponseEntity.ok(projectService.getProject(projectId, userId(authentication)));
  }

  @PatchMapping("/{projectId}")
  public ResponseEntity<ProjectResponse> updateProject(
      @PathVariable UUID projectId,
      @Valid @RequestBody UpdateProjectRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        projectService.updateProject(projectId, request, userId(authentication)));
  }

  @DeleteMapping("/{projectId}")
  public ResponseEntity<Void> deleteProject(
      @PathVariable UUID projectId, Authentication authentication) {
    projectService.deleteProject(projectId, userId(authentication));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{projectId}/members")
  public ResponseEntity<List<ProjectMemberResponse>> getMembers(
      @PathVariable UUID projectId, Authentication authentication) {
    return ResponseEntity.ok(projectService.getMembers(projectId, userId(authentication)));
  }

  @PostMapping("/{projectId}/members")
  public ResponseEntity<ProjectMemberResponse> addMember(
      @PathVariable UUID projectId,
      @Valid @RequestBody AddProjectMemberRequest request,
      Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(projectService.addMember(projectId, request, userId(authentication)));
  }

  @DeleteMapping("/{projectId}/members/{memberUserId}")
  public ResponseEntity<Void> removeMember(
      @PathVariable UUID projectId,
      @PathVariable UUID memberUserId,
      Authentication authentication) {
    projectService.removeMember(projectId, memberUserId, userId(authentication));
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{projectId}/members/me")
  public ResponseEntity<Void> leaveProject(
      @PathVariable UUID projectId, Authentication authentication) {
    projectService.leaveProject(projectId, userId(authentication));
    return ResponseEntity.noContent().build();
  }
}
