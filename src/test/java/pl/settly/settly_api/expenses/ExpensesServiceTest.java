package pl.settly.settly_api.expenses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.common.exception.ResourceNotFoundException;
import pl.settly.settly_api.common.search.PagedResponse;
import pl.settly.settly_api.expenses.dto.ExpenseMapper;
import pl.settly.settly_api.expenses.dto.CreateRequestExpense;
import pl.settly.settly_api.expenses.dto.ExpenseResponse;
import pl.settly.settly_api.expenses.dto.SearchExpenseRequest;
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
        CreateRequestExpense request = new CreateRequestExpense(
                "Test Shop",
                "Test Note",
                BigDecimal.valueOf(100.00),
                LocalDate.now(),
                projectId
        );
        User user = new User();
        Expense expense = new Expense();
        Expense savedExpense = new Expense();
        ExpenseResponse expectedResponse = new ExpenseResponse(
                expenseId,
                userId,
                projectId,
                "Test Shop",
                "Test Note",
                BigDecimal.valueOf(100.00),
                false,
                LocalDate.now(),
                Instant.now()
        );

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
        CreateRequestExpense request = new CreateRequestExpense(
                "Test Shop",
                "Test Note",
                BigDecimal.valueOf(100.00),
                LocalDate.now(),
                projectId
        );

        given(userRepository.getReferenceById(userId)).willThrow(new EntityNotFoundException("User not found"));

        assertThatThrownBy(() -> expenseService.createExpense(request, userId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("User not found");
    }

    // endregion

    // region getExpense

    @Test
    void should_return_expense_when_found() {
        Expense expense = new Expense();
        ExpenseResponse expectedResponse = new ExpenseResponse(
                expenseId,
                userId,
                projectId,
                "Test Shop",
                "Test Note",
                BigDecimal.valueOf(100.00),
                false,
                LocalDate.now(),
                Instant.now()
        );

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
        CreateRequestExpense request = new CreateRequestExpense(
                "Updated Shop",
                "Updated Note",
                BigDecimal.valueOf(150.00),
                LocalDate.now().plusDays(1),
                projectId
        );
        Expense existingExpense = new Expense();
        existingExpense.setId(expenseId);
        Expense savedExpense = new Expense();
        ExpenseResponse expectedResponse = new ExpenseResponse(
                expenseId,
                userId,
                projectId,
                "Updated Shop",
                "Updated Note",
                BigDecimal.valueOf(150.00),
                false,
                LocalDate.now().plusDays(1),
                Instant.now()
        );

        given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.of(existingExpense));
        given(expenseRepository.save(existingExpense)).willReturn(savedExpense);
        given(expenseMapper.toExpenseResponse(savedExpense)).willReturn(expectedResponse);

        ExpenseResponse response = expenseService.updateExpense(expenseId, userId, request);

        assertThat(response).isEqualTo(expectedResponse);
        assertThat(existingExpense.getShop()).isEqualTo("Updated Shop");
        assertThat(existingExpense.getNote()).isEqualTo("Updated Note");
        assertThat(existingExpense.getTotalAmount()).isEqualTo(BigDecimal.valueOf(150.00));
        assertThat(existingExpense.getDate()).isEqualTo(LocalDate.now().plusDays(1));
        verify(expenseRepository).save(existingExpense);
    }

    @Test
    void should_throw_when_expense_not_found_on_update() {
        CreateRequestExpense request = new CreateRequestExpense(
                "Updated Shop",
                "Updated Note",
                BigDecimal.valueOf(150.00),
                LocalDate.now(),
                projectId
        );

        given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.updateExpense(expenseId, userId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Expense does not exist");
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

    @Test
    void should_throw_when_expense_not_found_on_delete() {
        given(expenseRepository.findByIdAndUser_Id(expenseId, userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.deleteExpense(expenseId, userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Expense does not exist");
    }

    // endregion

    // region searchExpenses
    @Test
    void should_return_paged_expenses_when_searching() {
        SearchExpenseRequest searchRequest = new SearchExpenseRequest(0, 10, "createdAt", "desc");
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Expense expense = new Expense();
        ExpenseResponse expectedResponse = new ExpenseResponse(
                expenseId,
                userId,
                projectId,
                "Test Shop",
                "Test Note",
                BigDecimal.valueOf(100.00),
                false,
                LocalDate.now(),
                Instant.now());

        Page<Expense> page = new PageImpl<>(List.of(expense), pageable, 1);

        given(expenseRepository.findByUser_Id(userId, pageable)).willReturn(page);
        given(expenseMapper.toExpenseResponse(expense)).willReturn(expectedResponse);

        PagedResponse<ExpenseResponse> response = expenseService.searchExpenses(searchRequest, userId);

        assertThat(response.result()).containsExactly(expectedResponse);
        assertThat(response.pageNumber()).isEqualTo(0);
        assertThat(response.numberOfPages()).isEqualTo(1);

        verify(expenseRepository).findByUser_Id(userId, pageable);
    }
    // endregion

}
