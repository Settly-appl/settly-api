package pl.settly.settly_api.expenses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.common.exception.ResourceNotFoundException;
import pl.settly.settly_api.expenses.dto.CreateExpenseItemRequest;
import pl.settly.settly_api.expenses.dto.CreateExpenseRequest;
import pl.settly.settly_api.expenses.dto.ExpenseItemResponse;
import pl.settly.settly_api.expenses.dto.ExpenseItemSplitUserResponse;
import pl.settly.settly_api.expenses.dto.ExpenseMapper;
import pl.settly.settly_api.expenses.dto.ExpenseResponse;
import pl.settly.settly_api.expenses.model.Expense;
import pl.settly.settly_api.expenses.model.ExpenseItem;
import pl.settly.settly_api.expenses.model.ExpenseItemSplit;
import pl.settly.settly_api.expenses.repository.ExpenseItemRepository;
import pl.settly.settly_api.expenses.repository.ExpenseItemSplitRepository;
import pl.settly.settly_api.expenses.repository.ExpenseRepository;
import pl.settly.settly_api.expenses.service.ExpenseAccessService;
import pl.settly.settly_api.expenses.service.ExpenseService;

@ExtendWith(MockitoExtension.class)
class ExpensesServiceTest {

  @Mock ExpenseRepository expenseRepository;
  @Mock ExpenseItemRepository expenseItemRepository;
  @Mock ExpenseItemSplitRepository expenseItemSplitRepository;
  @Mock ExpenseMapper expenseMapper;
  @Mock UserRepository userRepository;
  @Mock ExpenseAccessService expenseAccessService;
  @Mock pl.settly.settly_api.projects.repository.ProjectRepository projectRepository;
  @Mock pl.settly.settly_api.projects.service.ProjectAccessService projectAccessService;
  @Mock pl.settly.settly_api.expenses.repository.ExpenseSplitRepository expenseSplitRepository;

  @InjectMocks ExpenseService expenseService;

  private final UUID userId = UUID.randomUUID();
  private final UUID expenseId = UUID.randomUUID();
  private final UUID projectId = UUID.randomUUID();

  // region createExpense

  @Test
  void should_create_expense_successfully() {
    CreateExpenseRequest request =
        new CreateExpenseRequest(
            "Test Shop",
            "Test Note",
            "Test category",
            "PLN",
            BigDecimal.valueOf(100.00),
            LocalDate.now(),
            projectId);
    User user = new User();
    user.setId(userId);
    Expense expense = new Expense();
    Expense savedExpense = new Expense();
    savedExpense.setUser(user); // expense.user is a non-null FK in production
    ExpenseResponse expectedResponse = createDefaultResponse();

    given(expenseMapper.toExpense(request)).willReturn(expense);
    given(userRepository.getReferenceById(userId)).willReturn(user);
    given(projectAccessService.isMember(projectId, userId)).willReturn(true);
    given(expenseRepository.save(expense)).willReturn(savedExpense);
    given(expenseMapper.toExpenseResponse(savedExpense)).willReturn(expectedResponse);

    ExpenseResponse response = expenseService.createExpense(request, userId);

    assertThat(response).isEqualTo(expectedResponse);
    assertThat(expense.getUser()).isEqualTo(user);
    verify(expenseRepository).save(expense);
  }

  @Test
  void should_throw_when_user_not_found_on_create() {
    CreateExpenseRequest request =
        new CreateExpenseRequest(
            "Test Shop",
            "Test Note",
            "Test category",
            "PLN",
            BigDecimal.valueOf(100.00),
            LocalDate.now(),
            projectId);

    given(userRepository.getReferenceById(userId))
        .willThrow(new EntityNotFoundException("User not found"));

    assertThatThrownBy(() -> expenseService.createExpense(request, userId))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage("User not found");
  }

  // endregion

  // region getExpense

