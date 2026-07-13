package pl.settly.settly_api.expenses.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.settly.settly_api.expenses.dto.ProjectExpenseTotals;
import pl.settly.settly_api.expenses.model.Expense;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {
  Optional<Expense> findByIdAndUser_Id(UUID id, UUID userId);

  /**
   * Expenses visible to the user (they own it, or they are in its split), optionally narrowed to a
   * category and/or a project. A null/blank filter means "don't narrow by that".
   */
  @Query(
      "SELECT DISTINCT e FROM Expense e LEFT JOIN ExpenseSplit es ON es.expense.id = e.id WHERE "
          + "(es.user.id = :userId OR e.user.id = :userId) "
          + "AND (:category IS NULL OR :category = '' OR e.category = :category) "
          + "AND (:projectId IS NULL OR e.project.id = :projectId)")
  Page<Expense> findExpenses(
      @Param("userId") UUID userId,
      @Param("category") String category,
      @Param("projectId") UUID projectId,
      Pageable pageable);

  /**
   * Drops a project's grouping from its expenses without touching the expenses themselves — they
   * are real spending and must outlive the project.
   */
  @Modifying
  @Query("UPDATE Expense e SET e.project = null WHERE e.project.id = :projectId")
  void detachFromProject(@Param("projectId") UUID projectId);

  /** Expense count and total per project, for a user's project list — one query, not an N+1. */
  @Query(
      "SELECT e.project.id AS projectId, COUNT(e) AS expenseCount, SUM(e.totalAmount) AS total"
          + " FROM Expense e"
          + " WHERE e.project.id IN :projectIds"
          + " GROUP BY e.project.id")
  List<ProjectExpenseTotals> sumByProject(@Param("projectIds") Collection<UUID> projectIds);
}
