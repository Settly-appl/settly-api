package pl.settly.settly_api.expenses.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record SplitParticipant(
    @NotNull UUID friendId,
    // 0.00 is allowed: when the creator takes most of the total, a friend's
    // rounded share can legitimately be 0. Negative amounts stay invalid.
    @DecimalMin(value = "0.00")
        @Digits(integer = 8, fraction = 2, message = "Amount can have at most 2 decimal places")
        BigDecimal amount) {}
