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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.common.exception.ResourceNotFoundException;
import pl.settly.settly_api.common.money.CurrencyConversionService;
import pl.settly.settly_api.expenses.dto.CreateExpenseSplitRequest;
import pl.settly.settly_api.expenses.dto.ExpenseMapper;
import pl.settly.settly_api.expenses.dto.ExpenseSplitResponse;
import pl.settly.settly_api.expenses.dto.ItemShare;
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
import pl.settly.settly_api.expenses.service.ExpenseAccessService;
import pl.settly.settly_api.expenses.service.ExpenseSplitService;
import pl.settly.settly_api.friendships.service.FriendshipService;
import pl.settly.settly_api.notifications.event.ExpensePaymentDeclaredEvent;
import pl.settly.settly_api.notifications.event.ExpenseSettlementChangedEvent;

@ExtendWith(MockitoExtension.class)
class ExpenseSplitServiceTest {

  @Mock FriendshipService friendshipService;
  @Mock ExpenseSplitRepository expenseSplitRepository;
  @Mock ExpenseRepository expenseRepository;
  @Mock ExpenseItemRepository expenseItemRepository;
  @Mock ExpenseItemSplitRepository expenseItemSplitRepository;
  @Mock UserRepository userRepository;
  @Mock ExpenseMapper expenseMapper;
  @Mock ExpenseAccessService expenseAccessService;
  @Mock ApplicationEventPublisher eventPublisher;

  @Spy CurrencyConversionService currencyConversionService = new CurrencyConversionService();

  @InjectMocks ExpenseSplitService expenseSplitService;

  @Captor ArgumentCaptor<List<ExpenseSplit>> splitsCaptor;
  @Captor ArgumentCaptor<List<ExpenseItemSplit>> itemSplitsCaptor;

  private final UUID userId = UUID.randomUUID();
  private final UUID friendId = UUID.randomUUID();
  private final UUID friendId2 = UUID.randomUUID();
  private final UUID expenseId = UUID.randomUUID();

  private Expense createExpense(BigDecimal totalAmount) {
    return createExpense(totalAmount, "PLN", BigDecimal.ONE);
  }

  /** An expense in {@code currency}, converted to the owner's PLN base at {@code rateToBase}. */
  private Expense createExpense(BigDecimal totalAmount, String currency, BigDecimal rateToBase) {
    User owner = new User();
    owner.setId(userId);
    return Expense.builder()
        .id(expenseId)
        .user(owner)
        .totalAmount(totalAmount)
        .currency(currency)
        .baseCurrency("PLN")
        .rateToBase(rateToBase)
        .baseAmount(
            totalAmount == null
                ? null
                : totalAmount.multiply(rateToBase).setScale(2, java.math.RoundingMode.HALF_UP))
        .build();
  }

  private User createFriendUser(UUID id) {
    User user = new User();
    user.setId(id);
    return user;
  }

  private ExpenseSplitResponse dummyResponse() {
    return new ExpenseSplitResponse(
        UUID.randomUUID(),
        expenseId,
        userId,
        "Test User",
        "testuser",
        ExpenseSplitType.EQUAL,
        BigDecimal.TEN,
        "PLN",
        BigDecimal.TEN,
        "PLN",
        false,
        null,
        false,
        null);
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
    // Same currency as the base: the converted share is the share.
    assertThat(ownerSplit.getBaseAmount()).isEqualByComparingTo("50.00");
    assertThat(friendSplit.getBaseAmount()).isEqualByComparingTo("50.00");
  }

