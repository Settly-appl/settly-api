package pl.settly.settly_api.projects.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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

  /**
   * Currency the trip is spent in, and the rate its members bought it at. Expenses added to the
   * project inherit both, so the rate is typed once per trip rather than once per expense; any
   * single expense may still override them.
   */
  @Column(name = "default_currency", length = 3, nullable = true)
  private String defaultCurrency;

  @Column(name = "default_rate_to_base", precision = 18, scale = 8, nullable = true)
  private BigDecimal defaultRateToBase;

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
