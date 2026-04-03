package pl.settly.settly_api.expenses.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.settly.settly_api.expenses.model.Expense;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {
  Optional<Expense> findByIdAndUser_Id(UUID id, UUID userId);

  @Query(
      "SELECT e FROM Expense e WHERE e.user.id = :userId "
          + "AND (:category IS NULL OR :category = '' OR e.category = :category)")
  Page<Expense> findExpenses(
      @Param("userId") UUID userId, @Param("category") String category, Pageable pageable);
}
