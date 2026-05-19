package pl.settly.settly_api.expenses.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import pl.settly.settly_api.expenses.model.ExpenseSplitType;

public record ExpenseSplitResponse(
    UUID id,
    UUID expenseId,
    UUID userId,
    ExpenseSplitType splitType,
    BigDecimal amount,
    boolean settled,
    Instant settledAt) {}
