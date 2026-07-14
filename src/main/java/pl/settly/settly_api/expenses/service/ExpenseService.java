package pl.settly.settly_api.expenses.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import pl.settly.settly_api.projects.model.Project;
import pl.settly.settly_api.projects.repository.ProjectRepository;
import pl.settly.settly_api.projects.service.ProjectAccessService;

@Service
public class ExpenseService {

  private final ExpenseRepository expenseRepository;
  private final ExpenseItemRepository expenseItemRepository;
  private final ExpenseItemSplitRepository expenseItemSplitRepository;
  private final ExpenseMapper expenseMapper;
  private final UserRepository userRepository;
  private final ExpenseSplitRepository expenseSplitRepository;
  private final ExpenseAccessService expenseAccessService;
  private final ProjectRepository projectRepository;
  private final ProjectAccessService projectAccessService;

  public ExpenseService(
      ExpenseRepository expenseRepository,
      ExpenseItemRepository expenseItemRepository,
      ExpenseItemSplitRepository expenseItemSplitRepository,
      ExpenseMapper expenseMapper,
      UserRepository userRepository,
      ExpenseSplitRepository expenseSplitRepository,
      ExpenseAccessService expenseAccessService,
      ProjectRepository projectRepository,
      ProjectAccessService projectAccessService) {
    this.expenseRepository = expenseRepository;
    this.expenseItemRepository = expenseItemRepository;
    this.expenseItemSplitRepository = expenseItemSplitRepository;
    this.expenseMapper = expenseMapper;
    this.userRepository = userRepository;
    this.expenseSplitRepository = expenseSplitRepository;
    this.expenseAccessService = expenseAccessService;
    this.projectRepository = projectRepository;
    this.projectAccessService = projectAccessService;
  }

  public ExpenseResponse createExpense(CreateExpenseRequest request, UUID userId) {
    Expense expense = expenseMapper.toExpense(request);
    User user = userRepository.getReferenceById(userId);
    expense.setUser(user);
    expense.setProject(resolveProject(request.projectId(), userId));
    Expense savedExpense = expenseRepository.save(expense);
    // A brand-new expense has no splits yet.
    return withSettlement(savedExpense, List.of(), userId);
  }

  /** Resolves the project for an expense, ensuring the user is a member of it. */
  private Project resolveProject(UUID projectId, UUID userId) {
    if (projectId == null) {
      return null;
    }
    if (!projectAccessService.isMember(projectId, userId)) {
      throw new IllegalArgumentException("You are not a member of this project");
    }
    return projectRepository.getReferenceById(projectId);
  }

  public ExpenseResponse getExpense(UUID expenseId, UUID userId) {
    // Owner or a split participant may view the expense (matches list/share visibility).
    if (expenseAccessService.hasNoAccessToExpense(expenseId, userId)) {
      throw new ResourceNotFoundException("Expense does not exist");
    }
    Expense expense =
        expenseRepository
            .findById(expenseId)
            .orElseThrow(() -> new ResourceNotFoundException("Expense does not exist"));
    return withSettlement(expense, expenseSplitRepository.findByExpenseId(expenseId), userId);
  }

  public Page<ExpenseResponse> searchExpenses(
      Pageable pageable, String category, UUID projectId, UUID userId) {
    // Scoped to a project, membership is what grants sight of the whole ledger —
    // so it has to be checked here, not left to the query.
    if (projectId != null && !projectAccessService.isMember(projectId, userId)) {
      throw new ResourceNotFoundException("Project does not exist");
    }

    Page<Expense> expensesPage =
        expenseRepository.findExpenses(userId, category, projectId, pageable);

    // One extra query for the whole page — never a per-expense lookup.
    Map<UUID, List<ExpenseSplit>> splitsByExpense = loadSplits(expensesPage.getContent());

    return expensesPage.map(
        expense ->
            withSettlement(
                expense, splitsByExpense.getOrDefault(expense.getId(), List.of()), userId));
  }

