package pl.settly.settly_api.expenses.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ItemSplitAssignment(
    @NotNull UUID expenseItemId, @NotNull @Size(min = 1) List<UUID> userIds) {}
