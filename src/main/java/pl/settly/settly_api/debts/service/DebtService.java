package pl.settly.settly_api.debts.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.common.exception.ResourceNotFoundException;
import pl.settly.settly_api.debts.dto.BalanceAggregate;
import pl.settly.settly_api.debts.dto.DebtResponse;
import pl.settly.settly_api.debts.dto.FriendBalanceResponse;
import pl.settly.settly_api.debts.dto.SettleUpRequest;
import pl.settly.settly_api.debts.model.Debt;
import pl.settly.settly_api.debts.repository.DebtRepository;
import pl.settly.settly_api.expenses.model.ExpenseSplit;
import pl.settly.settly_api.expenses.repository.ExpenseSplitRepository;
import pl.settly.settly_api.projects.repository.ProjectRepository;

@Service
public class DebtService {

  private final ExpenseSplitRepository expenseSplitRepository;
  private final DebtRepository debtRepository;
  private final UserRepository userRepository;
  private final ProjectRepository projectRepository;

  public DebtService(
      ExpenseSplitRepository expenseSplitRepository,
      DebtRepository debtRepository,
      UserRepository userRepository,
      ProjectRepository projectRepository) {
    this.expenseSplitRepository = expenseSplitRepository;
    this.debtRepository = debtRepository;
    this.userRepository = userRepository;
    this.projectRepository = projectRepository;
  }

  /**
   * Net balance between the user and every counterparty, derived live from unsettled splits. A
   * positive amount means the counterparty owes the user. Zero balances are omitted.
   */
  @Transactional(readOnly = true)
  public List<FriendBalanceResponse> getBalances(UUID userId, UUID projectId) {
    Map<UUID, BigDecimal> net = new HashMap<>();

    for (BalanceAggregate owed : expenseSplitRepository.sumOwedToUser(userId, projectId)) {
      net.merge(owed.getUserId(), owed.getTotal(), BigDecimal::add);
    }
    for (BalanceAggregate owing : expenseSplitRepository.sumOwedByUser(userId, projectId)) {
      net.merge(owing.getUserId(), owing.getTotal().negate(), BigDecimal::add);
    }

    List<UUID> counterpartyIds =
        net.entrySet().stream()
            .filter(e -> e.getValue().compareTo(BigDecimal.ZERO) != 0)
            .map(Map.Entry::getKey)
            .toList();

    if (counterpartyIds.isEmpty()) {
      return List.of();
    }

    Map<UUID, User> users =
        userRepository.findAllById(counterpartyIds).stream()
            .collect(Collectors.toMap(User::getId, u -> u));

    List<FriendBalanceResponse> balances = new ArrayList<>();
    for (UUID counterpartyId : counterpartyIds) {
      User user = users.get(counterpartyId);
      if (user == null) {
        continue;
      }
      balances.add(
          new FriendBalanceResponse(
              user.getId(),
              user.getDisplayName(),
              user.getUsername(),
              user.getAvatarUrl(),
              net.get(counterpartyId)));
    }
    return balances;
  }

  /**
   * Settle up the whole net balance between the current user (the creditor) and a debtor. Clears
   * the unsettled splits in BOTH directions (what the debtor owes the creditor and what the
   * creditor owes the debtor) so the relationship nets to zero, and records the net amount that
   * changed hands as a {@link Debt}. Only the net creditor may call this.
   */
  @Transactional
  public DebtResponse settleUp(UUID creditorId, SettleUpRequest request) {
    UUID debtorId = request.debtorUserId();
    if (debtorId.equals(creditorId)) {
      throw new IllegalArgumentException("Cannot settle up with yourself");
    }

    UUID projectId = request.projectId();
    // What the debtor owes the creditor (creditor's expenses, debtor's splits)...
    List<ExpenseSplit> debtorOwes =
        expenseSplitRepository.findUnsettledBetween(creditorId, debtorId, projectId);
    // ...and what the creditor owes the debtor (the opposite direction).
    List<ExpenseSplit> creditorOwes =
        expenseSplitRepository.findUnsettledBetween(debtorId, creditorId, projectId);

    if (debtorOwes.isEmpty() && creditorOwes.isEmpty()) {
      throw new ResourceNotFoundException("No outstanding balance to settle");
    }

    BigDecimal owedToCreditor = sumAmounts(debtorOwes);
    BigDecimal owedToDebtor = sumAmounts(creditorOwes);
    BigDecimal net = owedToCreditor.subtract(owedToDebtor);

    if (net.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Only the user who is owed the net balance can settle up");
    }

    Instant now = Instant.now();

    // Persist the payment record first so the splits can point at it.
    Debt debt =
        debtRepository.save(
            Debt.builder()
                .fromUser(userRepository.getReferenceById(debtorId))
                .toUser(userRepository.getReferenceById(creditorId))
                .project(projectId == null ? null : projectRepository.getReferenceById(projectId))
                .amount(net)
                .settled(true)
                .settledAt(now)
                .build());

    List<ExpenseSplit> toSettle = new ArrayList<>(debtorOwes);
    toSettle.addAll(creditorOwes);
    for (ExpenseSplit split : toSettle) {
      split.setSettled(true);
      split.setSettledAt(now);
      // Stamp the settlement that covered this split. Unsettling it individually
      // afterwards would claim the money is owed again even though it was paid,
      // so that is refused — the whole settle-up has to be undone instead.
      split.setSettledByDebt(debt);
    }
    expenseSplitRepository.saveAll(toSettle);

    return toResponse(debt);
  }

  /**
   * Reverses a settle-up: unsettles every split it covered and deletes the payment record, so the
   * balance and the audit trail stay in agreement. Only the two people involved may undo it.
   */
  @Transactional
  public void undoSettleUp(UUID debtId, UUID userId) {
    Debt debt =
        debtRepository
            .findById(debtId)
            .orElseThrow(() -> new ResourceNotFoundException("Settlement does not exist"));

    boolean involved =
        debt.getFromUser().getId().equals(userId) || debt.getToUser().getId().equals(userId);
    if (!involved) {
      throw new ResourceNotFoundException("Settlement does not exist");
    }

    List<ExpenseSplit> covered = expenseSplitRepository.findBySettledByDebtId(debtId);
    for (ExpenseSplit split : covered) {
      split.setSettled(false);
      split.setSettledAt(null);
      split.setSettledByDebt(null);
    }
    expenseSplitRepository.saveAll(covered);

    debtRepository.delete(debt);
  }

  private static BigDecimal sumAmounts(List<ExpenseSplit> splits) {
    return splits.stream().map(ExpenseSplit::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  @Transactional(readOnly = true)
  public List<DebtResponse> getSettlementHistory(UUID userId) {
    return debtRepository.findAllForUser(userId).stream().map(this::toResponse).toList();
  }

  private DebtResponse toResponse(Debt debt) {
    return new DebtResponse(
        debt.getId(),
        debt.getProject() == null ? null : debt.getProject().getId(),
        debt.getFromUser().getId(),
        debt.getToUser().getId(),
        debt.getAmount(),
        Boolean.TRUE.equals(debt.getSettled()),
        debt.getSettledAt(),
        debt.getCreatedAt());
  }
}