  @Test
  void should_value_a_foreign_split_in_the_base_currency() {
    // GBP 25.01 at 4.85 is PLN 121.30 (.2985 rounds up). Halved in pounds that is
    // 12.51 / 12.50; converted separately those are 60.67 + 60.63 = 121.30 exactly.
    Expense expense = createExpense(new BigDecimal("25.01"), "GBP", new BigDecimal("4.85"));
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

    expenseSplitService.createSplit(expenseId, request, userId);

    verify(expenseSplitRepository).saveAll(splitsCaptor.capture());
    List<ExpenseSplit> saved = splitsCaptor.getValue();

    // The shares must add up to exactly what the expense converted to, or every
    // balance built on them drifts a grosz at a time.
    BigDecimal totalBase =
        saved.stream().map(ExpenseSplit::getBaseAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(totalBase).isEqualByComparingTo(expense.getBaseAmount());
    assertThat(totalBase).isEqualByComparingTo("121.30");
    // Shares stay owed in the currency they were agreed in.
    assertThat(saved.stream().map(ExpenseSplit::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo("25.01");
  }

  @Test
  void should_refuse_a_split_between_different_base_currencies() {
    Expense expense = createExpense(BigDecimal.valueOf(100));
    User friend = createFriendUser(friendId);
    friend.setBaseCurrency("EUR");

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.existsByExpenseId(expenseId)).willReturn(false);
    given(friendshipService.areFriends(userId, friendId)).willReturn(true);
    given(userRepository.findAllById(List.of(friendId))).willReturn(List.of(friend));

    CreateExpenseSplitRequest request =
        new CreateExpenseSplitRequest(
            ExpenseSplitType.EQUAL, List.of(new SplitParticipant(friendId, null)), null);

    assertThatThrownBy(() -> expenseSplitService.createSplit(expenseId, request, userId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("base currency");
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
            List.of(new SplitParticipant(friendId, BigDecimal.valueOf(150))),
            null);

    assertThatThrownBy(() -> expenseSplitService.createSplit(expenseId, request, userId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Participants' amounts cannot exceed the total expense amount");
  }

  @Test
  void should_allow_custom_split_when_owner_owes_zero() {
    Expense expense = createExpense(BigDecimal.valueOf(100));
    User friend = createFriendUser(friendId);

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.existsByExpenseId(expenseId)).willReturn(false);
    given(friendshipService.areFriends(userId, friendId)).willReturn(true);
    given(userRepository.getReferenceById(friendId)).willReturn(friend);
    given(expenseSplitRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
    given(expenseMapper.toExpenseSplitResponse(any())).willReturn(dummyResponse());

    // Friend covers the whole total; the creator paid for them and owes 0.
    CreateExpenseSplitRequest request =
        new CreateExpenseSplitRequest(
            ExpenseSplitType.CUSTOM,
            List.of(new SplitParticipant(friendId, BigDecimal.valueOf(100))),
            null);

    List<ExpenseSplitResponse> result = expenseSplitService.createSplit(expenseId, request, userId);

    assertThat(result).hasSize(2);
    verify(expenseSplitRepository).saveAll(splitsCaptor.capture());
    List<ExpenseSplit> saved = splitsCaptor.getValue();

    ExpenseSplit ownerSplit =
        saved.stream().filter(ExpenseSplit::getSettled).findFirst().orElseThrow();
    ExpenseSplit friendSplit =
        saved.stream().filter(s -> !s.getSettled()).findFirst().orElseThrow();

    assertThat(ownerSplit.getAmount()).isEqualByComparingTo("0.00");
    assertThat(friendSplit.getAmount()).isEqualByComparingTo("100.00");
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
                new ItemSplitAssignment(itemId1, List.of(userId, friendId), null),
                new ItemSplitAssignment(itemId2, List.of(userId), null)));

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
            List.of(new ItemSplitAssignment(itemId, List.of(friendId), null)));

    expenseSplitService.createSplit(expenseId, request, userId);

    verify(expenseSplitRepository).saveAll(splitsCaptor.capture());
    List<ExpenseSplit> saved = splitsCaptor.getValue();

    ExpenseSplit ownerSplit =
        saved.stream().filter(ExpenseSplit::getSettled).findFirst().orElseThrow();
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
            List.of(new ItemSplitAssignment(UUID.randomUUID(), List.of(friendId), null)));

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
            List.of(new ItemSplitAssignment(itemId1, List.of(friendId), null)));

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
            List.of(new ItemSplitAssignment(itemId, List.of(strangeUserId), null)));

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

