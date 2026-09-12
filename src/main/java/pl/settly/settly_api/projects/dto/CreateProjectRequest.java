package pl.settly.settly_api.projects.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * {@code defaultCurrency} / {@code defaultRateToBase} are the trip's currency and the rate its
 * members bought it at. Expenses added to the project inherit both, so the rate is typed once per
 * trip instead of once per expense.
 */
public record CreateProjectRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 500) String description,
    @Pattern(regexp = "^[A-Za-z]{3}$", message = "Currency must be a 3-letter code")
        String defaultCurrency,
    @DecimalMin(value = "0.00000001", message = "Exchange rate must be greater than 0")
        @Digits(integer = 10, fraction = 8, message = "Exchange rate is too precise")
        BigDecimal defaultRateToBase) {}