  @Test
  void should_return_expense_when_found() {
    Expense expense = ownedExpense();
    ExpenseResponse expectedResponse = createDefaultResponse();

    given(expenseAccessService.hasNoAccessToExpense(expenseId, userId)).willReturn(false);
    given(expenseRepository.findById(expenseId)).willReturn(Optional.of(expense));
    given(expenseSplitRepository.findByExpenseId(expenseId)).willReturn(List.of());
    given(expenseMapper.toExpenseResponse(expense)).willReturn(expectedResponse);

    ExpenseResponse response = expenseService.getExpense(expenseId, userId);

    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  void should_throw_when_expense_not_found() {
    given(expenseAccessService.hasNoAccessToExpense(expenseId, userId)).willReturn(true);

    assertThatThrownBy(() -> expenseService.getExpense(expenseId, userId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Expense does not exist");
  }

  // endregion

  // region updateExpense

  @Test
  void should_update_expense_successfully() {
    CreateExpenseRequest request =
        new CreateExpenseRequest(
            "Updated Shop",
            "Updated Note",
            "EUR",
            "food",
            BigDecimal.valueOf(150.00),
            LocalDate.now().plusDays(1),
            projectId);
    Expense existingExpense = new Expense();
    existingExpense.setId(expenseId);
    existingExpense.setCategory("transport");
    existingExpense.setCurrency("PLN");
    Expense savedExpense = ownedExpense();
    ExpenseResponse expectedResponse = createDefaultResponse();

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId))
        .willReturn(Optional.of(existingExpense));
    given(projectAccessService.isMember(projectId, userId)).willReturn(true);
    given(expenseRepository.save(existingExpense)).willReturn(savedExpense);
    given(expenseSplitRepository.findByExpenseId(expenseId)).willReturn(List.of());
    given(expenseMapper.toExpenseResponse(savedExpense)).willReturn(expectedResponse);

    ExpenseResponse response = expenseService.updateExpense(expenseId, userId, request);

    assertThat(response).isEqualTo(expectedResponse);
    // All editable fields are applied — including category + currency, which the
    // update used to silently drop.
    assertThat(existingExpense.getShop()).isEqualTo("Updated Shop");
    assertThat(existingExpense.getNote()).isEqualTo("Updated Note");
    assertThat(existingExpense.getCategory()).isEqualTo("food");
    assertThat(existingExpense.getCurrency()).isEqualTo("EUR");
    assertThat(existingExpense.getTotalAmount()).isEqualByComparingTo("150.00");
    verify(expenseRepository).save(existingExpense);
  }

  // endregion

  // region deleteExpense

  @Test
  void should_delete_expense_with_its_splits_and_items() {
    Expense expense = new Expense();

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseItemSplitRepository.findByExpenseItemExpenseId(expenseId)).willReturn(List.of());
    given(expenseSplitRepository.findByExpenseId(expenseId)).willReturn(List.of());
    given(expenseItemRepository.findByExpenseId(expenseId)).willReturn(List.of());

    expenseService.deleteExpense(expenseId, userId);

    // Children are cleared before the expense (FK constraints).
    verify(expenseItemSplitRepository).deleteAll(anyList());
    verify(expenseSplitRepository).deleteAll(anyList());
    verify(expenseItemRepository).deleteAll(anyList());
    verify(expenseRepository).delete(expense);
  }

  // endregion

  // region searchExpenses
  @Test
  void should_return_paged_expenses_when_searching() {
    // Arrange
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
    String category = "FOOD";
    Expense expense = ownedExpense();
    expense.setId(expenseId);
    ExpenseResponse expectedResponse = createDefaultResponse();

    // Tworzymy stronę wyników
    Page<Expense> page = new PageImpl<>(List.of(expense), pageable, 1);

    // Mockujemy wywołanie repozytorium (używamy Twojej nowej metody findExpenses)
    given(expenseRepository.findExpenses(userId, category, null, pageable)).willReturn(page);
    // Splits for the whole page are fetched in one batch (no N+1).
    given(expenseSplitRepository.findByExpenseIdIn(List.of(expenseId))).willReturn(List.of());

    // Ważne: map() w Page używa mappera dla każdego elementu
    given(expenseMapper.toExpenseResponse(expense)).willReturn(expectedResponse);

    // Act
    Page<ExpenseResponse> response =
        expenseService.searchExpenses(pageable, category, null, userId);

    // Assert
    assertThat(response.getContent()).containsExactly(expectedResponse);
    assertThat(response.getTotalElements()).isEqualTo(1);
    assertThat(response.getNumber()).isEqualTo(0);

    verify(expenseRepository).findExpenses(userId, category, null, pageable);
  }

  @Test
  void should_narrow_expenses_to_a_project_when_asked() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
    Expense expense = ownedExpense();
    expense.setId(expenseId);
    ExpenseResponse expectedResponse = createDefaultResponse();

    Page<Expense> page = new PageImpl<>(List.of(expense), pageable, 1);
    given(expenseRepository.findExpenses(userId, null, projectId, pageable)).willReturn(page);
    given(expenseSplitRepository.findByExpenseIdIn(List.of(expenseId))).willReturn(List.of());
    given(expenseMapper.toExpenseResponse(expense)).willReturn(expectedResponse);

    expenseService.searchExpenses(pageable, null, projectId, userId);

