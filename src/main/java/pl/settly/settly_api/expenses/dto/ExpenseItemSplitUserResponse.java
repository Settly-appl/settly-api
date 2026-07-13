package pl.settly.settly_api.expenses.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** An assignee of a product, with their share of it (which may be unequal). */
public record ExpenseItemSplitUserResponse(
    UUID id, String username, String displayName, String avatarUrl, BigDecimal amount) {}
