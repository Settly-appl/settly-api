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
 * </ul>
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
    Boolean isScanned,
    LocalDate date,
    Instant createdAt,
    Integer splitCount,
    Integer settledCount,
    Boolean settled,
    Boolean canSettle) {

  public ExpenseResponse withSettlement(
      int splitCount, int settledCount, boolean settled, boolean canSettle) {
    return new ExpenseResponse(
        id,
        userId,
        projectId,
        shop,
        note,
        category,
        currency,
        totalAmount,
        isScanned,
        date,
        createdAt,
        splitCount,
        settledCount,
        settled,
        canSettle);
  }
}