    given(expenseAccessService.hasNoAccessToExpense(expenseId, userId)).willReturn(false);
    given(expenseSplitRepository.findByExpenseId(expenseId)).willReturn(List.of(split));
    given(expenseMapper.toExpenseSplitResponse(split)).willReturn(dummyResponse());

    List<ExpenseSplitResponse> result = expenseSplitService.getSplitsForExpense(expenseId, userId);

    assertThat(result).hasSize(1);
  }

  @Test
  void should_return_splits_for_participant() {
    given(expenseAccessService.hasNoAccessToExpense(expenseId, friendId)).willReturn(false);

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

    given(expenseAccessService.hasNoAccessToExpense(expenseId, strangerId)).willReturn(true);

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

  /** Builds a friend's unsettled split on the owner's expense. */
  private ExpenseSplit friendSplitOn(Expense expense, UUID splitId, boolean settled) {
    return ExpenseSplit.builder()
        .id(splitId)
        .expense(expense)
        .user(createFriendUser(friendId))
        .settled(settled)
        .settledAt(settled ? Instant.now() : null)
        .amount(BigDecimal.valueOf(50))
        .build();
  }

  @Test
  void should_settle_split_successfully_as_owner() {
    Expense expense = createExpense(BigDecimal.valueOf(100));
    UUID splitId = UUID.randomUUID();
    ExpenseSplit split = friendSplitOn(expense, splitId, false);

    given(expenseRepository.findById(expenseId)).willReturn(Optional.of(expense));
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
  void should_record_a_declaration_not_a_settlement_when_the_debtor_marks_their_own_split() {
    // "I paid" from the participant is a claim for the owner to verify — the share
    // must NOT become settled until the owner confirms.
    Expense expense = createExpense(BigDecimal.valueOf(100));
    UUID splitId = UUID.randomUUID();
    ExpenseSplit split = friendSplitOn(expense, splitId, false);

    given(expenseRepository.findById(expenseId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.findById(splitId)).willReturn(Optional.of(split));
    given(expenseSplitRepository.save(split)).willReturn(split);
    given(expenseMapper.toExpenseSplitResponse(split)).willReturn(dummyResponse());

    expenseSplitService.settleSplit(expenseId, splitId, friendId);

    assertThat(split.getSettled()).isFalse();
    assertThat(split.getDeclaredPaid()).isTrue();
    assertThat(split.getDeclaredAt()).isNotNull();
  }

  @Test
  void should_let_the_debtor_retract_their_declaration() {
    Expense expense = createExpense(BigDecimal.valueOf(100));
    UUID splitId = UUID.randomUUID();
    ExpenseSplit split = friendSplitOn(expense, splitId, false);
    split.setDeclaredPaid(true);
    split.setDeclaredAt(Instant.now());

    given(expenseRepository.findById(expenseId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.findById(splitId)).willReturn(Optional.of(split));
    given(expenseSplitRepository.save(split)).willReturn(split);
    given(expenseMapper.toExpenseSplitResponse(split)).willReturn(dummyResponse());

    expenseSplitService.unsettleSplit(expenseId, splitId, friendId);

    assertThat(split.getDeclaredPaid()).isFalse();
    assertThat(split.getDeclaredAt()).isNull();
    assertThat(split.getSettled()).isFalse();
  }

  @Test
  void should_not_let_the_debtor_flip_an_owner_confirmed_settlement() {
    // Settled is the owner's word; the participant's "unsettle" only retracts a
    // declaration and must leave a confirmed share settled.
    Expense expense = createExpense(BigDecimal.valueOf(100));
    UUID splitId = UUID.randomUUID();
    ExpenseSplit split = friendSplitOn(expense, splitId, true);

    given(expenseRepository.findById(expenseId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.findById(splitId)).willReturn(Optional.of(split));
    given(expenseSplitRepository.save(split)).willReturn(split);
    given(expenseMapper.toExpenseSplitResponse(split)).willReturn(dummyResponse());

    expenseSplitService.unsettleSplit(expenseId, splitId, friendId);

    assertThat(split.getSettled()).isTrue();
    verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any());
  }

  @Test
  void should_clear_the_declaration_when_the_owner_settles_the_share() {
    Expense expense = createExpense(BigDecimal.valueOf(100));
    UUID splitId = UUID.randomUUID();
    ExpenseSplit split = friendSplitOn(expense, splitId, false);
    split.setDeclaredPaid(true);
    split.setDeclaredAt(Instant.now());

    given(expenseRepository.findById(expenseId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.findById(splitId)).willReturn(Optional.of(split));
    given(expenseSplitRepository.save(split)).willReturn(split);
    given(expenseMapper.toExpenseSplitResponse(split)).willReturn(dummyResponse());

    expenseSplitService.settleSplit(expenseId, splitId, userId);

    assertThat(split.getSettled()).isTrue();
    assertThat(split.getDeclaredPaid()).isFalse();
    assertThat(split.getDeclaredAt()).isNull();
  }

  @Test
  void should_throw_when_a_stranger_settles_the_split() {
    Expense expense = createExpense(BigDecimal.valueOf(100));
    UUID splitId = UUID.randomUUID();
    ExpenseSplit split = friendSplitOn(expense, splitId, false);

    given(expenseRepository.findById(expenseId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.findById(splitId)).willReturn(Optional.of(split));

    assertThatThrownBy(() -> expenseSplitService.settleSplit(expenseId, splitId, UUID.randomUUID()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void should_throw_when_settling_the_owners_own_share() {
    Expense expense = createExpense(BigDecimal.valueOf(100));
    UUID splitId = UUID.randomUUID();
    User owner = new User();
    owner.setId(userId);

    ExpenseSplit split =
        ExpenseSplit.builder().id(splitId).expense(expense).user(owner).settled(true).build();

    given(expenseRepository.findById(expenseId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.findById(splitId)).willReturn(Optional.of(split));

    assertThatThrownBy(() -> expenseSplitService.settleSplit(expenseId, splitId, userId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("The owner's own share is not settleable");
  }

  @Test
  void should_be_idempotent_when_settling_an_already_settled_split() {
    // A stray second swipe must not surface an error.
    Expense expense = createExpense(BigDecimal.valueOf(100));
    UUID splitId = UUID.randomUUID();
    ExpenseSplit split = friendSplitOn(expense, splitId, true);

    given(expenseRepository.findById(expenseId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.findById(splitId)).willReturn(Optional.of(split));
    given(expenseSplitRepository.save(split)).willReturn(split);
    given(expenseMapper.toExpenseSplitResponse(split)).willReturn(dummyResponse());

    expenseSplitService.settleSplit(expenseId, splitId, userId);

    assertThat(split.getSettled()).isTrue();
  }

  @Test
  void should_unsettle_split_and_clear_settled_at() {
    Expense expense = createExpense(BigDecimal.valueOf(100));
    UUID splitId = UUID.randomUUID();
    ExpenseSplit split = friendSplitOn(expense, splitId, true);

    given(expenseRepository.findById(expenseId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.findById(splitId)).willReturn(Optional.of(split));
    given(expenseSplitRepository.save(split)).willReturn(split);
    given(expenseMapper.toExpenseSplitResponse(split)).willReturn(dummyResponse());

    expenseSplitService.unsettleSplit(expenseId, splitId, userId);

    assertThat(split.getSettled()).isFalse();
    assertThat(split.getSettledAt()).isNull();
  }

  // region BY_ITEM — unequal shares of one product

  /** One 10.00 item on the expense, shared by the owner and a friend. */
  private ExpenseItem singleItem(UUID itemId, Expense expense, BigDecimal price) {
    return ExpenseItem.builder()
        .id(itemId)
        .expense(expense)
        .name("Steak")
        .price(price)
        .quantity(BigDecimal.ONE)
        .build();
  }

  private void stubByItem(ExpenseItem item) {
    given(expenseRepository.findByIdAndUser_Id(expenseId, userId))
        .willReturn(Optional.of(item.getExpense()));
    given(expenseSplitRepository.existsByExpenseId(expenseId)).willReturn(false);
    given(friendshipService.areFriends(userId, friendId)).willReturn(true);
    given(expenseItemRepository.findByExpenseId(expenseId)).willReturn(List.of(item));
  }

  /** Only needed on the paths that actually get as far as building splits. */
  private void stubUserLookup() {
    given(userRepository.getReferenceById(any()))
        .willAnswer(
            inv -> {
              User u = new User();
              u.setId(inv.getArgument(0));
              return u;
            });
  }

  @Test
  void should_split_one_item_unequally_when_explicit_shares_are_given() {
    Expense expense = createExpense(null);
    UUID itemId = UUID.randomUUID();
    ExpenseItem item = singleItem(itemId, expense, BigDecimal.valueOf(10));

    stubByItem(item);
    stubUserLookup();
    given(expenseSplitRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
    given(expenseItemSplitRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
    given(expenseMapper.toExpenseSplitResponse(any())).willReturn(dummyResponse());

    // Not 5/5: the friend ate most of it.
    CreateExpenseSplitRequest request =
        new CreateExpenseSplitRequest(
            ExpenseSplitType.BY_ITEM,
            List.of(new SplitParticipant(friendId, null)),
            List.of(
                new ItemSplitAssignment(
                    itemId,
                    null,
                    List.of(
                        new ItemShare(userId, BigDecimal.valueOf(2.50)),
                        new ItemShare(friendId, BigDecimal.valueOf(7.50))))));

    expenseSplitService.createSplit(expenseId, request, userId);

    verify(expenseItemSplitRepository).saveAll(itemSplitsCaptor.capture());
    List<ExpenseItemSplit> itemSplits = itemSplitsCaptor.getValue();
    // The per-product share is now persisted, not recomputed as an equal division.
    assertThat(
            itemSplits.stream()
                .filter(s -> s.getUser().getId().equals(friendId))
                .findFirst()
                .orElseThrow()
                .getAmount())
        .isEqualByComparingTo("7.50");

    verify(expenseSplitRepository).saveAll(splitsCaptor.capture());
    List<ExpenseSplit> splits = splitsCaptor.getValue();
    assertThat(
            splits.stream()
                .filter(s -> s.getUser().getId().equals(friendId))
                .findFirst()
                .orElseThrow()
                .getAmount())
        .isEqualByComparingTo("7.50");
  }

  @Test
  void should_throw_when_item_shares_do_not_add_up_to_the_item_total() {
    Expense expense = createExpense(null);
    UUID itemId = UUID.randomUUID();
    ExpenseItem item = singleItem(itemId, expense, BigDecimal.valueOf(10));

    stubByItem(item);

    CreateExpenseSplitRequest request =
        new CreateExpenseSplitRequest(
            ExpenseSplitType.BY_ITEM,
            List.of(new SplitParticipant(friendId, null)),
            List.of(
                new ItemSplitAssignment(
                    itemId,
                    null,
                    List.of(
                        new ItemShare(userId, BigDecimal.valueOf(2)),
                        new ItemShare(friendId, BigDecimal.valueOf(3))))));

    assertThatThrownBy(() -> expenseSplitService.createSplit(expenseId, request, userId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("add up to");
  }

  @Test
  void should_still_split_equally_when_only_user_ids_are_given() {
    // Older clients send userIds and must keep working.
    Expense expense = createExpense(null);
    UUID itemId = UUID.randomUUID();
    ExpenseItem item = singleItem(itemId, expense, BigDecimal.valueOf(10));

    stubByItem(item);
    stubUserLookup();
    given(expenseSplitRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
    given(expenseItemSplitRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
    given(expenseMapper.toExpenseSplitResponse(any())).willReturn(dummyResponse());

    CreateExpenseSplitRequest request =
        new CreateExpenseSplitRequest(
            ExpenseSplitType.BY_ITEM,
            List.of(new SplitParticipant(friendId, null)),
            List.of(new ItemSplitAssignment(itemId, List.of(userId, friendId), null)));

    expenseSplitService.createSplit(expenseId, request, userId);

    verify(expenseItemSplitRepository).saveAll(itemSplitsCaptor.capture());
    assertThat(itemSplitsCaptor.getValue())
        .allSatisfy(s -> assertThat(s.getAmount()).isEqualByComparingTo("5.00"));
  }

  // endregion

  // region settlement notifications

  @Test
  void should_notify_the_participant_when_the_owner_settles_their_share() {
    Expense expense = createExpense(BigDecimal.valueOf(100));
    expense.setShop("Biedronka");
    UUID splitId = UUID.randomUUID();
    ExpenseSplit split = friendSplitOn(expense, splitId, false);

    given(expenseRepository.findById(expenseId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.findById(splitId)).willReturn(Optional.of(split));
    given(expenseSplitRepository.save(split)).willReturn(split);
    given(expenseMapper.toExpenseSplitResponse(split)).willReturn(dummyResponse());

    expenseSplitService.settleSplit(expenseId, splitId, userId);

    ArgumentCaptor<ExpenseSettlementChangedEvent> captor =
        ArgumentCaptor.forClass(ExpenseSettlementChangedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());

    ExpenseSettlementChangedEvent event = captor.getValue();
    assertThat(event.settled()).isTrue();
    assertThat(event.actorId()).isEqualTo(userId);
    // The other side hears about it — never the person who did it.
    assertThat(event.recipientIds()).containsExactly(friendId);
    assertThat(event.shop()).isEqualTo("Biedronka");
  }

  @Test
  void should_notify_the_owner_with_a_declaration_when_the_debtor_marks_their_payment() {
    Expense expense = createExpense(BigDecimal.valueOf(100));
    expense.setShop("Biedronka");
    UUID splitId = UUID.randomUUID();
    ExpenseSplit split = friendSplitOn(expense, splitId, false);

    given(expenseRepository.findById(expenseId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.findById(splitId)).willReturn(Optional.of(split));
    given(expenseSplitRepository.save(split)).willReturn(split);
    given(expenseMapper.toExpenseSplitResponse(split)).willReturn(dummyResponse());

    expenseSplitService.settleSplit(expenseId, splitId, friendId);

    ArgumentCaptor<ExpensePaymentDeclaredEvent> captor =
        ArgumentCaptor.forClass(ExpensePaymentDeclaredEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());

    ExpensePaymentDeclaredEvent event = captor.getValue();
    assertThat(event.ownerId()).isEqualTo(userId);
    assertThat(event.actorId()).isEqualTo(friendId);
    assertThat(event.declared()).isTrue();
    assertThat(event.shop()).isEqualTo("Biedronka");
  }

  @Test
  void should_not_renotify_the_owner_on_a_repeated_declaration() {
    // A stray double swipe must not spam the owner.
    Expense expense = createExpense(BigDecimal.valueOf(100));
    UUID splitId = UUID.randomUUID();
    ExpenseSplit split = friendSplitOn(expense, splitId, false);
    split.setDeclaredPaid(true);
    split.setDeclaredAt(Instant.now());

    given(expenseRepository.findById(expenseId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.findById(splitId)).willReturn(Optional.of(split));
    given(expenseSplitRepository.save(split)).willReturn(split);
    given(expenseMapper.toExpenseSplitResponse(split)).willReturn(dummyResponse());

    expenseSplitService.settleSplit(expenseId, splitId, friendId);

    verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any());
  }

  @Test
  void should_notify_on_unsettle_too() {
    Expense expense = createExpense(BigDecimal.valueOf(100));
    UUID splitId = UUID.randomUUID();
    ExpenseSplit split = friendSplitOn(expense, splitId, true);

    given(expenseRepository.findById(expenseId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.findById(splitId)).willReturn(Optional.of(split));
    given(expenseSplitRepository.save(split)).willReturn(split);
    given(expenseMapper.toExpenseSplitResponse(split)).willReturn(dummyResponse());

    expenseSplitService.unsettleSplit(expenseId, splitId, userId);

    ArgumentCaptor<ExpenseSettlementChangedEvent> captor =
        ArgumentCaptor.forClass(ExpenseSettlementChangedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());

    assertThat(captor.getValue().settled()).isFalse();
    assertThat(captor.getValue().recipientIds()).containsExactly(friendId);
  }

  // endregion

  @Test
  void should_refuse_to_unsettle_a_split_cleared_by_a_settle_up() {
    // The money really changed hands in that settle-up; unsettling this one share
    // would resurrect a balance for it. The whole settlement must be undone instead.
    Expense expense = createExpense(BigDecimal.valueOf(100));
    UUID splitId = UUID.randomUUID();
    ExpenseSplit split = friendSplitOn(expense, splitId, true);
    split.setSettledByDebt(pl.settly.settly_api.debts.model.Debt.builder().build());

    given(expenseRepository.findById(expenseId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.findById(splitId)).willReturn(Optional.of(split));

    assertThatThrownBy(() -> expenseSplitService.unsettleSplit(expenseId, splitId, userId))
        .isInstanceOf(pl.settly.settly_api.common.exception.SettlementLockedException.class)
        .hasMessageContaining("settle-up");

    assertThat(split.getSettled()).isTrue(); // unchanged
  }

  @Test
  void should_throw_when_split_not_found_on_settle() {
    Expense expense = createExpense(BigDecimal.valueOf(100));
    UUID splitId = UUID.randomUUID();

    given(expenseRepository.findById(expenseId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.findById(splitId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> expenseSplitService.settleSplit(expenseId, splitId, userId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Split does not exist");
  }

  // endregion

  // region setExpenseSettled (whole expense)

  @Test
  void should_settle_every_participant_when_owner_settles_the_expense() {
    Expense expense = createExpense(BigDecimal.valueOf(100));
    User owner = new User();
    owner.setId(userId);

    ExpenseSplit ownerShare =
        ExpenseSplit.builder()
            .id(UUID.randomUUID())
            .expense(expense)
            .user(owner)
            .settled(true)
            .build();
    ExpenseSplit friendShare = friendSplitOn(expense, UUID.randomUUID(), false);

    given(expenseRepository.findById(expenseId)).willReturn(Optional.of(expense));
    given(expenseAccessService.hasNoAccessToExpense(expenseId, userId)).willReturn(false);
    given(expenseSplitRepository.findByExpenseId(expenseId))
        .willReturn(List.of(ownerShare, friendShare));
    given(expenseMapper.toExpenseSplitResponse(friendShare)).willReturn(dummyResponse());

    expenseSplitService.setExpenseSettled(expenseId, userId, true);

    assertThat(friendShare.getSettled()).isTrue();
    // The owner's own share is never toggled.
    assertThat(ownerShare.getSettled()).isTrue();
  }

  @Test
  void should_declare_only_own_share_when_participant_settles_the_expense() {
    Expense expense = createExpense(BigDecimal.valueOf(100));
    UUID otherFriendId = UUID.randomUUID();

    ExpenseSplit myShare = friendSplitOn(expense, UUID.randomUUID(), false);
    User other = createFriendUser(otherFriendId);
    ExpenseSplit othersShare =
        ExpenseSplit.builder()
            .id(UUID.randomUUID())
            .expense(expense)
            .user(other)
            .settled(false)
            .amount(BigDecimal.valueOf(25))
            .build();

    given(expenseRepository.findById(expenseId)).willReturn(Optional.of(expense));
    given(expenseAccessService.hasNoAccessToExpense(expenseId, friendId)).willReturn(false);
    given(expenseSplitRepository.findByExpenseId(expenseId))
        .willReturn(List.of(myShare, othersShare));
    given(expenseMapper.toExpenseSplitResponse(myShare)).willReturn(dummyResponse());

    expenseSplitService.setExpenseSettled(expenseId, friendId, true);

    // The participant's "settle" is only a claim — nothing becomes settled.
    assertThat(myShare.getSettled()).isFalse();
    assertThat(myShare.getDeclaredPaid()).isTrue();
    // A participant must not touch anybody else's share.
    assertThat(othersShare.getSettled()).isFalse();
    assertThat(othersShare.getDeclaredPaid()).isFalse();

    ArgumentCaptor<ExpensePaymentDeclaredEvent> captor =
        ArgumentCaptor.forClass(ExpensePaymentDeclaredEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().ownerId()).isEqualTo(userId);
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
