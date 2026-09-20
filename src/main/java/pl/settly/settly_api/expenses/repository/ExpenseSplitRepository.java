package pl.settly.settly_api.expenses.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.settly.settly_api.debts.dto.BalanceAggregate;
import pl.settly.settly_api.expenses.dto.PairDebtSummary;
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
   * Unsettled shares grouped by (debtor, creditor), which is what the settle-up reminder needs.
   *
   * <p>Grouping by debtor alone — as this did — answers "who holds an unsettled share", not "who
   * should send money". Someone who owes 50 and is owed 200 by the same person was nagged to pay,
   * although the other side is the one who has to transfer anything. Netting has to happen per
   * pair, so the query hands both directions over and the job subtracts them.
   *
   * <p>The owner's own row is excluded — nobody owes it. Shares already declared paid are still
   * returned but counted separately: a declaration is not a payment, so it must not move the
   * balance, while nagging someone who is waiting for the owner to confirm is exactly the noise
   * the declaration was meant to stop.
   *
   * <p>Sums {@code baseAmount}, not {@code amount}: the latter would add pounds to zloty and remind
   * people of a number that is not any amount of money.
   */
  @Query(
      "SELECT s.user.id AS debtorId, s.expense.user.id AS creditorId,"
          + " SUM(s.baseAmount) AS total,"
          + " SUM(CASE WHEN s.declaredPaid = false THEN 1 ELSE 0 END) AS undeclaredCount"
          + " FROM ExpenseSplit s"
          + " WHERE s.settled = false AND s.user.id <> s.expense.user.id"
          + " GROUP BY s.user.id, s.expense.user.id")
  List<PairDebtSummary> sumUnsettledByPair();

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

  /**
   * The same set as {@link #findUnsettledBetween}, with the expense and its project fetched.
   *
   * <p>Separate from that method on purpose: settle-up only touches the splits, while listing what
   * a balance is made of reads every expense behind it, and a lazy load per row would be an N+1
   * over the whole relationship.
   */
  @Query(
      "SELECT s FROM ExpenseSplit s JOIN FETCH s.expense e LEFT JOIN FETCH e.project"
          + " WHERE e.user.id = :creditorId AND s.user.id = :debtorId AND s.settled = false"
          + " AND (:projectId IS NULL OR e.project.id = :projectId)")
  List<ExpenseSplit> findUnsettledBetweenWithExpense(
      @Param("creditorId") UUID creditorId,
      @Param("debtorId") UUID debtorId,
      @Param("projectId") UUID projectId);

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
