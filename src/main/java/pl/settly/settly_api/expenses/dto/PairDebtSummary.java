package pl.settly.settly_api.expenses.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What one person still owes another, before netting: the unsettled shares the debtor holds on the
 * creditor's expenses.
 *
 * <p>Per pair rather than per person, because "who should send money" is only answerable by
 * comparing the two directions. A per-person total cannot tell the difference between owing 50 and
 * owing 50 while being owed 200.
 *
 * <p>{@code total} covers every unsettled share, while {@code undeclaredCount} leaves out the ones
 * the debtor has already claimed to have paid. The two have different jobs: the balance is netted
 * from the first, because a claim is not a payment, while the second decides whether there is
 * anything left to nag about at all.
 */
public interface PairDebtSummary {
  UUID getDebtorId();

  UUID getCreditorId();

  BigDecimal getTotal();

  long getUndeclaredCount();
}
