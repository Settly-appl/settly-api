package pl.settly.settly_api.expenses.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code rateToBase} is how many units of the user's base currency one unit of {@code currency} is
 * worth - the rate they got when they bought it (1 GBP = 4.85 PLN is 4.85). Required when {@code
 * currency} differs from the base and the expense's project carries no default rate; ignored when
 * the expense is already in the base currency, where the rate is 1 by definition.
 */
public record CreateExpenseRequest(
    @Size(max = 255, message = "Shop name cannot exceed 255 characters") String shop,
    @Size(max = 500, message = "Note cannot exceed 500 characters") String note,
    @Pattern(regexp = "^[A-Za-z]{3}$", message = "Currency must be a 3-letter code") String currency,
    @DecimalMin(value = "0.00000001", message = "Exchange rate must be greater than 0")
        @Digits(integer = 10, fraction = 8, message = "Exchange rate is too precise")
        BigDecimal rateToBase,
    @Size(max = 50, message = "Category cannot be longer than 50 characters") String category,
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @Digits(integer = 8, fraction = 2, message = "Amount can have at most 2 decimal places")
        BigDecimal totalAmount,
    @NotNull(message = "Date is required") LocalDate date,
    UUID projectId) {}
