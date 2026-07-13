package pl.settly.settly_api.expenses.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Who is on a given product, and optionally for how much each.
 *
 * <p>Provide exactly one of:
 *
 * <ul>
 *   <li>{@code userIds} — split the item equally between them. The server does the rounding, so the
 *       shares always add up to the item total exactly.
 *   <li>{@code shares} — an explicit amount per person, for an unequal split of the same product
 *       ("you had the steak, I had the salad"). They must add up to the item total.
 * </ul>
 *
 * <p>{@code userIds} is kept (rather than replaced) so an older client — e.g. a cached PWA — keeps
 * working after this ships.
 */
public record ItemSplitAssignment(
    @NotNull UUID expenseItemId, List<UUID> userIds, @Valid List<ItemShare> shares) {

  /** True when the caller gave per-person amounts rather than asking for an equal split. */
  public boolean hasExplicitShares() {
    return shares != null && !shares.isEmpty();
  }

  /** Everyone on this item, whichever form was used. */
  public List<UUID> assignedUserIds() {
    return hasExplicitShares()
        ? shares.stream().map(ItemShare::userId).toList()
        : (userIds == null ? List.of() : userIds);
  }
}
