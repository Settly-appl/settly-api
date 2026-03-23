package pl.settly.settly_api.expenses.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RequestExpense(
        @Size(max = 255, message = "Shop name cannot exceed 255 characters") String shop,
        @Size(max = 500, message = "Note cannot exceed 500 characters") String note,
        @NotNull(message = "Total amount is required") @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
                BigDecimal totalAmount,
        @NotNull(message = "Date is required") LocalDate date,
        UUID projectId) {}
