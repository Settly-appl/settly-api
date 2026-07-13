package pl.settly.settly_api.expenses.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.expenses.model.ExpenseItemSplit;

public interface ExpenseItemSplitRepository extends JpaRepository<ExpenseItemSplit, UUID> {
  List<ExpenseItemSplit> findByExpenseItemExpenseId(UUID expenseId);

  @Query(
      """
      select split.user
      from ExpenseItemSplit split
      where split.expenseItem.id = :expenseItemId
      """)
  List<User> findUsersByExpenseItemId(@Param("expenseItemId") UUID expenseItemId);

  /**
   * The assignees of an item together with each one's share. Fetch-joins the user because callers
   * read it outside a transaction.
   */
  @Query(
      "SELECT s FROM ExpenseItemSplit s JOIN FETCH s.user WHERE s.expenseItem.id = :expenseItemId")
  List<ExpenseItemSplit> findWithUserByExpenseItemId(@Param("expenseItemId") UUID expenseItemId);

  boolean existsByExpenseItemId(UUID expenseItemId);

  boolean existsByExpenseItemIdAndUserId(UUID expenseItemId, UUID userId);
}
