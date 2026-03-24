package pl.settly.settly_api.expenses.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        UUID user,
        UUID project,
        String shop,
        String note,
        BigDecimal totalAmount,
        Boolean isScanned,
        LocalDate date,
        Instant createdAt) {}
