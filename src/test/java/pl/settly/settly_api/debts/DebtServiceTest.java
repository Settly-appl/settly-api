package pl.settly.settly_api.debts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.common.exception.ResourceNotFoundException;
import pl.settly.settly_api.debts.dto.BalanceAggregate;
import pl.settly.settly_api.debts.dto.DebtResponse;
import pl.settly.settly_api.debts.dto.FriendBalanceResponse;
import pl.settly.settly_api.debts.dto.SettleUpRequest;
import pl.settly.settly_api.debts.model.Debt;
import pl.settly.settly_api.debts.repository.DebtRepository;
import pl.settly.settly_api.debts.service.DebtService;
import pl.settly.settly_api.expenses.model.Expense;
import pl.settly.settly_api.expenses.model.ExpenseSplit;
import pl.settly.settly_api.expenses.repository.ExpenseSplitRepository;
import pl.settly.settly_api.projects.model.Project;
import pl.settly.settly_api.projects.repository.ProjectRepository;

@ExtendWith(MockitoExtension.class)
class DebtServiceTest {

  @Mock ExpenseSplitRepository expenseSplitRepository;
  @Mock DebtRepository debtRepository;
  @Mock UserRepository userRepository;
  @Mock ProjectRepository projectRepository;

  @InjectMocks DebtService debtService;

  @Captor ArgumentCaptor<Debt> debtCaptor;
  @Captor ArgumentCaptor<List<ExpenseSplit>> splitsCaptor;

  private final UUID userId = UUID.randomUUID();
  private final UUID friendId = UUID.randomUUID();
  private final UUID friendId2 = UUID.randomUUID();

  private BalanceAggregate aggregate(UUID id, String total) {
    return new BalanceAggregate() {
      @Override
      public UUID getUserId() {
        return id;
      }

      @Override
      public BigDecimal getTotal() {
        return new BigDecimal(total);
      }
    };
  }

  private User user(UUID id, String username) {
    User u = new User();
    u.setId(id);
    u.setUsername(username);
    u.setDisplayName(username);
    return u;
  }

  // region getBalances

