package pl.settly.settly_api.debts.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A recorded settlement: {@code fromUserId} (debtor) paid {@code toUserId} (creditor). */
public record DebtResponse(
    UUID id,
    UUID projectId,
    UUID fromUserId,
    UUID toUserId,
    BigDecimal amount,
    boolean settled,
    Instant settledAt,
    Instant createdAt) {}
