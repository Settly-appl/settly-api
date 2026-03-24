package pl.settly.settly_api.expenses.service;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.common.exception.ResourceNotFoundException;
import pl.settly.settly_api.common.search.PagedResponse;
import pl.settly.settly_api.expenses.dto.CreateRequestExpense;
import pl.settly.settly_api.expenses.dto.ExpenseMapper;
import pl.settly.settly_api.expenses.dto.ExpenseResponse;
import pl.settly.settly_api.expenses.dto.SearchExpenseRequest;
import pl.settly.settly_api.expenses.model.Expense;
import pl.settly.settly_api.expenses.repository.ExpenseRepository;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseMapper expenseMapper;
    private final UserRepository userRepository;

    public ExpenseService(
            ExpenseRepository expenseRepository,
            ExpenseMapper expenseMapper,
            UserRepository userRepository) {
        this.expenseRepository = expenseRepository;
        this.expenseMapper = expenseMapper;
        this.userRepository = userRepository;
    }

    public ExpenseResponse createExpense(CreateRequestExpense request, UUID userId) {
        Expense expense = expenseMapper.toExpense(request);
        User user = userRepository.getReferenceById(userId);
        expense.setUser(user);
        Expense savedExpense = expenseRepository.save(expense);
        return expenseMapper.toExpenseResponse(savedExpense);
    }

    public ExpenseResponse getExpense(UUID expenseId, UUID userId) {
        return expenseRepository
                .findByIdAndUser_Id(expenseId, userId)
                .map(expenseMapper::toExpenseResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Expense does not exist"));
    }

    public PagedResponse<ExpenseResponse> searchExpenses(
            UUID userId, SearchExpenseRequest searchRequest) {
        Pageable pageable = createPageable(searchRequest);
        Page<Expense> expensesPage = expenseRepository.findByUser_Id(userId, pageable);
        List<ExpenseResponse> responses =
                expensesPage.getContent().stream().map(expenseMapper::toExpenseResponse).toList();
        return new PagedResponse<>(responses, expensesPage.getNumber(), expensesPage.getTotalPages());
    }

    public ExpenseResponse updateExpense(UUID expenseId, UUID userId, CreateRequestExpense request) {
        Expense expense =
                expenseRepository
                        .findByIdAndUser_Id(expenseId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Expense does not exist"));
        expense.setShop(request.shop());
        expense.setNote(request.note());
        expense.setTotalAmount(request.totalAmount());
        expense.setDate(request.date());

        return expenseMapper.toExpenseResponse(expenseRepository.save(expense));
    }

    public void deleteExpense(UUID expenseId, UUID userId) {
        Expense expense =
                expenseRepository
                        .findByIdAndUser_Id(expenseId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Expense does not exist"));
        expenseRepository.delete(expense);
    }

    private Pageable createPageable(SearchExpenseRequest searchRequest) {
        Sort.Direction sortDirection =
                searchRequest.sortDirection().equalsIgnoreCase("asc")
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC;
        String sortBy =
                (searchRequest.sortBy() != null && !searchRequest.sortBy().isEmpty())
                        ? searchRequest.sortBy()
                        : "createdAt";
        return PageRequest.of(
                searchRequest.pageNumber(), searchRequest.pageSize(), Sort.by(sortDirection, sortBy));
    }
}
