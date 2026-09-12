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

  @Column(name = "category", length = 50, nullable = true)
  private String category;

  @Column(name = "currency", length = 3, nullable = false)
  private String currency;

  @Column(name = "total_amount", precision = 10, scale = 2, nullable = true)
  private BigDecimal totalAmount;

  /**
   * The user's base currency at the moment the expense was created, snapshotted so changing the
   * base later does not restate history.
   */
  @Column(name = "base_currency", length = 3, nullable = false)
  private String baseCurrency;

  /**
   * How many base units one unit of {@link #currency} is worth - the rate the user actually got
   * when they bought the foreign currency, not a market rate. 1 GBP = 4.85 PLN is 4.85. Always 1
   * when the expense is already in the base currency.
   */
  @Column(name = "rate_to_base", precision = 18, scale = 8, nullable = false)
  private BigDecimal rateToBase;

  /**
   * {@link #totalAmount} converted at {@link #rateToBase}. Stored rather than derived so balances
   * and project totals are a plain SQL sum, and so a later rate change cannot silently restate what
   * a past trip cost.
   */
  @Column(name = "base_amount", precision = 12, scale = 2, nullable = true)
  private BigDecimal baseAmount;

  @Builder.Default
  @Column(name = "scanned", nullable = true)
  private Boolean isScanned = false;

  @Column(name = "date", nullable = false)
  private LocalDate date;

  @Column(name = "created_at")
  @CreationTimestamp
  private Instant createdAt;
}
