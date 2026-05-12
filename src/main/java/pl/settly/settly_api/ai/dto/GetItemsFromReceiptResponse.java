package pl.settly.settly_api.ai.dto;

import java.util.List;

public record GetItemsFromReceiptResponse(List<ReceiptItem> items) {}
