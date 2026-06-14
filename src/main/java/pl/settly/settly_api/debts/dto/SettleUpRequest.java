package pl.settly.settly_api.debts.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request to settle up everything a debtor owes the current user (the creditor), optionally scoped
 * to a single project.
 */
public record SettleUpRequest(@NotNull UUID debtorUserId, UUID projectId) {}
