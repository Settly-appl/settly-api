package pl.settly.settly_api.expenses.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.UuidGenerator;
import pl.settly.settly_api.auth.user.model.User;

@Entity
@Table(name = "expense_splits")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseSplit {
  @Id @UuidGenerator private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "expense_id")
  private Expense expense;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(name = "split_type", nullable = false)
  private ExpenseSplitType expenseSplitType;

  @Column(name = "amount", precision = 10, scale = 2, nullable = false)
  private BigDecimal amount;

  @Column(name = "settled", nullable = false)
  private Boolean settled;

  @Column(name = "settled_at")
  private Instant settledAt;
}
