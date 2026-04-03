package pl.settly.settly_api.expenses.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.common.exception.ResourceNotFoundException;
import pl.settly.settly_api.expenses.dto.CreateExpenseSplitRequest;
import pl.settly.settly_api.expenses.dto.ExpenseMapper;
import pl.settly.settly_api.expenses.dto.ExpenseSplitResponse;
import pl.settly.settly_api.expenses.model.Expense;
import pl.settly.settly_api.expenses.model.ExpenseSplit;
import pl.settly.settly_api.expenses.model.ExpenseSplitType;
import pl.settly.settly_api.expenses.repository.ExpenseRepository;
import pl.settly.settly_api.expenses.repository.ExpenseSplitRepository;
import pl.settly.settly_api.friendships.service.FriendshipService;

@Service
public class ExpenseSplitService {

  private final FriendshipService friendshipService;
  private final ExpenseSplitRepository expenseSplitRepository;
  private final ExpenseRepository expenseRepository;
  private final UserRepository userRepository;

  private final ExpenseMapper expenseMapper;

  public ExpenseSplitService(
      FriendshipService friendshipService,
      ExpenseSplitRepository expenseSplitRepository,
      ExpenseRepository expenseRepository,
      UserRepository userRepository,
      ExpenseMapper expenseMapper) {
    this.friendshipService = friendshipService;
    this.expenseSplitRepository = expenseSplitRepository;
    this.expenseRepository = expenseRepository;
    this.expenseMapper = expenseMapper;
    this.userRepository = userRepository;
  }

  @Transactional
  public List<ExpenseSplitResponse> createSplit(
      UUID expenseId, CreateExpenseSplitRequest createExpenseSplitRequest, UUID userId) {

    Expense expense =
        expenseRepository
            .findByIdAndUser_Id(expenseId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Expense does not exist"));

    if (expenseSplitRepository.existsByExpenseId(expenseId)) {
      throw new IllegalArgumentException("Expense is already split");
    }

    createExpenseSplitRequest
        .participants()
        .forEach(
            participant -> {
              if (!friendshipService.areFriends(userId, participant.friendId())) {
                throw new IllegalArgumentException(
                    "User " + participant.friendId() + " is not requesting user's friend.");
              }
            });

    List<ExpenseSplit> expenseSplits = new ArrayList<>();
    int totalParticipants = createExpenseSplitRequest.participants().size() + 1;

    switch (createExpenseSplitRequest.expenseSplitType()) {
      case EQUAL:
        BigDecimal equalAmount =
            expense
                .getTotalAmount()
                .divide(BigDecimal.valueOf(totalParticipants), 2, RoundingMode.HALF_UP);
        BigDecimal remainder =
            expense
                .getTotalAmount()
                .subtract(equalAmount.multiply(BigDecimal.valueOf(totalParticipants)));

        expenseSplits.add(
            ExpenseSplit.builder()
                .expense(expense)
                .user(expense.getUser())
                .expenseSplitType(ExpenseSplitType.EQUAL)
                .amount(equalAmount.add(remainder))
                .settled(true)
                .build());

        createExpenseSplitRequest
            .participants()
            .forEach(
                participant -> {
                  expenseSplits.add(
                      ExpenseSplit.builder()
                          .expense(expense)
                          .user(userRepository.getReferenceById(participant.friendId()))
                          .expenseSplitType(ExpenseSplitType.EQUAL)
                          .amount(equalAmount)
                          .settled(false)
                          .build());
                });

        break;

      default:
        throw new IllegalArgumentException(
            "Unsupported split type: " + createExpenseSplitRequest.expenseSplitType());
    }

    List<ExpenseSplit> savedSplits = expenseSplitRepository.saveAll(expenseSplits);

    return savedSplits.stream().map(expenseMapper::toExpenseSplitResponse).toList();
  }

  public List<ExpenseSplitResponse> getSplitsForExpense(UUID expenseId, UUID userId) {
    expenseRepository
        .findByIdAndUser_Id(expenseId, userId)
        .orElseThrow(() -> new ResourceNotFoundException("Expense does not exist"));

    return expenseSplitRepository.findByExpenseId(expenseId).stream()
        .map(expenseMapper::toExpenseSplitResponse)
        .toList();
  }

  @Transactional
  public void deleteAllSplits(UUID expenseId, UUID userId) {
    expenseRepository
        .findByIdAndUser_Id(expenseId, userId)
        .orElseThrow(() -> new ResourceNotFoundException("Expense does not exist"));

    List<ExpenseSplit> splits = expenseSplitRepository.findByExpenseId(expenseId);

    if (splits.isEmpty()) {
      throw new ResourceNotFoundException("No splits found for this expense");
    }

    boolean hasSettledNonPayer =
        splits.stream().anyMatch(s -> s.getSettled() && !s.getUser().getId().equals(userId));

    if (hasSettledNonPayer) {
      throw new IllegalArgumentException(
          "Cannot delete splits — some participants have already settled");
    }

    expenseSplitRepository.deleteAll(splits);
  }

  public List<ExpenseSplitResponse> getUnsettledSplits(UUID userId) {
    return expenseSplitRepository.findByUserIdAndSettledFalse(userId).stream()
        .map(expenseMapper::toExpenseSplitResponse)
        .toList();
  }
}
