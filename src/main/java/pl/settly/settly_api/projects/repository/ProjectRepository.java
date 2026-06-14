package pl.settly.settly_api.projects.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.settly.settly_api.projects.model.Project;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

  /** Projects the user belongs to (as owner or member), newest first. */
  @Query(
      "SELECT p FROM Project p JOIN ProjectMember m ON m.project.id = p.id"
          + " WHERE m.user.id = :userId ORDER BY p.createdAt DESC")
  List<Project> findAllForMember(@Param("userId") UUID userId);
}
