package pl.settly.settly_api.projects.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.settly.settly_api.projects.model.ProjectMember;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {
  boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

  Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);

  List<ProjectMember> findByProjectId(UUID projectId);

  long countByProjectId(UUID projectId);

  void deleteByProjectId(UUID projectId);
}
