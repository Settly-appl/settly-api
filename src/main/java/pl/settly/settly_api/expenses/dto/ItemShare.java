package pl.settly.settly_api.expenses.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * One participant's explicit share of a single product, so the same item can be split unequally
 * ("you had the steak, I had the salad"). The shares of an item must add up to that item's total.
 */
public record ItemShare(
    @NotNull UUID userId,
    @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal amount) {}
