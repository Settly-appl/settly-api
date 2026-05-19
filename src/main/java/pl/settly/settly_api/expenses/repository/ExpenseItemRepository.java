package pl.settly.settly_api.expenses.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.settly.settly_api.expenses.model.ExpenseItem;

public interface ExpenseItemRepository extends JpaRepository<ExpenseItem, UUID> {
  List<ExpenseItem> findByExpenseId(UUID expenseId);

  boolean existsByIdAndExpense_User_Id(UUID id, UUID userId);
}
