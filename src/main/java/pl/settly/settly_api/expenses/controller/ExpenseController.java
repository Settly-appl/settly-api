package pl.settly.settly_api.expenses.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.settly.settly_api.expenses.dto.CreateExpenseItemRequest;
import pl.settly.settly_api.expenses.dto.CreateExpenseRequest;
import pl.settly.settly_api.expenses.dto.ExpenseItemResponse;
import pl.settly.settly_api.expenses.dto.ExpenseItemSplitUserResponse;
import pl.settly.settly_api.expenses.dto.ExpenseResponse;
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
      @Valid @RequestBody CreateExpenseRequest requestExpense, Authentication authentication) {
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

  @GetMapping
  public ResponseEntity<Page<ExpenseResponse>> getAllExpenses(
      @PageableDefault(sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable,
      @RequestParam(required = false) String category,
      Authentication authentication) {

    UUID userId = UUID.fromString(authentication.getName());

    return ResponseEntity.ok(expenseService.searchExpenses(pageable, category, userId));
  }

  @PutMapping("/{expenseId}")
  public ResponseEntity<ExpenseResponse> updateExpense(
      @PathVariable UUID expenseId,
      @Valid @RequestBody CreateExpenseRequest request,
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

  @PostMapping("/{expenseId}/items")
  public ResponseEntity<ExpenseItemResponse> addItem(
      @PathVariable UUID expenseId,
      @Valid @RequestBody CreateExpenseItemRequest request,
      Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            expenseService.addItem(expenseId, UUID.fromString(authentication.getName()), request));
  }

  @GetMapping("/{expenseId}/items")
  public ResponseEntity<List<ExpenseItemResponse>> getItems(
      @PathVariable UUID expenseId, Authentication authentication) {
    return ResponseEntity.ok(
        expenseService.getItems(expenseId, UUID.fromString(authentication.getName())));
  }

  @GetMapping("/items/{itemId}/users")
  public ResponseEntity<List<ExpenseItemSplitUserResponse>> getItemSplitUsers(
      @PathVariable UUID itemId, Authentication authentication) {
    return ResponseEntity.ok(
        expenseService.getItemSplitUsers(itemId, UUID.fromString(authentication.getName())));
  }

  @DeleteMapping("/{expenseId}/items/{itemId}")
  public ResponseEntity<Void> deleteItem(
      @PathVariable UUID expenseId, @PathVariable UUID itemId, Authentication authentication) {
    expenseService.deleteItem(expenseId, itemId, UUID.fromString(authentication.getName()));
    return ResponseEntity.noContent().build();
  }
}
