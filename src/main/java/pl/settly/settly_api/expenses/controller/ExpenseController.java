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
import pl.settly.settly_api.expenses.dto.RequestExpense;
import pl.settly.settly_api.expenses.dto.RequestExpenseResponse;
import pl.settly.settly_api.expenses.service.ExpenseService;

@RestController
@RequestMapping("/expenses")
public class ExpenseController {
    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping("/create")
    public ResponseEntity<RequestExpenseResponse> createExpense(
            @Valid @RequestBody RequestExpense requestExpense, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        expenseService.createExpense(
                                requestExpense, UUID.fromString(authentication.getName())));
    }

    @GetMapping("/get/{expenseId}")
    public ResponseEntity<RequestExpenseResponse> getExpenseById(@PathVariable UUID expenseId) {
        return ResponseEntity.ok(expenseService.getExpense(expenseId));
    }

    @PutMapping("/update/{expenseId}")
    public ResponseEntity<RequestExpenseResponse> updateExpense(
            @PathVariable UUID expenseId, @Valid @RequestBody RequestExpense request) {
        return ResponseEntity.ok(expenseService.updateExpense(expenseId, request));
    }

    @DeleteMapping("/delete/{expenseId}")
    public ResponseEntity<Void> deleteExpense(@PathVariable UUID expenseId) {
        expenseService.deleteExpense(expenseId);
        return ResponseEntity.noContent().build();
    }
}
