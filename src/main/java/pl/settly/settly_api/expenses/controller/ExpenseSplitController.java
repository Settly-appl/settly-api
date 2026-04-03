package pl.settly.settly_api.expenses.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.settly.settly_api.expenses.dto.CreateExpenseSplitRequest;
import pl.settly.settly_api.expenses.dto.ExpenseSplitResponse;
import pl.settly.settly_api.expenses.service.ExpenseSplitService;

@RestController
@RequestMapping("/expenses")
public class ExpenseSplitController {

  private final ExpenseSplitService expenseSplitService;

  public ExpenseSplitController(ExpenseSplitService expenseSplitService) {
    this.expenseSplitService = expenseSplitService;
  }

  @PostMapping("/{expenseId}/splits")
  public ResponseEntity<List<ExpenseSplitResponse>> createSplit(
      @PathVariable UUID expenseId,
      @Valid @RequestBody CreateExpenseSplitRequest createExpenseSplitRequest,
      Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            expenseSplitService.createSplit(
                expenseId, createExpenseSplitRequest, UUID.fromString(authentication.getName())));
  }

  @GetMapping("/{expenseId}/splits")
  public ResponseEntity<List<ExpenseSplitResponse>> getSplitsForExpense(
      @PathVariable UUID expenseId, Authentication authentication) {
    return ResponseEntity.ok(
        expenseSplitService.getSplitsForExpense(
            expenseId, UUID.fromString(authentication.getName())));
  }

  @DeleteMapping("/{expenseId}/splits")
  public ResponseEntity<Void> deleteAllSplits(
      @PathVariable UUID expenseId, Authentication authentication) {
    expenseSplitService.deleteAllSplits(expenseId, UUID.fromString(authentication.getName()));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/splits/unsettled")
  public ResponseEntity<List<ExpenseSplitResponse>> getUnsettledSplits(
      Authentication authentication) {
    return ResponseEntity.ok(
        expenseSplitService.getUnsettledSplits(UUID.fromString(authentication.getName())));
  }
}
