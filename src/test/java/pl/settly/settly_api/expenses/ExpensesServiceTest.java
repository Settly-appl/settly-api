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
import pl.settly.settly_api.expenses.dto.CreateExpenseRequest;
import pl.settly.settly_api.expenses.dto.ExpenseMapper;
import pl.settly.settly_api.expenses.dto.ExpenseResponse;
import pl.settly.settly_api.expenses.model.Expense;
import pl.settly.settly_api.expenses.repository.ExpenseRepository;
import pl.settly.settly_api.expenses.service.ExpenseService;

@ExtendWith(MockitoExtension.class)
class ExpensesServiceTest {

  @Mock ExpenseRepository expenseRepository;
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
            "Test Shop", "Test Note", BigDecimal.valueOf(100.00), LocalDate.now(), projectId);
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
            "Test Shop", "Test Note", BigDecimal.valueOf(100.00), LocalDate.now(), projectId);

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

  // Helper do tworzenia powtarzalnych obiektów response z nowymi polami
  private ExpenseResponse createDefaultResponse() {
    return new ExpenseResponse(
        expenseId,
        userId,
        projectId,
        "Test Shop",
        "Test Note",
        "FOOD", // nowa kolumna
        "PLN", // nowa kolumna
        BigDecimal.valueOf(100.00),
        false,
        LocalDate.now(),
        Instant.now());
  }
}
