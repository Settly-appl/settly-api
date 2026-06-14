package pl.settly.settly_api.debts.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Projection for the per-counterparty unsettled-split aggregation queries. */
public interface BalanceAggregate {
  UUID getUserId();

  BigDecimal getTotal();
}
