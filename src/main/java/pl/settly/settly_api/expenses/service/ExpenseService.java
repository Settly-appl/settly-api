package pl.settly.settly_api.expenses.service;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.common.exception.ResourceNotFoundException;
import pl.settly.settly_api.expenses.dto.*;
import pl.settly.settly_api.expenses.model.Expense;
import pl.settly.settly_api.expenses.model.ExpenseItem;
import pl.settly.settly_api.expenses.model.ExpenseSplit;
import pl.settly.settly_api.expenses.repository.ExpenseItemRepository;
import pl.settly.settly_api.expenses.repository.ExpenseItemSplitRepository;
import pl.settly.settly_api.expenses.repository.ExpenseRepository;
import pl.settly.settly_api.expenses.repository.ExpenseSplitRepository;

@Service
public class ExpenseService {

  private final ExpenseRepository expenseRepository;
  private final ExpenseItemRepository expenseItemRepository;
  private final ExpenseItemSplitRepository expenseItemSplitRepository;
  private final ExpenseMapper expenseMapper;
  private final UserRepository userRepository;
  private final ExpenseSplitRepository expenseSplitRepository;
  private final ExpenseAccessService expenseAccessService;

  public ExpenseService(
      ExpenseRepository expenseRepository,
      ExpenseItemRepository expenseItemRepository,
      ExpenseItemSplitRepository expenseItemSplitRepository,
      ExpenseMapper expenseMapper,
      UserRepository userRepository,
      ExpenseSplitRepository expenseSplitRepository,
      ExpenseAccessService expenseAccessService) {
    this.expenseRepository = expenseRepository;
    this.expenseItemRepository = expenseItemRepository;
    this.expenseItemSplitRepository = expenseItemSplitRepository;
    this.expenseMapper = expenseMapper;
    this.userRepository = userRepository;
    this.expenseSplitRepository = expenseSplitRepository;
    this.expenseAccessService = expenseAccessService;
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
    if (expenseAccessService.hasNoAccessToExpense(expenseId, userId)) {
      throw new ResourceNotFoundException("Expense does not exist");
    }

    return expenseItemRepository.findByExpenseId(expenseId).stream()
        .map(expenseMapper::toExpenseItemResponse)
        .toList();
  }

  public List<ExpenseItemSplitUserResponse> getItemSplitUsers(UUID itemId, UUID userId) {
    ExpenseItem item =
        expenseItemRepository
            .findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Item does not exist"));

    UUID expenseId = item.getExpense().getId();
    if (expenseAccessService.hasNoAccessToExpense(expenseId, userId)) {
      throw new ResourceNotFoundException("Item does not exist");
    }

    return expenseItemSplitRepository.findUsersByExpenseItemId(itemId).stream()
        .map(
            splitUser ->
                new ExpenseItemSplitUserResponse(
                    splitUser.getId(),
                    splitUser.getUsername(),
                    splitUser.getDisplayName(),
                    splitUser.getAvatarUrl()))
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

  public ExpenseUserShareResponse getUserShare(UUID expenseId, UUID userId) {
    if (expenseAccessService.hasNoAccessToExpense(expenseId, userId)) {
      throw new ResourceNotFoundException("Expense does not exist");
    }

    ExpenseSplit userSplit =
        expenseSplitRepository.findByExpenseId(expenseId).stream()
            .filter(split -> split.getUser().getId().equals(userId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Expense does not exist"));

    Expense expense =
        expenseRepository
            .findById(expenseId)
            .orElseThrow(() -> new ResourceNotFoundException("Expense does not exist"));

    return new ExpenseUserShareResponse(userSplit.getAmount().doubleValue(), expense.getCurrency());
  }
}
