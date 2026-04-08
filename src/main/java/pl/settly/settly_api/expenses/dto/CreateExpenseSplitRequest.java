package pl.settly.settly_api.expenses.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import pl.settly.settly_api.expenses.model.ExpenseSplitType;

public record CreateExpenseSplitRequest(
    @NotNull ExpenseSplitType expenseSplitType,
    @NotNull @Size(min = 1) @Valid List<SplitParticipant> participants,
    @Valid List<ItemSplitAssignment> itemAssignments) {}
