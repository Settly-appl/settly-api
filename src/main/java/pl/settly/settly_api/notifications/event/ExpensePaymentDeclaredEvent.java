package pl.settly.settly_api.notifications.event;

import java.util.UUID;

/**
 * A participant declared (or retracted) "I paid my share" on an expense. This is a claim aimed at
 * the expense owner — a suggestion to double-check and confirm the settlement — not a settlement
 * itself, so it is a separate event from {@link ExpenseSettlementChangedEvent}.
 */
public record ExpensePaymentDeclaredEvent(
    UUID actorId, UUID ownerId, UUID expenseId, String shop, boolean declared) {}
