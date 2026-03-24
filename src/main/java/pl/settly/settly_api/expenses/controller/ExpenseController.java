package pl.settly.settly_api.expenses.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.settly.settly_api.common.search.PagedResponse;
import pl.settly.settly_api.expenses.dto.CreateRequestExpense;
import pl.settly.settly_api.expenses.dto.ExpenseResponse;
import pl.settly.settly_api.expenses.dto.SearchExpenseRequest;
import pl.settly.settly_api.expenses.service.ExpenseService;

@RestController
@RequestMapping("/expenses")
public class ExpenseController {
    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> createExpense(
            @Valid @RequestBody CreateRequestExpense requestExpense, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        expenseService.createExpense(
                                requestExpense, UUID.fromString(authentication.getName())));
    }

    @GetMapping("/{expenseId}")
    public ResponseEntity<ExpenseResponse> getExpenseById(
            @PathVariable UUID expenseId, Authentication authentication) {
        return ResponseEntity.ok(
                expenseService.getExpense(expenseId, UUID.fromString(authentication.getName())));
    }

    @GetMapping("/commands/search")
    public ResponseEntity<PagedResponse<ExpenseResponse>> getAllExpenses(
            @Valid @RequestBody SearchExpenseRequest searchRequest, Authentication authentication) {
        return ResponseEntity.ok(
                expenseService.searchExpenses(searchRequest, UUID.fromString(authentication.getName())));
    }

    @PutMapping("/{expenseId}")
    public ResponseEntity<ExpenseResponse> updateExpense(
            @PathVariable UUID expenseId,
            @Valid @RequestBody CreateRequestExpense request,
            Authentication authentication) {
        return ResponseEntity.ok(
                expenseService.updateExpense(
                        expenseId, UUID.fromString(authentication.getName()), request));
    }

    @DeleteMapping("/{expenseId}")
    public ResponseEntity<Void> deleteExpense(
            @PathVariable UUID expenseId, Authentication authentication) {
        expenseService.deleteExpense(expenseId, UUID.fromString(authentication.getName()));
        return ResponseEntity.noContent().build();
    }
}
