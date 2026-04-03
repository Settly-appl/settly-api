package pl.settly.settly_api.expenses.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.settly.settly_api.expenses.model.ExpenseSplit;

public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, UUID> {
  boolean existsByExpenseId(UUID expenseId);

  List<ExpenseSplit> findByExpenseId(UUID expenseId);

  List<ExpenseSplit> findByUserIdAndSettledFalse(UUID userId);
}