    // The project must reach the query — otherwise a project's expense list would
    // silently show every expense the user has.
    verify(expenseRepository).findExpenses(userId, null, projectId, pageable);
  }

  // endregion

  // region addItem

  @Test
  void should_add_item_to_expense_successfully() {
    Expense expense = new Expense();
    expense.setId(expenseId);
    CreateExpenseItemRequest request =
        new CreateExpenseItemRequest("Milk", BigDecimal.valueOf(6.00), BigDecimal.ONE, "groceries");
    ExpenseItem item = new ExpenseItem();
    ExpenseItem savedItem = new ExpenseItem();
    savedItem.setExpense(expense);
    UUID itemId = UUID.randomUUID();
    ExpenseItemResponse expectedResponse =
        new ExpenseItemResponse(
            itemId, expenseId, "Milk", BigDecimal.valueOf(6.00), BigDecimal.ONE, "groceries");

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseMapper.toExpenseItem(request)).willReturn(item);
    given(expenseItemRepository.save(item)).willReturn(savedItem);
    given(expenseMapper.toExpenseItemResponse(savedItem)).willReturn(expectedResponse);

    ExpenseItemResponse response = expenseService.addItem(expenseId, userId, request);

    assertThat(response).isEqualTo(expectedResponse);
    assertThat(item.getExpense()).isEqualTo(expense);
    verify(expenseItemRepository).save(item);
  }

  @Test
  void should_throw_when_expense_not_found_on_add_item() {
    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.empty());

    CreateExpenseItemRequest request =
        new CreateExpenseItemRequest("Milk", BigDecimal.valueOf(6.00), BigDecimal.ONE, null);

    assertThatThrownBy(() -> expenseService.addItem(expenseId, userId, request))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Expense does not exist");
  }

  // endregion

  // region getItems

  @Test
  void should_return_items_for_expense() {
    Expense expense = new Expense();
    expense.setId(expenseId);
    ExpenseItem item = new ExpenseItem();
    item.setExpense(expense);
    UUID itemId = UUID.randomUUID();
    ExpenseItemResponse expectedResponse =
        new ExpenseItemResponse(
            itemId, expenseId, "Milk", BigDecimal.valueOf(6.00), BigDecimal.ONE, null);

    given(expenseAccessService.hasNoAccessToExpense(expenseId, userId)).willReturn(false);
    given(expenseItemRepository.findByExpenseId(expenseId)).willReturn(List.of(item));
    given(expenseMapper.toExpenseItemResponse(item)).willReturn(expectedResponse);

    List<ExpenseItemResponse> result = expenseService.getItems(expenseId, userId);

    assertThat(result).hasSize(1).containsExactly(expectedResponse);
  }

  @Test
  void should_throw_when_expense_not_found_on_get_items() {
    given(expenseAccessService.hasNoAccessToExpense(expenseId, userId)).willReturn(true);

    assertThatThrownBy(() -> expenseService.getItems(expenseId, userId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Expense does not exist");
  }

  // endregion

  // region getItemSplitUsers

  @Test
  void should_return_split_users_for_item() {
    UUID itemId = UUID.randomUUID();
    UUID expenseId = UUID.randomUUID();

    Expense expense = new Expense();
    expense.setId(expenseId);
    expense.setCurrency("PLN");

    ExpenseItem item = new ExpenseItem();
    item.setExpense(expense);

    User splitUser = new User();
    UUID splitUserId = UUID.randomUUID();
    splitUser.setId(splitUserId);
    splitUser.setUsername("split_user");
    splitUser.setDisplayName("Split User");
    splitUser.setAvatarUrl("https://avatar.example/split.png");

    given(expenseItemRepository.findById(itemId)).willReturn(Optional.of(item));
    given(expenseAccessService.hasNoAccessToExpense(expenseId, userId)).willReturn(false);
    ExpenseItemSplit itemSplit =
        ExpenseItemSplit.builder()
            .user(splitUser)
            .amount(BigDecimal.valueOf(7.50)) // an unequal share of this product
            .build();
    given(expenseItemSplitRepository.findWithUserByExpenseItemId(itemId))
        .willReturn(List.of(itemSplit));

    List<ExpenseItemSplitUserResponse> result = expenseService.getItemSplitUsers(itemId, userId);

    assertThat(result)
        .containsExactly(
            new ExpenseItemSplitUserResponse(
                splitUserId,
                "split_user",
                "Split User",
                "https://avatar.example/split.png",
                BigDecimal.valueOf(7.50)));
  }

  @Test
  void should_return_split_users_when_logged_user_is_not_in_item_split_but_belongs_to_expense() {
    UUID itemId = UUID.randomUUID();
    UUID expenseId = UUID.randomUUID();

    Expense expense = new Expense();
    expense.setId(expenseId);
    expense.setCurrency("PLN");

    ExpenseItem item = new ExpenseItem();
    item.setExpense(expense);

    User splitUser = new User();
    UUID splitUserId = UUID.randomUUID();
    splitUser.setId(splitUserId);
    splitUser.setUsername("split_user");
    splitUser.setDisplayName("Split User");
    splitUser.setAvatarUrl("https://avatar.example/split.png");

    given(expenseItemRepository.findById(itemId)).willReturn(Optional.of(item));
    given(expenseAccessService.hasNoAccessToExpense(expenseId, userId)).willReturn(false);
    ExpenseItemSplit itemSplit =
        ExpenseItemSplit.builder()
            .user(splitUser)
            .amount(BigDecimal.valueOf(7.50)) // an unequal share of this product
            .build();
    given(expenseItemSplitRepository.findWithUserByExpenseItemId(itemId))
        .willReturn(List.of(itemSplit));

    List<ExpenseItemSplitUserResponse> result = expenseService.getItemSplitUsers(itemId, userId);

    assertThat(result)
        .containsExactly(
            new ExpenseItemSplitUserResponse(
                splitUserId,
                "split_user",
                "Split User",
                "https://avatar.example/split.png",
                BigDecimal.valueOf(7.50)));
  }

  @Test
  void should_throw_when_item_not_found_on_get_item_split_users() {
    UUID itemId = UUID.randomUUID();
    given(expenseItemRepository.findById(itemId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> expenseService.getItemSplitUsers(itemId, userId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Item does not exist");
  }

  @Test
  void should_throw_when_item_belongs_to_other_user_on_get_item_split_users() {
    UUID itemId = UUID.randomUUID();
    UUID expenseId = UUID.randomUUID();

    Expense expense = new Expense();
    expense.setId(expenseId);
    expense.setCurrency("PLN");

    ExpenseItem item = new ExpenseItem();
    item.setExpense(expense);

    given(expenseItemRepository.findById(itemId)).willReturn(Optional.of(item));
    given(expenseAccessService.hasNoAccessToExpense(expenseId, userId)).willReturn(true);

    assertThatThrownBy(() -> expenseService.getItemSplitUsers(itemId, userId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Item does not exist");
  }

  // endregion

  // region deleteItem

  @Test
  void should_delete_item_successfully() {
    Expense expense = new Expense();
    expense.setId(expenseId);
    UUID itemId = UUID.randomUUID();
    ExpenseItem item = new ExpenseItem();
    item.setId(itemId);
    item.setExpense(expense);

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseItemRepository.findById(itemId)).willReturn(Optional.of(item));
    given(expenseItemSplitRepository.existsByExpenseItemId(itemId)).willReturn(false);

    expenseService.deleteItem(expenseId, itemId, userId);

    verify(expenseItemRepository).delete(item);
  }

  @Test
  void should_throw_when_item_not_found_on_delete() {
    Expense expense = new Expense();
    expense.setId(expenseId);
    UUID itemId = UUID.randomUUID();

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseItemRepository.findById(itemId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> expenseService.deleteItem(expenseId, itemId, userId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Item does not exist");
  }

  @Test
  void should_throw_when_item_belongs_to_different_expense() {
    Expense expense = new Expense();
    expense.setId(expenseId);
    Expense otherExpense = new Expense();
    otherExpense.setId(UUID.randomUUID());
    UUID itemId = UUID.randomUUID();
    ExpenseItem item = new ExpenseItem();
    item.setId(itemId);
    item.setExpense(otherExpense);

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseItemRepository.findById(itemId)).willReturn(Optional.of(item));

    assertThatThrownBy(() -> expenseService.deleteItem(expenseId, itemId, userId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Item does not exist");
  }

  @Test
  void should_throw_when_deleting_item_that_is_part_of_split() {
    Expense expense = new Expense();
    expense.setId(expenseId);
    UUID itemId = UUID.randomUUID();
    ExpenseItem item = new ExpenseItem();
    item.setId(itemId);
    item.setExpense(expense);

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseItemRepository.findById(itemId)).willReturn(Optional.of(item));
    given(expenseItemSplitRepository.existsByExpenseItemId(itemId)).willReturn(true);

    assertThatThrownBy(() -> expenseService.deleteItem(expenseId, itemId, userId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot delete item");
  }

  // endregion

  /** An expense owned by userId — expense.user is a non-null FK in production. */
  private Expense ownedExpense() {
    User owner = new User();
    owner.setId(userId);
    Expense expense = new Expense();
    expense.setUser(owner);
    return expense;
  }

  // Helper do tworzenia powtarzalnych obiektów response z nowymi polami
  private ExpenseResponse createDefaultResponse() {
    return new ExpenseResponse(
        expenseId,
        userId,
        projectId,
        "Test Shop",
        "Test Note",
        "FOOD",
        "PLN",
        BigDecimal.valueOf(100.00),
        false,
        LocalDate.now(),
        Instant.now(),
        0,
        0,
        false);
  }
}
