package pl.settly.settly_api.debts.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
import org.hibernate.annotations.UuidGenerator;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.projects.model.Project;

/**
 * A recorded settlement between two users: {@code fromUser} (the debtor) paid {@code toUser} (the
 * creditor) {@code amount}. Acts as the payment audit trail; live balances are derived from {@code
 * expense_splits}.
 */
@Entity
@Table(name = "debts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Debt {
  @Id @UuidGenerator private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "project_id")
  private Project project;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "from_user")
  private User fromUser;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "to_user")
  private User toUser;

  @Column(name = "amount", precision = 10, scale = 2, nullable = false)
  private BigDecimal amount;

  /** The currency {@link #amount} is in - the creditor's base currency at settle-up time. */
  @Column(name = "currency", length = 3, nullable = false)
  private String currency;

  @Column(name = "settled")
  private Boolean settled;

  @Column(name = "settled_at")
  private Instant settledAt;

  @Column(name = "created_at")
  @CreationTimestamp
  private Instant createdAt;
}
