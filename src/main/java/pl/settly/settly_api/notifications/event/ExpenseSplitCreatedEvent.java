package pl.settly.settly_api.notifications.event;

import java.util.List;
import java.util.UUID;

public record ExpenseSplitCreatedEvent(
    UUID actorId, List<UUID> recipientIds, UUID expenseId, String shop) {}
