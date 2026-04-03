package pl.settly.settly_api.expenses.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record SplitParticipant(
    @NotNull UUID friendId, @DecimalMin(value = "0.01") BigDecimal amount) {}
