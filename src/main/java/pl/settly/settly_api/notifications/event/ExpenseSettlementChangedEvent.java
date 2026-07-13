package pl.settly.settly_api.notifications.event;

import java.util.List;
import java.util.UUID;

/**
 * Someone marked a share of an expense as settled (or took that back).
 *
 * <p>{@code recipientIds} is the other side, never the actor: if the owner settles a participant's
 * share, the participant hears about it; if a participant records that they paid, the owner does.
 * Telling people about their own action would just be noise.
 */
public record ExpenseSettlementChangedEvent(
    UUID actorId, List<UUID> recipientIds, UUID expenseId, String shop, boolean settled) {}
