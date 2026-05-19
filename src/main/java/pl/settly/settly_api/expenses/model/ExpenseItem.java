package pl.settly.settly_api.expenses.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "expense_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseItem {
  @Id @UuidGenerator private UUID id;

  @ManyToOne
  @JoinColumn(name = "expense_id", nullable = true)
  private Expense expense;

  @Column(name = "name", length = 255, nullable = false)
  private String name;

  @Column(name = "price", precision = 10, scale = 2, nullable = false)
  private BigDecimal price;

  @Builder.Default
  @Column(name = "quantity", precision = 10, scale = 3, nullable = false)
  private BigDecimal quantity = BigDecimal.ONE;

  @Column(name = "category", length = 50, nullable = true)
  private String category;
}
