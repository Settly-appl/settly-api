package pl.settly.settly_api.debts.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One unsettled share standing behind a balance: the expense it comes from, how much of it is owed,
 * and in which direction.
 *
 * <p>{@code owedToMe} is relative to the caller — true when the counterparty owes this share to
 * them, false when they owe it to the counterparty. The balance is the difference of the two sides,
 * so both are needed to explain it.
 *
 * <p>{@code shareBaseAmount} is null exactly when the expense has no rate (V10): the balance leaves
 * that share out, and a caller that substituted {@code shareAmount} here would put pounds into a
 * zloty total — the bug the null exists to prevent. Such a row is listed anyway, because a share
 * missing from the balance is something the user has to be told about, not something to hide.
 */
public record BalanceItemResponse(
    UUID expenseId,
    String shop,
    String category,
    LocalDate date,
    UUID projectId,
    String projectName,
    BigDecimal shareAmount,
    String currency,
    BigDecimal shareBaseAmount,
    String baseCurrency,
    boolean owedToMe,
    boolean declaredPaid) {}
