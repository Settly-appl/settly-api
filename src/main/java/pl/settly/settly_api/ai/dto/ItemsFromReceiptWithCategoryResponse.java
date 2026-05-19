package pl.settly.settly_api.ai.dto;

import java.util.List;

public record ItemsFromReceiptWithCategoryResponse(String category, List<ReceiptItem> items) {}
