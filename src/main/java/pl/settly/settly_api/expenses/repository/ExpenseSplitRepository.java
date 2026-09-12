package pl.settly.settly_api.expenses.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.settly.settly_api.debts.dto.BalanceAggregate;
import pl.settly.settly_api.expenses.dto.DebtorSummary;
import pl.settly.settly_api.expenses.model.ExpenseSplit;

public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, UUID> {
  boolean existsByExpenseId(UUID expenseId);

  List<ExpenseSplit> findByExpenseId(UUID expenseId);

  /**
   * All splits for a page of expenses in one query, so the list can report settled state without an
   * N+1. Fetches the owner join too, since the caller needs to tell the owner's row apart.
   */
  @Query("SELECT s FROM ExpenseSplit s JOIN FETCH s.expense e WHERE e.id IN :expenseIds")
  List<ExpenseSplit> findByExpenseIdIn(@Param("expenseIds") Collection<UUID> expenseIds);

  boolean existsByExpenseIdAndUserId(UUID expenseId, UUID userId);

  List<ExpenseSplit> findByUserIdAndSettledFalse(UUID userId);

  /** Splits cleared by a given settle-up — used to reverse it. */
  List<ExpenseSplit> findBySettledByDebtId(UUID debtId);

  /**
   * Everyone who currently owes money, with how many shares are outstanding and their total. The
   * owner's own row is excluded — nobody owes it. Shares whose user already declared "I paid" are
   * skipped too: nagging someone who claims to have paid (and is waiting for the owner to confirm)
   * would just be noise. Drives the daily settle-up reminder.
   *
   * <p>Sums {@code baseAmount}, not {@code amount}: the latter would add pounds to zloty and
   * reminds people of a number that is not any amount of money.
   */
  @Query(
      "SELECT s.user.id AS userId, COUNT(s) AS unsettledCount, SUM(s.baseAmount) AS total"
          + " FROM ExpenseSplit s"
          + " WHERE s.settled = false AND s.declaredPaid = false"
          + " AND s.user.id <> s.expense.user.id"
          + " GROUP BY s.user.id")
  List<DebtorSummary> findDebtorsWithUnsettledShares();

  /**
   * Unsettled amounts other users owe the given user (their splits on the user's expenses), in the
   * user's base currency — shares spent abroad are netted at the rate stored on their expense.
   */
  @Query(
      "SELECT s.user.id AS userId, SUM(s.baseAmount) AS total FROM ExpenseSplit s"
          + " WHERE s.expense.user.id = :userId AND s.user.id <> :userId AND s.settled = false"
          + " AND (:projectId IS NULL OR s.expense.project.id = :projectId)"
          + " GROUP BY s.user.id")
  List<BalanceAggregate> sumOwedToUser(
      @Param("userId") UUID userId, @Param("projectId") UUID projectId);

  /** Unsettled amounts the given user owes other users (their splits on others' expenses). */
  @Query(
      "SELECT s.expense.user.id AS userId, SUM(s.baseAmount) AS total FROM ExpenseSplit s"
          + " WHERE s.user.id = :userId AND s.expense.user.id <> :userId AND s.settled = false"
          + " AND (:projectId IS NULL OR s.expense.project.id = :projectId)"
          + " GROUP BY s.expense.user.id")
  List<BalanceAggregate> sumOwedByUser(
      @Param("userId") UUID userId, @Param("projectId") UUID projectId);

  /** A debtor's unsettled splits on the creditor's expenses, optionally scoped to a project. */
  @Query(
      "SELECT s FROM ExpenseSplit s WHERE s.expense.user.id = :creditorId"
          + " AND s.user.id = :debtorId AND s.settled = false"
          + " AND (:projectId IS NULL OR s.expense.project.id = :projectId)")
  List<ExpenseSplit> findUnsettledBetween(
      @Param("creditorId") UUID creditorId,
      @Param("debtorId") UUID debtorId,
      @Param("projectId") UUID projectId);
}
