package pl.settly.settly_api.projects.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import pl.settly.settly_api.projects.repository.ProjectMemberRepository;

@Service
public class ProjectAccessService {

  private final ProjectMemberRepository projectMemberRepository;

  public ProjectAccessService(ProjectMemberRepository projectMemberRepository) {
    this.projectMemberRepository = projectMemberRepository;
  }

  public boolean isMember(UUID projectId, UUID userId) {
    return projectMemberRepository.existsByProjectIdAndUserId(projectId, userId);
  }
}
