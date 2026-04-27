package pl.settly.settly_api.expenses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import pl.settly.settly_api.expenses.repository.ExpenseItemRepository;
import pl.settly.settly_api.expenses.repository.ExpenseItemSplitRepository;
import pl.settly.settly_api.expenses.repository.ExpenseRepository;
import pl.settly.settly_api.expenses.service.ExpenseService;

@ExtendWith(MockitoExtension.class)
class ExpensesServiceTest {

  @Mock ExpenseRepository expenseRepository;
  @Mock ExpenseItemRepository expenseItemRepository;
  @Mock ExpenseItemSplitRepository expenseItemSplitRepository;
  @Mock ExpenseMapper expenseMapper;
  @Mock UserRepository userRepository;

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
    Expense expense = new Expense();
    Expense savedExpense = new Expense();
    ExpenseResponse expectedResponse = createDefaultResponse();

    given(expenseMapper.toExpense(request)).willReturn(expense);
    given(userRepository.getReferenceById(userId)).willReturn(user);
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
    Expense expense = new Expense();
    ExpenseResponse expectedResponse = createDefaultResponse();

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseMapper.toExpenseResponse(expense)).willReturn(expectedResponse);

    ExpenseResponse response = expenseService.getExpense(expenseId, userId);

    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  void should_throw_when_expense_not_found() {
    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.empty());

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
            "Update category",
            "PLN",
            BigDecimal.valueOf(150.00),
            LocalDate.now().plusDays(1),
            projectId);
    Expense existingExpense = new Expense();
    existingExpense.setId(expenseId);
    Expense savedExpense = new Expense();
    ExpenseResponse expectedResponse = createDefaultResponse();

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId))
        .willReturn(Optional.of(existingExpense));
    given(expenseRepository.save(existingExpense)).willReturn(savedExpense);
    given(expenseMapper.toExpenseResponse(savedExpense)).willReturn(expectedResponse);

    ExpenseResponse response = expenseService.updateExpense(expenseId, userId, request);

    assertThat(response).isEqualTo(expectedResponse);
    verify(expenseRepository).save(existingExpense);
  }

  // endregion

  // region deleteExpense

  @Test
  void should_delete_expense_successfully() {
    Expense expense = new Expense();

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));

    expenseService.deleteExpense(expenseId, userId);

    verify(expenseRepository).delete(expense);
  }

  // endregion

  // region searchExpenses
  @Test
  void should_return_paged_expenses_when_searching() {
    // Arrange
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
    String category = "FOOD";
    Expense expense = new Expense();
    ExpenseResponse expectedResponse = createDefaultResponse();

    // Tworzymy stronę wyników
    Page<Expense> page = new PageImpl<>(List.of(expense), pageable, 1);

    // Mockujemy wywołanie repozytorium (używamy Twojej nowej metody findExpenses)
    given(expenseRepository.findExpenses(userId, category, pageable)).willReturn(page);

    // Ważne: map() w Page używa mappera dla każdego elementu
    given(expenseMapper.toExpenseResponse(expense)).willReturn(expectedResponse);

    // Act
    Page<ExpenseResponse> response = expenseService.searchExpenses(pageable, category, userId);

    // Assert
    assertThat(response.getContent()).containsExactly(expectedResponse);
    assertThat(response.getTotalElements()).isEqualTo(1);
    assertThat(response.getNumber()).isEqualTo(0);

    verify(expenseRepository).findExpenses(userId, category, pageable);
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

    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(expense));
    given(expenseItemRepository.findByExpenseId(expenseId)).willReturn(List.of(item));
    given(expenseMapper.toExpenseItemResponse(item)).willReturn(expectedResponse);

    List<ExpenseItemResponse> result = expenseService.getItems(expenseId, userId);

    assertThat(result).hasSize(1).containsExactly(expectedResponse);
  }

  @Test
  void should_throw_when_expense_not_found_on_get_items() {
    given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> expenseService.getItems(expenseId, userId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Expense does not exist");
  }

  // endregion

  // region getItemSplitUsers

  @Test
  void should_return_split_users_for_item() {
    UUID itemId = UUID.randomUUID();
    Expense expense = new Expense();
    User owner = new User();
    owner.setId(userId);
    expense.setUser(owner);

    ExpenseItem item = new ExpenseItem();
    item.setId(itemId);
    item.setExpense(expense);

    User splitUser = new User();
    UUID splitUserId = UUID.randomUUID();
    splitUser.setId(splitUserId);
    splitUser.setUsername("split_user");
    splitUser.setDisplayName("Split User");
    splitUser.setAvatarUrl("https://avatar.example/split.png");

    given(expenseItemRepository.findById(itemId)).willReturn(Optional.of(item));
    given(expenseItemSplitRepository.findDistinctUsersByExpenseItemId(itemId))
        .willReturn(List.of(splitUser));

    List<ExpenseItemSplitUserResponse> result = expenseService.getItemSplitUsers(itemId, userId);

    assertThat(result)
        .containsExactly(
            new ExpenseItemSplitUserResponse(
                splitUserId, "split_user", "Split User", "https://avatar.example/split.png"));
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
    Expense expense = new Expense();
    User otherOwner = new User();
    otherOwner.setId(UUID.randomUUID());
    expense.setUser(otherOwner);

    ExpenseItem item = new ExpenseItem();
    item.setId(itemId);
    item.setExpense(expense);

    given(expenseItemRepository.findById(itemId)).willReturn(Optional.of(item));

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
        Instant.now());
  }
}
