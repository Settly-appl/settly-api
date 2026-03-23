package pl.settly.settly_api.expenses.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RequestExpenseResponse(
        UUID id,
        UUID userId,
        UUID projectId,
        String shop,
        String note,
        BigDecimal totalAmount,
        Boolean isScanned,
        LocalDate date,
        Instant createdAt) {}
