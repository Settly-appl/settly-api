package pl.settly.settly_api.expenses.dto;

public record SearchExpenseRequest(
        int pageNumber, int pageSize, String sortBy, String sortDirection) {}
