package pl.settly.settly_api.debts.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Net balance between the current user and a counterparty. A positive {@code netAmount} means the
 * counterparty owes the current user; negative means the current user owes the counterparty.
 *
 * <p>{@code currency} is the current user's base currency: shares spent in other currencies are
 * netted at the rate stored on each expense, so one balance stands for the whole relationship
 * rather than one row per currency.
 */
public record FriendBalanceResponse(
    UUID userId,
    String displayName,
    String username,
    String avatarUrl,
    BigDecimal netAmount,
    String currency) {}
