package pl.settly.settly_api.expenses.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Someone who currently owes money: how many shares are outstanding and for how much in total. */
public interface DebtorSummary {
  UUID getUserId();

  long getUnsettledCount();

  BigDecimal getTotal();
}
