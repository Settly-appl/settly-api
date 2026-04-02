package pl.settly.settly_api.expenses.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.projects.model.Project;

@Entity
@Table(name = "expenses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Expense {
  @Id @UuidGenerator private UUID id;

  @ManyToOne
  @JoinColumn(name = "user_id", nullable = true)
  private User user;

  @ManyToOne
  @JoinColumn(name = "project_id", nullable = true)
  private Project project;

  @Column(name = "shop", length = 255, nullable = true)
  private String shop;

  @Column(name = "note", length = 500, nullable = true)
  private String note;

  @Column(name = "category", length = 500, nullable = true)
  private String category;

  @Column(name = "currency", length = 3, nullable = false)
  private String currency;

  @Column(name = "total_amount",length = 500, nullable = true)
  private BigDecimal totalAmount;

  @Builder.Default
  @Column(name = "scanned", nullable = true)
  private Boolean isScanned = false;

  @Column(name = "date", nullable = false)
  private LocalDate date;

  @Column(name = "created_at")
  @CreationTimestamp
  private Instant createdAt;
}
