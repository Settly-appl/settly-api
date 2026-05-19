package pl.settly.settly_api.ai.dto;

public record DataForSingleExpenseFromReceiptResponse(
    String currency, String category, Double totalAmount) {}
