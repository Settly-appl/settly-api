package pl.settly.settly_api.expenses.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateExpenseItemRequest(
    @NotBlank String name,
    @NotNull @DecimalMin("0.01") BigDecimal price,
    @DecimalMin("0.001") BigDecimal quantity,
    String category) {}
