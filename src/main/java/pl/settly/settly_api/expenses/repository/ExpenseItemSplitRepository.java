package pl.settly.settly_api.expenses.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.settly.settly_api.expenses.model.ExpenseItemSplit;

public interface ExpenseItemSplitRepository extends JpaRepository<ExpenseItemSplit, UUID> {
  List<ExpenseItemSplit> findByExpenseItemExpenseId(UUID expenseId);

  boolean existsByExpenseItemId(UUID expenseItemId);
}
