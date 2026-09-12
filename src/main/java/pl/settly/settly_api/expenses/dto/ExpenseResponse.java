package pl.settly.settly_api.expenses.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An expense as seen by the requesting user.
 *
 * <p>The settlement fields are <em>viewer-relative</em> and are populated by the service (MapStruct
 * leaves them null), so the list can render settled state without an N+1 per expense:
 *
 * <ul>
 *   <li>{@code splitCount} — participants owing the owner (the owner's own split row is excluded).
 *       0 means a personal expense: nothing to settle.
 *   <li>{@code settledCount} — how many of those have been settled.
 *   <li>{@code settled} — if the viewer owns the expense: every participant has settled. If the
 *       viewer is a participant: their own split is settled. If the viewer is neither (a project
 *       member looking at someone else's expense in the shared ledger): whether everyone has
 *       settled.
 *   <li>{@code canSettle} — whether this viewer may settle it at all. False for a bystander seeing
 *       it only through project membership; the backend would refuse, so the UI must not offer it.
 *   <li>{@code declaredCount} — unsettled participants who claim they already paid (a suggestion
 *       for the owner to verify and confirm, not a fact).
 *   <li>{@code declared} — the viewer's own unsettled share carries such a claim. Always false for
 *       the owner and bystanders.
 * </ul>
 *
 * <p>Money comes in two denominations: {@code totalAmount}/{@code currency} is what was actually
 * spent, {@code baseAmount}/{@code baseCurrency} the same sum converted at {@code rateToBase} —
 * the rate the payer got when they bought the currency. They are equal, and the rate 1, for an
 * expense already in the base currency.
 */
public record ExpenseResponse(
    UUID id,
    UUID userId,
    UUID projectId,
    String shop,
    String note,
    String category,
    String currency,
    BigDecimal totalAmount,
    String baseCurrency,
    BigDecimal rateToBase,
    BigDecimal baseAmount,
    Boolean isScanned,
    LocalDate date,
    Instant createdAt,
    Integer splitCount,
    Integer settledCount,
    Boolean settled,
    Boolean canSettle,
    Integer declaredCount,
    Boolean declared) {

  public ExpenseResponse withSettlement(
      int splitCount,
      int settledCount,
      boolean settled,
      boolean canSettle,
      int declaredCount,
      boolean declared) {
    return new ExpenseResponse(
        id,
        userId,
        projectId,
        shop,
        note,
        category,
        currency,
        totalAmount,
        baseCurrency,
        rateToBase,
        baseAmount,
        isScanned,
        date,
        createdAt,
        splitCount,
        settledCount,
        settled,
        canSettle,
        declaredCount,
        declared);
  }
}
