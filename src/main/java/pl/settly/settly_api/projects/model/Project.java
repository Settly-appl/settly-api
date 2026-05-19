package pl.settly.settly_api.projects.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import pl.settly.settly_api.auth.user.model.User;

@Entity
@Table(name = "projects")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {
  @Id @UuidGenerator private UUID id;

  @ManyToOne
  @JoinColumn(name = "owner_id", nullable = true)
  private User projectOwner;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "description", nullable = true)
  private String description;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private ProjectStatus status = ProjectStatus.ACTIVE;

  @Column(name = "created_at")
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "updated_at")
  @UpdateTimestamp
  private Instant updatedAt;
}
