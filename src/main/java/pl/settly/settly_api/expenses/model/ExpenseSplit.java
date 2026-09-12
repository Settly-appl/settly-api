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
import pl.settly.settly_api.debts.model.Debt;

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

  /**
   * {@link #amount} in the expense owner's base currency. Every balance query sums this rather than
   * {@code amount}, which would add pounds to zloty. Apportioned so the shares of one expense add
   * up to exactly its {@code baseAmount} - the odd grosz goes to the payer, as elsewhere.
   */
  @Column(name = "base_amount", precision = 12, scale = 2, nullable = false)
  private BigDecimal baseAmount;

  @Column(name = "settled", nullable = false)
  private Boolean settled;

  @Column(name = "settled_at")
  private Instant settledAt;

  /**
   * The split's own user claims they paid. A claim, not a fact: it does not affect balances and
   * only the owner settling the share turns it into one. Cleared whenever the share becomes
   * settled.
   */
  @Builder.Default
  @Column(name = "declared_paid", nullable = false)
  private Boolean declaredPaid = false;

  @Column(name = "declared_at")
  private Instant declaredAt;

  /**
   * The settle-up that settled this split, if any. Set only when a bulk settle-up cleared it;
   * splits settled one-by-one leave this null. A split settled by a settle-up must not be unsettled
   * on its own — that would resurrect a balance for money that was actually paid. Reverse the whole
   * settlement instead.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "settled_by_debt_id")
  private Debt settledByDebt;
}
