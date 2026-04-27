package pl.settly.settly_api.expenses.dto;

import java.util.UUID;

public record ExpenseItemSplitUserResponse(
    UUID id, String username, String displayName, String avatarUrl) {}