  @Test
  void should_compute_positive_net_when_friend_owes_user() {
    given(expenseSplitRepository.sumOwedToUser(userId, null))
        .willReturn(List.of(aggregate(friendId, "70.00")));
    given(expenseSplitRepository.sumOwedByUser(userId, null))
        .willReturn(List.of(aggregate(friendId, "20.00")));
    given(userRepository.findAllById(List.of(friendId)))
        .willReturn(List.of(user(friendId, "alice")));

    List<FriendBalanceResponse> result = debtService.getBalances(userId, null);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).userId()).isEqualTo(friendId);
    assertThat(result.get(0).netAmount()).isEqualByComparingTo("50.00");
  }

  @Test
  void should_compute_negative_net_when_user_owes_friend() {
    given(expenseSplitRepository.sumOwedToUser(userId, null)).willReturn(List.of());
    given(expenseSplitRepository.sumOwedByUser(userId, null))
        .willReturn(List.of(aggregate(friendId, "30.00")));
    given(userRepository.findAllById(List.of(friendId))).willReturn(List.of(user(friendId, "bob")));

    List<FriendBalanceResponse> result = debtService.getBalances(userId, null);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).netAmount()).isEqualByComparingTo("-30.00");
  }

  @Test
  void should_omit_zero_net_balances() {
    given(expenseSplitRepository.sumOwedToUser(userId, null))
        .willReturn(List.of(aggregate(friendId, "40.00"), aggregate(friendId2, "10.00")));
    given(expenseSplitRepository.sumOwedByUser(userId, null))
        .willReturn(List.of(aggregate(friendId, "40.00")));
    given(userRepository.findAllById(List.of(friendId2)))
        .willReturn(List.of(user(friendId2, "carol")));

    List<FriendBalanceResponse> result = debtService.getBalances(userId, null);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).userId()).isEqualTo(friendId2);
    assertThat(result.get(0).netAmount()).isEqualByComparingTo("10.00");
  }

  @Test
  void should_return_empty_when_no_balances() {
    given(expenseSplitRepository.sumOwedToUser(userId, null)).willReturn(List.of());
    given(expenseSplitRepository.sumOwedByUser(userId, null)).willReturn(List.of());

    assertThat(debtService.getBalances(userId, null)).isEmpty();
  }

  @Test
  void should_scope_balances_to_project() {
    UUID projectId = UUID.randomUUID();
    given(expenseSplitRepository.sumOwedToUser(userId, projectId))
        .willReturn(List.of(aggregate(friendId, "15.00")));
    given(expenseSplitRepository.sumOwedByUser(userId, projectId)).willReturn(List.of());
    given(userRepository.findAllById(List.of(friendId)))
        .willReturn(List.of(user(friendId, "dave")));

    List<FriendBalanceResponse> result = debtService.getBalances(userId, projectId);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).netAmount()).isEqualByComparingTo("15.00");
  }

  // endregion

  // region settleUp

  @Test
  void should_settle_up_and_record_debt() {
    Expense expense = Expense.builder().build();
    ExpenseSplit split1 =
        ExpenseSplit.builder()
            .expense(expense)
            .amount(BigDecimal.valueOf(30))
            .settled(false)
            .build();
    ExpenseSplit split2 =
        ExpenseSplit.builder()
            .expense(expense)
            .amount(BigDecimal.valueOf(20))
            .settled(false)
            .build();

    given(expenseSplitRepository.findUnsettledBetween(userId, friendId, null))
        .willReturn(List.of(split1, split2));
    given(userRepository.getReferenceById(friendId)).willReturn(user(friendId, "alice"));
    given(userRepository.getReferenceById(userId)).willReturn(user(userId, "me"));
    given(debtRepository.save(any(Debt.class))).willAnswer(inv -> inv.getArgument(0));

    DebtResponse result = debtService.settleUp(userId, new SettleUpRequest(friendId, null));

    assertThat(split1.getSettled()).isTrue();
    assertThat(split2.getSettled()).isTrue();
    assertThat(split1.getSettledAt()).isNotNull();
    verify(expenseSplitRepository).saveAll(splitsCaptor.capture());
    assertThat(splitsCaptor.getValue()).hasSize(2);

    verify(debtRepository).save(debtCaptor.capture());
    Debt saved = debtCaptor.getValue();
    assertThat(saved.getAmount()).isEqualByComparingTo("50");
    assertThat(saved.getFromUser().getId()).isEqualTo(friendId);
    assertThat(saved.getToUser().getId()).isEqualTo(userId);
    assertThat(saved.getSettled()).isTrue();
    assertThat(saved.getProject()).isNull();

    assertThat(result.amount()).isEqualByComparingTo("50");
    assertThat(result.fromUserId()).isEqualTo(friendId);
    assertThat(result.toUserId()).isEqualTo(userId);

    // Each cleared split remembers the settlement that paid it, so it cannot later
    // be unsettled on its own and contradict this payment record.
    assertThat(split1.getSettledByDebt()).isSameAs(saved);
    assertThat(split2.getSettledByDebt()).isSameAs(saved);
  }

  // region undoSettleUp

  @Test
  void should_undo_settle_up_by_unsettling_its_splits_and_deleting_the_record() {
    UUID debtId = UUID.randomUUID();
    Debt debt =
        Debt.builder()
            .id(debtId)
            .fromUser(user(friendId, "alice"))
            .toUser(user(userId, "me"))
            .amount(BigDecimal.valueOf(50))
            .settled(true)
            .settledAt(Instant.now())
            .build();

    ExpenseSplit covered =
        ExpenseSplit.builder()
            .expense(Expense.builder().build())
            .amount(BigDecimal.valueOf(50))
            .settled(true)
            .settledAt(Instant.now())
            .settledByDebt(debt)
            .build();

    given(debtRepository.findById(debtId)).willReturn(Optional.of(debt));
    given(expenseSplitRepository.findBySettledByDebtId(debtId)).willReturn(List.of(covered));

    debtService.undoSettleUp(debtId, userId);

    assertThat(covered.getSettled()).isFalse();
    assertThat(covered.getSettledAt()).isNull();
    assertThat(covered.getSettledByDebt()).isNull();
    verify(expenseSplitRepository).saveAll(anyList());
    verify(debtRepository).delete(debt);
  }

  @Test
  void should_throw_when_undoing_a_settlement_you_are_not_part_of() {
    UUID debtId = UUID.randomUUID();
    Debt debt =
        Debt.builder()
            .id(debtId)
            .fromUser(user(friendId, "alice"))
            .toUser(user(userId, "me"))
            .amount(BigDecimal.valueOf(50))
            .build();

    given(debtRepository.findById(debtId)).willReturn(Optional.of(debt));

    assertThatThrownBy(() -> debtService.undoSettleUp(debtId, UUID.randomUUID()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  // endregion

  @Test
  void should_settle_up_scoped_to_project() {
    UUID projectId = UUID.randomUUID();
    Expense expense = Expense.builder().build();
    ExpenseSplit split =
        ExpenseSplit.builder()
            .expense(expense)
            .amount(BigDecimal.valueOf(25))
            .settled(false)
            .build();
    Project project = Project.builder().id(projectId).build();

    given(expenseSplitRepository.findUnsettledBetween(userId, friendId, projectId))
        .willReturn(List.of(split));
    given(userRepository.getReferenceById(friendId)).willReturn(user(friendId, "alice"));
    given(userRepository.getReferenceById(userId)).willReturn(user(userId, "me"));
    given(projectRepository.getReferenceById(projectId)).willReturn(project);
    given(debtRepository.save(any(Debt.class))).willAnswer(inv -> inv.getArgument(0));

    DebtResponse result = debtService.settleUp(userId, new SettleUpRequest(friendId, projectId));

    verify(debtRepository).save(debtCaptor.capture());
    assertThat(debtCaptor.getValue().getProject().getId()).isEqualTo(projectId);
    assertThat(result.projectId()).isEqualTo(projectId);
  }

  @Test
  void should_throw_when_settling_with_self() {
    assertThatThrownBy(() -> debtService.settleUp(userId, new SettleUpRequest(userId, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot settle up with yourself");

    verify(expenseSplitRepository, never()).findUnsettledBetween(any(), any(), any());
  }

  @Test
  void should_throw_when_nothing_to_settle() {
    given(expenseSplitRepository.findUnsettledBetween(userId, friendId, null))
        .willReturn(List.of());

    assertThatThrownBy(() -> debtService.settleUp(userId, new SettleUpRequest(friendId, null)))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("No outstanding balance to settle");

    verify(debtRepository, never()).save(any());
    verify(expenseSplitRepository, never()).saveAll(anyList());
  }

  @Test
  void should_settle_net_and_clear_both_directions() {
    // Friend owes user 30; user owes friend 5 -> net 25 in user's favour.
    ExpenseSplit friendOwesUser =
        ExpenseSplit.builder()
            .expense(Expense.builder().build())
            .amount(BigDecimal.valueOf(30))
            .settled(false)
            .build();
    ExpenseSplit userOwesFriend =
        ExpenseSplit.builder()
            .expense(Expense.builder().build())
            .amount(BigDecimal.valueOf(5))
            .settled(false)
            .build();

    given(expenseSplitRepository.findUnsettledBetween(userId, friendId, null))
        .willReturn(List.of(friendOwesUser));
    given(expenseSplitRepository.findUnsettledBetween(friendId, userId, null))
        .willReturn(List.of(userOwesFriend));
    given(userRepository.getReferenceById(friendId)).willReturn(user(friendId, "alice"));
    given(userRepository.getReferenceById(userId)).willReturn(user(userId, "me"));
    given(debtRepository.save(any(Debt.class))).willAnswer(inv -> inv.getArgument(0));

    DebtResponse result = debtService.settleUp(userId, new SettleUpRequest(friendId, null));

    // Both directions are cleared so the relationship nets to zero.
    assertThat(friendOwesUser.getSettled()).isTrue();
    assertThat(userOwesFriend.getSettled()).isTrue();
    verify(expenseSplitRepository).saveAll(splitsCaptor.capture());
    assertThat(splitsCaptor.getValue()).hasSize(2);

    // The recorded debt is the net amount that changed hands.
    verify(debtRepository).save(debtCaptor.capture());
    assertThat(debtCaptor.getValue().getAmount()).isEqualByComparingTo("25");
    assertThat(debtCaptor.getValue().getFromUser().getId()).isEqualTo(friendId);
    assertThat(debtCaptor.getValue().getToUser().getId()).isEqualTo(userId);
    assertThat(result.amount()).isEqualByComparingTo("25");
  }

  @Test
  void should_throw_when_caller_is_not_the_net_creditor() {
    // Friend owes user 5; user owes friend 30 -> user is the net debtor, not creditor.
    ExpenseSplit friendOwesUser =
        ExpenseSplit.builder()
            .expense(Expense.builder().build())
            .amount(BigDecimal.valueOf(5))
            .settled(false)
            .build();
    ExpenseSplit userOwesFriend =
        ExpenseSplit.builder()
            .expense(Expense.builder().build())
            .amount(BigDecimal.valueOf(30))
            .settled(false)
            .build();

    given(expenseSplitRepository.findUnsettledBetween(userId, friendId, null))
        .willReturn(List.of(friendOwesUser));
    given(expenseSplitRepository.findUnsettledBetween(friendId, userId, null))
        .willReturn(List.of(userOwesFriend));

    assertThatThrownBy(() -> debtService.settleUp(userId, new SettleUpRequest(friendId, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Only the user who is owed the net balance can settle up");

    verify(debtRepository, never()).save(any());
    verify(expenseSplitRepository, never()).saveAll(anyList());
  }

  // endregion

  // region getSettlementHistory

  @Test
  void should_return_settlement_history() {
    Debt debt =
        Debt.builder()
            .id(UUID.randomUUID())
            .fromUser(user(friendId, "alice"))
            .toUser(user(userId, "me"))
            .amount(BigDecimal.valueOf(50))
            .settled(true)
            .build();

    given(debtRepository.findAllForUser(userId)).willReturn(List.of(debt));

    List<DebtResponse> result = debtService.getSettlementHistory(userId);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).amount()).isEqualByComparingTo("50");
    assertThat(result.get(0).settled()).isTrue();
  }

  @Test
  void should_return_empty_history() {
    given(debtRepository.findAllForUser(userId)).willReturn(List.of());

    assertThat(debtService.getSettlementHistory(userId)).isEmpty();
  }

  // endregion
}
