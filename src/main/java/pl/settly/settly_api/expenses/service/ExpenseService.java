package pl.settly.settly_api.expenses.service;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.common.exception.ResourceNotFoundException;
import pl.settly.settly_api.expenses.dto.CreateExpenseItemRequest;
import pl.settly.settly_api.expenses.dto.CreateExpenseRequest;
import pl.settly.settly_api.expenses.dto.ExpenseItemResponse;
import pl.settly.settly_api.expenses.dto.ExpenseMapper;
import pl.settly.settly_api.expenses.dto.ExpenseResponse;
import pl.settly.settly_api.expenses.model.Expense;
import pl.settly.settly_api.expenses.model.ExpenseItem;
import pl.settly.settly_api.expenses.repository.ExpenseItemRepository;
import pl.settly.settly_api.expenses.repository.ExpenseItemSplitRepository;
import pl.settly.settly_api.expenses.repository.ExpenseRepository;

@Service
public class ExpenseService {

  private final ExpenseRepository expenseRepository;
  private final ExpenseItemRepository expenseItemRepository;
  private final ExpenseItemSplitRepository expenseItemSplitRepository;
  private final ExpenseMapper expenseMapper;
  private final UserRepository userRepository;

  public ExpenseService(
      ExpenseRepository expenseRepository,
      ExpenseItemRepository expenseItemRepository,
      ExpenseItemSplitRepository expenseItemSplitRepository,
      ExpenseMapper expenseMapper,
      UserRepository userRepository) {
    this.expenseRepository = expenseRepository;
    this.expenseItemRepository = expenseItemRepository;
    this.expenseItemSplitRepository = expenseItemSplitRepository;
    this.expenseMapper = expenseMapper;
    this.userRepository = userRepository;
  }

  public ExpenseResponse createExpense(CreateExpenseRequest request, UUID userId) {
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

  public Page<ExpenseResponse> searchExpenses(Pageable pageable, String category, UUID userId) {
    Page<Expense> expensesPage = expenseRepository.findExpenses(userId, category, pageable);

    return expensesPage.map(expenseMapper::toExpenseResponse);
  }

  public ExpenseResponse updateExpense(UUID expenseId, UUID userId, CreateExpenseRequest request) {
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

  public ExpenseItemResponse addItem(
      UUID expenseId, UUID userId, CreateExpenseItemRequest request) {
    Expense expense =
        expenseRepository
            .findByIdAndUser_Id(expenseId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Expense does not exist"));

    ExpenseItem item = expenseMapper.toExpenseItem(request);
    item.setExpense(expense);

    return expenseMapper.toExpenseItemResponse(expenseItemRepository.save(item));
  }

  public List<ExpenseItemResponse> getItems(UUID expenseId, UUID userId) {
    expenseRepository
        .findByIdAndUser_Id(expenseId, userId)
        .orElseThrow(() -> new ResourceNotFoundException("Expense does not exist"));

    return expenseItemRepository.findByExpenseId(expenseId).stream()
        .map(expenseMapper::toExpenseItemResponse)
        .toList();
  }

  public void deleteItem(UUID expenseId, UUID itemId, UUID userId) {
    expenseRepository
        .findByIdAndUser_Id(expenseId, userId)
        .orElseThrow(() -> new ResourceNotFoundException("Expense does not exist"));

    ExpenseItem item =
        expenseItemRepository
            .findById(itemId)
            .filter(i -> i.getExpense().getId().equals(expenseId))
            .orElseThrow(() -> new ResourceNotFoundException("Item does not exist"));

    if (expenseItemSplitRepository.existsByExpenseItemId(itemId)) {
      throw new IllegalArgumentException("Cannot delete item — it is part of an existing split");
    }

    expenseItemRepository.delete(item);
  }
}
