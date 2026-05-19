package pl.settly.settly_api.expenses.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ExpenseItemResponse(
    UUID id, UUID expenseId, String name, BigDecimal price, BigDecimal quantity, String category) {}
