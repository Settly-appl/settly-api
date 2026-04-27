package pl.settly.settly_api.expenses.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.expenses.model.ExpenseItemSplit;

public interface ExpenseItemSplitRepository extends JpaRepository<ExpenseItemSplit, UUID> {
  List<ExpenseItemSplit> findByExpenseItemExpenseId(UUID expenseId);

  @Query(
      """
      select distinct split.user
      from ExpenseItemSplit split
      where split.expenseItem.id = :expenseItemId
      """)
  List<User> findDistinctUsersByExpenseItemId(UUID expenseItemId);

  boolean existsByExpenseItemId(UUID expenseItemId);
}
