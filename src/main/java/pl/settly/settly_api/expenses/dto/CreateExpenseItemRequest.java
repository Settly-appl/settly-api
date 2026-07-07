package pl.settly.settly_api.expenses.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateExpenseItemRequest(
    @NotBlank String name,
    @NotNull @DecimalMin("0.01")
        @Digits(integer = 8, fraction = 2, message = "Price can have at most 2 decimal places")
        BigDecimal price,
    @DecimalMin("0.001")
        @Digits(integer = 7, fraction = 3, message = "Quantity can have at most 3 decimal places")
        BigDecimal quantity,
    String category) {}
