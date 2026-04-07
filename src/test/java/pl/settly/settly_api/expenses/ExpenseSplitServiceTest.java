package pl.settly.settly_api.expenses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
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
import pl.settly.settly_api.expenses.dto.CreateExpenseSplitRequest;
import pl.settly.settly_api.expenses.dto.ExpenseMapper;
import pl.settly.settly_api.expenses.dto.ExpenseSplitResponse;
import pl.settly.settly_api.expenses.dto.ItemSplitAssignment;
import pl.settly.settly_api.expenses.dto.SplitParticipant;
import pl.settly.settly_api.expenses.model.Expense;
import pl.settly.settly_api.expenses.model.ExpenseItem;
import pl.settly.settly_api.expenses.model.ExpenseItemSplit;
import pl.settly.settly_api.expenses.model.ExpenseSplit;
import pl.settly.settly_api.expenses.model.ExpenseSplitType;
import pl.settly.settly_api.expenses.repository.ExpenseItemRepository;
import pl.settly.settly_api.expenses.repository.ExpenseItemSplitRepository;
import pl.settly.settly_api.expenses.repository.ExpenseRepository;
import pl.settly.settly_api.expenses.repository.ExpenseSplitRepository;
import pl.settly.settly_api.expenses.service.ExpenseSplitService;
import pl.settly.settly_api.friendships.service.FriendshipService;

@ExtendWith(MockitoExtension.class)
class ExpenseSplitServiceTest {

  @Mock FriendshipService friendshipService;
  @Mock ExpenseSplitRepository expenseSplitRepository;
  @Mock ExpenseRepository expenseRepository;
  @Mock ExpenseItemRepository expenseItemRepository;
  @Mock ExpenseItemSplitRepository expenseItemSplitRepository;
  @Mock UserRepository userRepository;
  @Mock ExpenseMapper expenseMapper;

  @InjectMocks ExpenseSplitService expenseSplitService;

  @Captor ArgumentCaptor<List<ExpenseSplit>> splitsCaptor;
  @Captor ArgumentCaptor<List<ExpenseItemSplit>> itemSplitsCaptor;

  private final UUID userId = UUID.randomUUID();
  private final UUID friendId = UUID.randomUUID();
  private final UUID friendId2 = UUID.randomUUID();
  private final UUID expenseId = UUID.randomUUID();

  private Expense createExpense(BigDecimal totalAmount) {
    User owner = new User();
    owner.setId(userId);
    return Expense.builder().id(expenseId).user(owner).totalAmount(totalAmount).build();
  }

  private User createFriendUser(UUID id) {
    User user = new User();
    user.setId(id);
    return user;
  }

  private ExpenseSplitResponse dummyResponse() {
    return new ExpenseSplitResponse(
        UUID.randomUUID(), expenseId, userId, ExpenseSplitType.EQUAL, BigDecimal.TEN, false, null);
  }

  // region createSplit - common validations

  @Test
  void should_throw_when_expense_not_found() {
    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.empty());

    CreateExpenseSplitRequest request =
        new CreateExpenseSplitRequest(
            ExpenseSplitType.EQUAL, List.of(new SplitParticipant(friendId, null)), null);

