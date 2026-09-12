package pl.settly.settly_api.expenses.dto;

/**
 * The requesting user's share of an expense, in the currency it was spent in ({@code amount} /
 * {@code currency}) and converted at the expense's rate ({@code baseAmount} / {@code baseCurrency}).
 */
public record ExpenseUserShareResponse(
    Double amount, String currency, Double baseAmount, String baseCurrency) {}
