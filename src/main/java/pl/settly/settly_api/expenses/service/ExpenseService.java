package pl.settly.settly_api.expenses.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.common.exception.ResourceNotFoundException;
import pl.settly.settly_api.expenses.dto.ExpenseMapper;
import pl.settly.settly_api.expenses.dto.RequestExpense;
import pl.settly.settly_api.expenses.dto.RequestExpenseResponse;
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

    public RequestExpenseResponse createExpense(RequestExpense request, UUID userId) {
        var expense = expenseMapper.toExpense(request);
        var user = userRepository.findById(userId).orElseThrow();
        expense.setUserId(user);
        var savedExpense = expenseRepository.save(expense);
        return expenseMapper.toExpenseResponse(savedExpense);
    }

    public RequestExpenseResponse getExpense(UUID expenseId) {
        return expenseRepository
                .findById(expenseId)
                .map(expenseMapper::toExpenseResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Expense does not exist"));
    }

    public RequestExpenseResponse updateExpense(UUID expenseId, RequestExpense request) {
        var expense =
                expenseRepository
                        .findById(expenseId)
                        .orElseThrow(() -> new ResourceNotFoundException("Expense does not exist"));
        expense.setShop(request.shop());
        expense.setNote(request.note());
        expense.setTotalAmount(request.totalAmount());
        expense.setDate(request.date());

        return expenseMapper.toExpenseResponse(expenseRepository.save(expense));
    }

    public void deleteExpense(UUID expenseId) {
        var expense =
                expenseRepository
                        .findById(expenseId)
                        .orElseThrow(() -> new ResourceNotFoundException("Expense does not exist"));
        expenseRepository.delete(expense);
    }
}