    assertThatThrownBy(() -> expenseSplitService.createSplit(expenseId, request, userId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Expense does not exist");
  }

  @Test
  void should_throw_when_expense_already_split() {
    Expense expense = createExpense(BigDecimal.valueOf(100));
    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.existsByExpenseId(expenseId)).willReturn(true);

    CreateExpenseSplitRequest request =
        new CreateExpenseSplitRequest(
            ExpenseSplitType.EQUAL, List.of(new SplitParticipant(friendId, null)), null);

    assertThatThrownBy(() -> expenseSplitService.createSplit(expenseId, request, userId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expense is already split");
  }

  @Test
  void should_throw_when_participant_is_not_friend() {
    Expense expense = createExpense(BigDecimal.valueOf(100));
    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.existsByExpenseId(expenseId)).willReturn(false);
    given(friendshipService.areFriends(userId, friendId)).willReturn(false);

    CreateExpenseSplitRequest request =
        new CreateExpenseSplitRequest(
            ExpenseSplitType.EQUAL, List.of(new SplitParticipant(friendId, null)), null);

    assertThatThrownBy(() -> expenseSplitService.createSplit(expenseId, request, userId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("is not requesting user's friend");
  }

  // endregion

  // region createSplit - EQUAL

  @Test
  void should_create_equal_split_successfully() {
    Expense expense = createExpense(BigDecimal.valueOf(100));
    User friend = createFriendUser(friendId);

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.existsByExpenseId(expenseId)).willReturn(false);
    given(friendshipService.areFriends(userId, friendId)).willReturn(true);
    given(userRepository.getReferenceById(friendId)).willReturn(friend);
    given(expenseSplitRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
    given(expenseMapper.toExpenseSplitResponse(any())).willReturn(dummyResponse());

    CreateExpenseSplitRequest request =
        new CreateExpenseSplitRequest(
            ExpenseSplitType.EQUAL, List.of(new SplitParticipant(friendId, null)), null);

    List<ExpenseSplitResponse> result = expenseSplitService.createSplit(expenseId, request, userId);

    assertThat(result).hasSize(2);
    verify(expenseSplitRepository).saveAll(splitsCaptor.capture());
    List<ExpenseSplit> saved = splitsCaptor.getValue();
    assertThat(saved).hasSize(2);

    ExpenseSplit ownerSplit =
        saved.stream().filter(ExpenseSplit::getSettled).findFirst().orElseThrow();
    ExpenseSplit friendSplit =
        saved.stream().filter(s -> !s.getSettled()).findFirst().orElseThrow();

    assertThat(ownerSplit.getAmount()).isEqualByComparingTo("50.00");
    assertThat(friendSplit.getAmount()).isEqualByComparingTo("50.00");
    assertThat(ownerSplit.getExpenseSplitType()).isEqualTo(ExpenseSplitType.EQUAL);
  }

  @Test
  void should_handle_equal_split_remainder() {
    Expense expense = createExpense(BigDecimal.valueOf(10));
    User friend1 = createFriendUser(friendId);
    User friend2 = createFriendUser(friendId2);

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.existsByExpenseId(expenseId)).willReturn(false);
    given(friendshipService.areFriends(userId, friendId)).willReturn(true);
    given(friendshipService.areFriends(userId, friendId2)).willReturn(true);
    given(userRepository.getReferenceById(friendId)).willReturn(friend1);
    given(userRepository.getReferenceById(friendId2)).willReturn(friend2);
    given(expenseSplitRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
    given(expenseMapper.toExpenseSplitResponse(any())).willReturn(dummyResponse());

    CreateExpenseSplitRequest request =
        new CreateExpenseSplitRequest(
            ExpenseSplitType.EQUAL,
            List.of(new SplitParticipant(friendId, null), new SplitParticipant(friendId2, null)),
            null);

    expenseSplitService.createSplit(expenseId, request, userId);

    verify(expenseSplitRepository).saveAll(splitsCaptor.capture());
    List<ExpenseSplit> saved = splitsCaptor.getValue();

    ExpenseSplit ownerSplit =
        saved.stream().filter(ExpenseSplit::getSettled).findFirst().orElseThrow();
    BigDecimal totalSaved =
        saved.stream().map(ExpenseSplit::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

    assertThat(ownerSplit.getAmount()).isEqualByComparingTo("3.34");
    assertThat(totalSaved).isEqualByComparingTo("10.00");
  }

  @Test
  void should_throw_when_total_amount_null_for_equal_split() {
    Expense expense = createExpense(null);

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.existsByExpenseId(expenseId)).willReturn(false);
    given(friendshipService.areFriends(userId, friendId)).willReturn(true);

    CreateExpenseSplitRequest request =
        new CreateExpenseSplitRequest(
            ExpenseSplitType.EQUAL, List.of(new SplitParticipant(friendId, null)), null);

    assertThatThrownBy(() -> expenseSplitService.createSplit(expenseId, request, userId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Total amount is required for EQUAL split");
  }

  // endregion

  // region createSplit - CUSTOM

  @Test
  void should_create_custom_split_successfully() {
    Expense expense = createExpense(BigDecimal.valueOf(100));
    User friend = createFriendUser(friendId);

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.existsByExpenseId(expenseId)).willReturn(false);
    given(friendshipService.areFriends(userId, friendId)).willReturn(true);
    given(userRepository.getReferenceById(friendId)).willReturn(friend);
    given(expenseSplitRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
    given(expenseMapper.toExpenseSplitResponse(any())).willReturn(dummyResponse());

    CreateExpenseSplitRequest request =
        new CreateExpenseSplitRequest(
            ExpenseSplitType.CUSTOM,
            List.of(new SplitParticipant(friendId, BigDecimal.valueOf(30))),
            null);

    List<ExpenseSplitResponse> result = expenseSplitService.createSplit(expenseId, request, userId);

    assertThat(result).hasSize(2);
    verify(expenseSplitRepository).saveAll(splitsCaptor.capture());
    List<ExpenseSplit> saved = splitsCaptor.getValue();

    ExpenseSplit ownerSplit =
        saved.stream().filter(ExpenseSplit::getSettled).findFirst().orElseThrow();
    ExpenseSplit friendSplit =
        saved.stream().filter(s -> !s.getSettled()).findFirst().orElseThrow();

    assertThat(ownerSplit.getAmount()).isEqualByComparingTo("70.00");
    assertThat(friendSplit.getAmount()).isEqualByComparingTo("30.00");
    assertThat(ownerSplit.getExpenseSplitType()).isEqualTo(ExpenseSplitType.CUSTOM);
  }

  @Test
  void should_throw_when_custom_amount_is_null() {
    Expense expense = createExpense(BigDecimal.valueOf(100));

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.existsByExpenseId(expenseId)).willReturn(false);
    given(friendshipService.areFriends(userId, friendId)).willReturn(true);

    CreateExpenseSplitRequest request =
        new CreateExpenseSplitRequest(
            ExpenseSplitType.CUSTOM, List.of(new SplitParticipant(friendId, null)), null);

    assertThatThrownBy(() -> expenseSplitService.createSplit(expenseId, request, userId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Amount is required for each participant in a custom split");
  }

  @Test
  void should_throw_when_custom_amounts_exceed_total() {
    Expense expense = createExpense(BigDecimal.valueOf(100));

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.existsByExpenseId(expenseId)).willReturn(false);
    given(friendshipService.areFriends(userId, friendId)).willReturn(true);

    CreateExpenseSplitRequest request =
        new CreateExpenseSplitRequest(
            ExpenseSplitType.CUSTOM,
            List.of(new SplitParticipant(friendId, BigDecimal.valueOf(100))),
            null);

    assertThatThrownBy(() -> expenseSplitService.createSplit(expenseId, request, userId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Participants' amounts must be less than the total expense amount");
  }

  @Test
  void should_throw_when_total_amount_null_for_custom_split() {
    Expense expense = createExpense(null);

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.existsByExpenseId(expenseId)).willReturn(false);
    given(friendshipService.areFriends(userId, friendId)).willReturn(true);

    CreateExpenseSplitRequest request =
        new CreateExpenseSplitRequest(
            ExpenseSplitType.CUSTOM,
            List.of(new SplitParticipant(friendId, BigDecimal.valueOf(30))),
            null);

    assertThatThrownBy(() -> expenseSplitService.createSplit(expenseId, request, userId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Total amount is required for CUSTOM split");
  }

  // endregion

  // region createSplit - BY_ITEM

  @Test
  void should_create_by_item_split_successfully() {
    Expense expense = createExpense(null);
    UUID itemId1 = UUID.randomUUID();
    UUID itemId2 = UUID.randomUUID();

    ExpenseItem item1 =
        ExpenseItem.builder()
            .id(itemId1)
            .expense(expense)
            .name("Milk")
            .price(BigDecimal.valueOf(6))
            .quantity(BigDecimal.ONE)
            .build();
    ExpenseItem item2 =
        ExpenseItem.builder()
            .id(itemId2)
            .expense(expense)
            .name("Bread")
            .price(BigDecimal.valueOf(3))
            .quantity(BigDecimal.ONE)
            .build();

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.existsByExpenseId(expenseId)).willReturn(false);
    given(friendshipService.areFriends(userId, friendId)).willReturn(true);
    given(expenseItemRepository.findByExpenseId(expenseId)).willReturn(List.of(item1, item2));
    given(userRepository.getReferenceById(any()))
        .willAnswer(
            inv -> {
              User u = new User();
              u.setId(inv.getArgument(0));
              return u;
            });
    given(expenseSplitRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
    given(expenseItemSplitRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
    given(expenseMapper.toExpenseSplitResponse(any())).willReturn(dummyResponse());

    CreateExpenseSplitRequest request =
        new CreateExpenseSplitRequest(
            ExpenseSplitType.BY_ITEM,
            List.of(new SplitParticipant(friendId, null)),
            List.of(
                new ItemSplitAssignment(itemId1, List.of(userId, friendId)),
                new ItemSplitAssignment(itemId2, List.of(userId))));

    List<ExpenseSplitResponse> result = expenseSplitService.createSplit(expenseId, request, userId);

    assertThat(result).hasSize(2);

    verify(expenseSplitRepository).saveAll(splitsCaptor.capture());
    verify(expenseItemSplitRepository).saveAll(itemSplitsCaptor.capture());

    List<ExpenseSplit> savedSplits = splitsCaptor.getValue();
    List<ExpenseItemSplit> savedItemSplits = itemSplitsCaptor.getValue();

    ExpenseSplit ownerSplit =
        savedSplits.stream().filter(ExpenseSplit::getSettled).findFirst().orElseThrow();
    ExpenseSplit friendSplit =
        savedSplits.stream().filter(s -> !s.getSettled()).findFirst().orElseThrow();

    // Owner: milk/2 + bread = 3 + 3 = 6
    assertThat(ownerSplit.getAmount()).isEqualByComparingTo("6.00");
    // Friend: milk/2 = 3
    assertThat(friendSplit.getAmount()).isEqualByComparingTo("3.00");
    // 3 item split rows: 2 for milk (owner + friend), 1 for bread (owner)
    assertThat(savedItemSplits).hasSize(3);
  }

  @Test
  void should_create_owner_split_even_when_no_items_assigned_to_owner() {
    Expense expense = createExpense(null);
    UUID itemId = UUID.randomUUID();

    ExpenseItem item =
        ExpenseItem.builder()
            .id(itemId)
            .expense(expense)
            .name("Milk")
            .price(BigDecimal.valueOf(6))
            .quantity(BigDecimal.ONE)
            .build();

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.existsByExpenseId(expenseId)).willReturn(false);
    given(friendshipService.areFriends(userId, friendId)).willReturn(true);
    given(expenseItemRepository.findByExpenseId(expenseId)).willReturn(List.of(item));
    given(userRepository.getReferenceById(any()))
        .willAnswer(
            inv -> {
              User u = new User();
              u.setId(inv.getArgument(0));
              return u;
            });
    given(expenseSplitRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
    given(expenseItemSplitRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
    given(expenseMapper.toExpenseSplitResponse(any())).willReturn(dummyResponse());

    CreateExpenseSplitRequest request =
        new CreateExpenseSplitRequest(
            ExpenseSplitType.BY_ITEM,
            List.of(new SplitParticipant(friendId, null)),
            List.of(new ItemSplitAssignment(itemId, List.of(friendId))));

    expenseSplitService.createSplit(expenseId, request, userId);

    verify(expenseSplitRepository).saveAll(splitsCaptor.capture());
    List<ExpenseSplit> saved = splitsCaptor.getValue();

    ExpenseSplit ownerSplit = saved.stream().filter(s -> s.getSettled()).findFirst().orElseThrow();
    assertThat(ownerSplit.getAmount()).isEqualByComparingTo("0");
  }

  @Test
  void should_throw_when_item_assignments_missing_for_by_item() {
    Expense expense = createExpense(null);

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.existsByExpenseId(expenseId)).willReturn(false);
    given(friendshipService.areFriends(userId, friendId)).willReturn(true);

    CreateExpenseSplitRequest request =
        new CreateExpenseSplitRequest(
            ExpenseSplitType.BY_ITEM, List.of(new SplitParticipant(friendId, null)), null);

    assertThatThrownBy(() -> expenseSplitService.createSplit(expenseId, request, userId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Item assignments are required for BY_ITEM split");
  }

  @Test
  void should_throw_when_expense_has_no_items() {
    Expense expense = createExpense(null);

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.existsByExpenseId(expenseId)).willReturn(false);
    given(friendshipService.areFriends(userId, friendId)).willReturn(true);
    given(expenseItemRepository.findByExpenseId(expenseId)).willReturn(List.of());

    CreateExpenseSplitRequest request =
        new CreateExpenseSplitRequest(
            ExpenseSplitType.BY_ITEM,
            List.of(new SplitParticipant(friendId, null)),
            List.of(new ItemSplitAssignment(UUID.randomUUID(), List.of(friendId))));

    assertThatThrownBy(() -> expenseSplitService.createSplit(expenseId, request, userId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expense has no items to split");
  }

  @Test
  void should_throw_when_not_all_items_assigned() {
    Expense expense = createExpense(null);
    UUID itemId1 = UUID.randomUUID();
    UUID itemId2 = UUID.randomUUID();

    ExpenseItem item1 =
        ExpenseItem.builder()
            .id(itemId1)
            .expense(expense)
            .name("Milk")
            .price(BigDecimal.valueOf(6))
            .quantity(BigDecimal.ONE)
            .build();
    ExpenseItem item2 =
        ExpenseItem.builder()
            .id(itemId2)
            .expense(expense)
            .name("Bread")
            .price(BigDecimal.valueOf(3))
            .quantity(BigDecimal.ONE)
            .build();

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.existsByExpenseId(expenseId)).willReturn(false);
    given(friendshipService.areFriends(userId, friendId)).willReturn(true);
    given(expenseItemRepository.findByExpenseId(expenseId)).willReturn(List.of(item1, item2));

    CreateExpenseSplitRequest request =
        new CreateExpenseSplitRequest(
            ExpenseSplitType.BY_ITEM,
            List.of(new SplitParticipant(friendId, null)),
            List.of(new ItemSplitAssignment(itemId1, List.of(friendId))));

    assertThatThrownBy(() -> expenseSplitService.createSplit(expenseId, request, userId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("All expense items must be assigned");
  }

  @Test
  void should_throw_when_assigned_user_is_not_participant() {
    Expense expense = createExpense(null);
    UUID itemId = UUID.randomUUID();
    UUID strangeUserId = UUID.randomUUID();

    ExpenseItem item =
        ExpenseItem.builder()
            .id(itemId)
            .expense(expense)
            .name("Milk")
            .price(BigDecimal.valueOf(6))
            .quantity(BigDecimal.ONE)
            .build();

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.existsByExpenseId(expenseId)).willReturn(false);
    given(friendshipService.areFriends(userId, friendId)).willReturn(true);
    given(expenseItemRepository.findByExpenseId(expenseId)).willReturn(List.of(item));

    CreateExpenseSplitRequest request =
        new CreateExpenseSplitRequest(
            ExpenseSplitType.BY_ITEM,
            List.of(new SplitParticipant(friendId, null)),
            List.of(new ItemSplitAssignment(itemId, List.of(strangeUserId))));

    assertThatThrownBy(() -> expenseSplitService.createSplit(expenseId, request, userId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("is not a participant in this split");
  }

  // endregion

  // region getSplitsForExpense

  @Test
  void should_return_splits_for_expense_owner() {
    Expense expense = createExpense(BigDecimal.valueOf(100));
    ExpenseSplit split = ExpenseSplit.builder().expense(expense).build();

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.findByExpenseId(expenseId)).willReturn(List.of(split));
    given(expenseMapper.toExpenseSplitResponse(split)).willReturn(dummyResponse());

    List<ExpenseSplitResponse> result = expenseSplitService.getSplitsForExpense(expenseId, userId);

    assertThat(result).hasSize(1);
  }

  @Test
  void should_return_splits_for_participant() {
    given(expenseRepository.findByIdAndUser_Id(expenseId, friendId)).willReturn(Optional.empty());
    given(expenseSplitRepository.existsByExpenseIdAndUserId(expenseId, friendId)).willReturn(true);

    ExpenseSplit split = ExpenseSplit.builder().build();
    given(expenseSplitRepository.findByExpenseId(expenseId)).willReturn(List.of(split));
    given(expenseMapper.toExpenseSplitResponse(split)).willReturn(dummyResponse());

    List<ExpenseSplitResponse> result =
        expenseSplitService.getSplitsForExpense(expenseId, friendId);

    assertThat(result).hasSize(1);
  }

  @Test
  void should_throw_when_user_is_neither_owner_nor_participant() {
    UUID strangerId = UUID.randomUUID();

    given(expenseRepository.findByIdAndUser_Id(expenseId, strangerId)).willReturn(Optional.empty());
    given(expenseSplitRepository.existsByExpenseIdAndUserId(expenseId, strangerId))
        .willReturn(false);

    assertThatThrownBy(() -> expenseSplitService.getSplitsForExpense(expenseId, strangerId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Expense does not exist");
  }

  // endregion

  // region deleteAllSplits

  @Test
  void should_delete_all_splits_successfully() {
    Expense expense = createExpense(BigDecimal.valueOf(100));
    User owner = new User();
    owner.setId(userId);

    ExpenseSplit ownerSplit = ExpenseSplit.builder().user(owner).settled(true).build();
    ExpenseSplit friendSplit =
        ExpenseSplit.builder().user(createFriendUser(friendId)).settled(false).build();

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.findByExpenseId(expenseId))
        .willReturn(List.of(ownerSplit, friendSplit));
    given(expenseItemSplitRepository.findByExpenseItemExpenseId(expenseId)).willReturn(List.of());

    expenseSplitService.deleteAllSplits(expenseId, userId);

    verify(expenseSplitRepository).deleteAll(List.of(ownerSplit, friendSplit));
    verify(expenseItemSplitRepository).deleteAll(List.of());
  }

  @Test
  void should_throw_when_no_splits_found_on_delete() {
    Expense expense = createExpense(BigDecimal.valueOf(100));

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.findByExpenseId(expenseId)).willReturn(List.of());

    assertThatThrownBy(() -> expenseSplitService.deleteAllSplits(expenseId, userId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("No splits found for this expense");
  }

  @Test
  void should_throw_when_non_payer_has_settled() {
    Expense expense = createExpense(BigDecimal.valueOf(100));
    User owner = new User();
    owner.setId(userId);

    ExpenseSplit settledFriendSplit =
        ExpenseSplit.builder().user(createFriendUser(friendId)).settled(true).build();

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.findByExpenseId(expenseId))
        .willReturn(List.of(settledFriendSplit));

    assertThatThrownBy(() -> expenseSplitService.deleteAllSplits(expenseId, userId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot delete splits — some participants have already settled");
  }

  // endregion

  // region settleSplit

  @Test
  void should_settle_split_successfully() {
    Expense expense = createExpense(BigDecimal.valueOf(100));
    UUID splitId = UUID.randomUUID();
    User friend = createFriendUser(friendId);

    ExpenseSplit split =
        ExpenseSplit.builder()
            .id(splitId)
            .expense(expense)
            .user(friend)
            .settled(false)
            .amount(BigDecimal.valueOf(50))
            .build();

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.findById(splitId)).willReturn(Optional.of(split));
    given(expenseSplitRepository.save(split)).willReturn(split);
    given(expenseMapper.toExpenseSplitResponse(split)).willReturn(dummyResponse());

    ExpenseSplitResponse result = expenseSplitService.settleSplit(expenseId, splitId, userId);

    assertThat(result).isNotNull();
    assertThat(split.getSettled()).isTrue();
    assertThat(split.getSettledAt()).isNotNull();
    verify(expenseSplitRepository).save(split);
  }

  @Test
  void should_throw_when_settling_own_split() {
    Expense expense = createExpense(BigDecimal.valueOf(100));
    UUID splitId = UUID.randomUUID();
    User owner = new User();
    owner.setId(userId);

    ExpenseSplit split =
        ExpenseSplit.builder().id(splitId).expense(expense).user(owner).settled(true).build();

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.findById(splitId)).willReturn(Optional.of(split));

    assertThatThrownBy(() -> expenseSplitService.settleSplit(expenseId, splitId, userId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot settle your own split");
  }

  @Test
  void should_throw_when_split_already_settled() {
    Expense expense = createExpense(BigDecimal.valueOf(100));
    UUID splitId = UUID.randomUUID();
    User friend = createFriendUser(friendId);

    ExpenseSplit split =
        ExpenseSplit.builder()
            .id(splitId)
            .expense(expense)
            .user(friend)
            .settled(true)
            .settledAt(Instant.now())
            .build();

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.findById(splitId)).willReturn(Optional.of(split));

    assertThatThrownBy(() -> expenseSplitService.settleSplit(expenseId, splitId, userId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Split is already settled");
  }

  @Test
  void should_throw_when_split_not_found_on_settle() {
    Expense expense = createExpense(BigDecimal.valueOf(100));
    UUID splitId = UUID.randomUUID();

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.findById(splitId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> expenseSplitService.settleSplit(expenseId, splitId, userId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Split does not exist");
  }

  // endregion

  // region getUnsettledSplits

  @Test
  void should_return_unsettled_splits() {
    ExpenseSplit split = ExpenseSplit.builder().settled(false).build();

    given(expenseSplitRepository.findByUserIdAndSettledFalse(userId)).willReturn(List.of(split));
    given(expenseMapper.toExpenseSplitResponse(split)).willReturn(dummyResponse());

    List<ExpenseSplitResponse> result = expenseSplitService.getUnsettledSplits(userId);

    assertThat(result).hasSize(1);
  }

  @Test
  void should_return_empty_list_when_no_unsettled_splits() {
    given(expenseSplitRepository.findByUserIdAndSettledFalse(userId)).willReturn(List.of());

    assertThat(expenseSplitService.getUnsettledSplits(userId)).isEmpty();
  }

  // endregion
}
