package pl.settly.settly_api.expenses.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** How much has been spent in a project, and across how many expenses. */
public interface ProjectExpenseTotals {
  UUID getProjectId();

  long getExpenseCount();

  BigDecimal getTotal();
}
