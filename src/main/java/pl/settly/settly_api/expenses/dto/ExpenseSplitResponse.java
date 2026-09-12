package pl.settly.settly_api.expenses.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import pl.settly.settly_api.expenses.model.ExpenseSplitType;

public record ExpenseSplitResponse(
    UUID id,
    UUID expenseId,
    UUID userId,
    String userDisplayName,
    String userName,
    ExpenseSplitType splitType,
    BigDecimal amount,
    // What `amount` is in, and the same share converted at the expense's rate. Both come
    // from the parent expense - a share is never in a different currency from its expense.
    String currency,
    BigDecimal baseAmount,
    String baseCurrency,
    boolean settled,
    Instant settledAt,
    // The split's user claims they paid — a suggestion for the owner, not a fact.
    boolean declaredPaid,
    Instant declaredAt) {}