  private Map<UUID, List<ExpenseSplit>> loadSplits(List<Expense> expenses) {
    if (expenses.isEmpty()) {
      return Map.of();
    }
    List<UUID> ids = expenses.stream().map(Expense::getId).toList();
    return expenseSplitRepository.findByExpenseIdIn(ids).stream()
        .collect(Collectors.groupingBy(split -> split.getExpense().getId()));
  }

  /**
   * Attaches the viewer-relative settlement summary. The owner's own split row is excluded — it is
   * always settled and nobody owes it — so an expense with no participants reads as "nothing to
   * settle" (splitCount 0).
   */
  private ExpenseResponse withSettlement(
      Expense expense, List<ExpenseSplit> splits, UUID viewerId) {
    UUID ownerId = expense.getUser().getId();
    List<ExpenseSplit> participants =
        splits.stream().filter(s -> !s.getUser().getId().equals(ownerId)).toList();

    int splitCount = participants.size();
    int settledCount =
        (int) participants.stream().filter(s -> Boolean.TRUE.equals(s.getSettled())).count();

    boolean isOwner = ownerId.equals(viewerId);
    ExpenseSplit ownShare =
        participants.stream()
            .filter(s -> s.getUser().getId().equals(viewerId))
            .findFirst()
            .orElse(null);

    // A project member can see expenses between other members (the shared ledger), but
    // has no share in them and cannot settle them — the backend would refuse, so don't
    // let the UI offer it, and don't report "unsettled" as if it were their debt.
    boolean canSettle = splitCount > 0 && (isOwner || ownShare != null);

    boolean settled;
    if (splitCount == 0) {
      settled = false; // personal expense — nothing to settle
    } else if (isOwner || ownShare == null) {
      settled = settledCount == splitCount; // everyone has settled
    } else {
      settled = Boolean.TRUE.equals(ownShare.getSettled()); // the viewer's own share
    }

    // "I paid" claims awaiting the owner's confirmation (settled shares carry none).
    int declaredCount =
        (int) participants.stream().filter(s -> Boolean.TRUE.equals(s.getDeclaredPaid())).count();
    boolean declared = ownShare != null && Boolean.TRUE.equals(ownShare.getDeclaredPaid());

    return expenseMapper
        .toExpenseResponse(expense)
        .withSettlement(splitCount, settledCount, settled, canSettle, declaredCount, declared);
  }

  public ExpenseResponse updateExpense(UUID expenseId, UUID userId, CreateExpenseRequest request) {
    Expense expense =
        expenseRepository
            .findByIdAndUser_Id(expenseId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Expense does not exist"));
    expense.setShop(request.shop());
    expense.setNote(request.note());
    expense.setCategory(request.category());
    expense.setCurrency(request.currency());
    expense.setTotalAmount(request.totalAmount());
    expense.setDate(request.date());
    expense.setProject(resolveProject(request.projectId(), userId));

    Expense saved = expenseRepository.save(expense);
    return withSettlement(saved, expenseSplitRepository.findByExpenseId(expenseId), userId);
  }

  @Transactional
  public void deleteExpense(UUID expenseId, UUID userId) {
    Expense expense =
        expenseRepository
            .findByIdAndUser_Id(expenseId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Expense does not exist"));

    // Remove children first — item splits, splits, then items — otherwise the
    // foreign keys block deleting the expense.
    expenseItemSplitRepository.deleteAll(
        expenseItemSplitRepository.findByExpenseItemExpenseId(expenseId));
    expenseSplitRepository.deleteAll(expenseSplitRepository.findByExpenseId(expenseId));
    expenseItemRepository.deleteAll(expenseItemRepository.findByExpenseId(expenseId));

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

    // Return each assignee's stored share, so an unequal split of one product survives a round-trip
    // (the edit form prefills from this — recomputing it as an equal division would silently
    // discard the custom amounts).
    return expenseItemSplitRepository.findWithUserByExpenseItemId(itemId).stream()
        .map(
            split -> {
              User splitUser = split.getUser();
              return new ExpenseItemSplitUserResponse(
                  splitUser.getId(),
                  splitUser.getUsername(),
                  splitUser.getDisplayName(),
                  splitUser.getAvatarUrl(),
                  split.getAmount());
            })
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
