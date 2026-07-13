package pl.settly.settly_api.debts.controller;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.settly.settly_api.debts.dto.DebtResponse;
import pl.settly.settly_api.debts.dto.FriendBalanceResponse;
import pl.settly.settly_api.debts.dto.SettleUpRequest;
import pl.settly.settly_api.debts.service.DebtService;

@RestController
public class DebtController {

  private final DebtService debtService;

  public DebtController(DebtService debtService) {
    this.debtService = debtService;
  }

  /** Net balances between the current user and counterparties, optionally scoped to a project. */
  @GetMapping("/balances")
  public ResponseEntity<List<FriendBalanceResponse>> getBalances(
      @RequestParam(required = false) UUID projectId, Authentication authentication) {
    return ResponseEntity.ok(
        debtService.getBalances(UUID.fromString(authentication.getName()), projectId));
  }

  /** Settle up everything a debtor owes the current user; only the creditor may call this. */
  @PostMapping("/debts/settle")
  public ResponseEntity<DebtResponse> settleUp(
      @Valid @RequestBody SettleUpRequest request, Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(debtService.settleUp(UUID.fromString(authentication.getName()), request));
  }

  /** The current user's settlement history (as debtor or creditor). */
  @GetMapping("/debts")
  public ResponseEntity<List<DebtResponse>> getSettlementHistory(Authentication authentication) {
    return ResponseEntity.ok(
        debtService.getSettlementHistory(UUID.fromString(authentication.getName())));
  }

  /**
   * Reverse a settle-up: unsettles every split it covered and removes the payment record. This is
   * the supported way to undo a bulk settlement — individual splits it covered cannot be unsettled
   * on their own.
   */
  @DeleteMapping("/debts/{debtId}")
  public ResponseEntity<Void> undoSettleUp(
      @PathVariable UUID debtId, Authentication authentication) {
    debtService.undoSettleUp(debtId, UUID.fromString(authentication.getName()));
    return ResponseEntity.noContent().build();
  }
}
