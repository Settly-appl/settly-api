package pl.settly.settly_api.expenses.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.common.exception.ResourceNotFoundException;
import pl.settly.settly_api.expenses.dto.*;
import pl.settly.settly_api.expenses.model.Expense;
import pl.settly.settly_api.expenses.model.ExpenseItem;
import pl.settly.settly_api.expenses.model.ExpenseItemSplit;
import pl.settly.settly_api.expenses.model.ExpenseSplit;
import pl.settly.settly_api.expenses.model.ExpenseSplitType;
import pl.settly.settly_api.expenses.repository.ExpenseItemRepository;
import pl.settly.settly_api.expenses.repository.ExpenseItemSplitRepository;
import pl.settly.settly_api.expenses.repository.ExpenseRepository;
import pl.settly.settly_api.expenses.repository.ExpenseSplitRepository;
import pl.settly.settly_api.friendships.service.FriendshipService;

@Service
public class ExpenseSplitService {

  private final FriendshipService friendshipService;
  private final ExpenseSplitRepository expenseSplitRepository;
  private final ExpenseRepository expenseRepository;
  private final ExpenseItemRepository expenseItemRepository;
  private final ExpenseItemSplitRepository expenseItemSplitRepository;
  private final UserRepository userRepository;
  private final ExpenseAccessService expenseAccessService;

  private final ExpenseMapper expenseMapper;

  public ExpenseSplitService(
      FriendshipService friendshipService,
      ExpenseSplitRepository expenseSplitRepository,
      ExpenseRepository expenseRepository,
      ExpenseItemRepository expenseItemRepository,
      ExpenseItemSplitRepository expenseItemSplitRepository,
      UserRepository userRepository,
      ExpenseAccessService expenseAccessService,
      ExpenseMapper expenseMapper) {
    this.friendshipService = friendshipService;
    this.expenseSplitRepository = expenseSplitRepository;
    this.expenseRepository = expenseRepository;
    this.expenseItemRepository = expenseItemRepository;
    this.expenseItemSplitRepository = expenseItemSplitRepository;
    this.expenseMapper = expenseMapper;
    this.userRepository = userRepository;
    this.expenseAccessService = expenseAccessService;
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
        if (expense.getTotalAmount() == null) {
          throw new IllegalArgumentException("Total amount is required for EQUAL split");
        }
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
                participant ->
                    expenseSplits.add(
                        ExpenseSplit.builder()
                            .expense(expense)
                            .user(userRepository.getReferenceById(participant.friendId()))
                            .expenseSplitType(ExpenseSplitType.EQUAL)
                            .amount(equalAmount)
                            .settled(false)
                            .build()));

        break;

      case CUSTOM:
        if (expense.getTotalAmount() == null) {
          throw new IllegalArgumentException("Total amount is required for CUSTOM split");
        }
        BigDecimal payerAmount =
            createExpenseSplitRequest.participants().stream()
                .peek(
                    participant -> {
                      if (participant.amount() == null) {
                        throw new IllegalArgumentException(
                            "Amount is required for each participant in a custom split");
                      }
                    })
                .map(SplitParticipant::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal ownerAmount = expense.getTotalAmount().subtract(payerAmount);

        if (ownerAmount.compareTo(BigDecimal.ZERO) <= 0) {
          throw new IllegalArgumentException(
              "Participants' amounts must be less than the total expense amount");
        }

        expenseSplits.add(
            ExpenseSplit.builder()
                .expense(expense)
                .user(expense.getUser())
                .expenseSplitType(ExpenseSplitType.CUSTOM)
                .amount(ownerAmount)
                .settled(true)
                .build());

        createExpenseSplitRequest
            .participants()
            .forEach(
                participant ->
                    expenseSplits.add(
                        ExpenseSplit.builder()
                            .expense(expense)
                            .user(userRepository.getReferenceById(participant.friendId()))
                            .expenseSplitType(ExpenseSplitType.CUSTOM)
                            .amount(participant.amount())
                            .settled(false)
                            .build()));

        break;

      case BY_ITEM:
        if (createExpenseSplitRequest.itemAssignments() == null
            || createExpenseSplitRequest.itemAssignments().isEmpty()) {
          throw new IllegalArgumentException("Item assignments are required for BY_ITEM split");
        }

        List<ExpenseItem> expenseItems = expenseItemRepository.findByExpenseId(expenseId);
        if (expenseItems.isEmpty()) {
          throw new IllegalArgumentException("Expense has no items to split");
        }

        Map<UUID, ExpenseItem> itemMap =
            expenseItems.stream().collect(Collectors.toMap(ExpenseItem::getId, item -> item));

        Set<UUID> assignedItemIds =
            createExpenseSplitRequest.itemAssignments().stream()
                .map(ItemSplitAssignment::expenseItemId)
                .collect(Collectors.toSet());

        if (!assignedItemIds.equals(itemMap.keySet())) {
          throw new IllegalArgumentException("All expense items must be assigned");
        }

        Set<UUID> participantIds =
            createExpenseSplitRequest.participants().stream()
                .map(SplitParticipant::friendId)
                .collect(Collectors.toSet());

        Map<UUID, BigDecimal> userTotals = new HashMap<>();
        List<ExpenseItemSplit> itemSplits = new ArrayList<>();

        for (var assignment : createExpenseSplitRequest.itemAssignments()) {
          ExpenseItem item = itemMap.get(assignment.expenseItemId());

          for (UUID assignedUserId : assignment.userIds()) {
            if (!assignedUserId.equals(userId) && !participantIds.contains(assignedUserId)) {
              throw new IllegalArgumentException(
                  "User " + assignedUserId + " is not a participant in this split");
            }
          }

          BigDecimal itemTotal =
              item.getPrice().multiply(item.getQuantity()).setScale(2, RoundingMode.HALF_UP);
          int assigneeCount = assignment.userIds().size();
          BigDecimal perPerson =
              itemTotal.divide(BigDecimal.valueOf(assigneeCount), 2, RoundingMode.HALF_UP);
          BigDecimal itemRemainder =
              itemTotal.subtract(perPerson.multiply(BigDecimal.valueOf(assigneeCount)));

          boolean first = true;
          for (UUID assignedUserId : assignment.userIds()) {
            BigDecimal amount = first ? perPerson.add(itemRemainder) : perPerson;
            first = false;

            userTotals.merge(assignedUserId, amount, BigDecimal::add);

            itemSplits.add(
                ExpenseItemSplit.builder()
                    .expenseItem(item)
                    .user(userRepository.getReferenceById(assignedUserId))
                    .build());
          }
        }

        if (!userTotals.containsKey(userId)) {
          userTotals.put(userId, BigDecimal.ZERO);
        }

        for (Map.Entry<UUID, BigDecimal> entry : userTotals.entrySet()) {
          expenseSplits.add(
              ExpenseSplit.builder()
                  .expense(expense)
                  .user(userRepository.getReferenceById(entry.getKey()))
                  .expenseSplitType(ExpenseSplitType.BY_ITEM)
                  .amount(entry.getValue())
                  .settled(entry.getKey().equals(userId))
                  .build());
        }

        expenseItemSplitRepository.saveAll(itemSplits);

        break;

      default:
        throw new IllegalArgumentException(
            "Unsupported split type: " + createExpenseSplitRequest.expenseSplitType());
    }

    List<ExpenseSplit> savedSplits = expenseSplitRepository.saveAll(expenseSplits);

    return savedSplits.stream().map(expenseMapper::toExpenseSplitResponse).toList();
  }

  public List<ExpenseSplitResponse> getSplitsForExpense(UUID expenseId, UUID userId) {
    if (expenseAccessService.hasNoAccessToExpense(expenseId, userId)) {
      throw new ResourceNotFoundException("Expense does not exist");
    }

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

    expenseItemSplitRepository.deleteAll(
        expenseItemSplitRepository.findByExpenseItemExpenseId(expenseId));
    expenseSplitRepository.deleteAll(splits);
  }

  @Transactional
  public ExpenseSplitResponse settleSplit(UUID expenseId, UUID splitId, UUID userId) {
    expenseRepository
        .findByIdAndUser_Id(expenseId, userId)
        .orElseThrow(() -> new ResourceNotFoundException("Expense does not exist"));

    ExpenseSplit split =
        expenseSplitRepository
            .findById(splitId)
            .filter(s -> s.getExpense().getId().equals(expenseId))
            .orElseThrow(() -> new ResourceNotFoundException("Split does not exist"));

    if (split.getUser().getId().equals(userId)) {
      throw new IllegalArgumentException("Cannot settle your own split");
    }

    if (split.getSettled()) {
      throw new IllegalArgumentException("Split is already settled");
    }

    split.setSettled(true);
    split.setSettledAt(Instant.now());

    return expenseMapper.toExpenseSplitResponse(expenseSplitRepository.save(split));
  }

  public List<ExpenseSplitResponse> getUnsettledSplits(UUID userId) {
    return expenseSplitRepository.findByUserIdAndSettledFalse(userId).stream()
        .map(expenseMapper::toExpenseSplitResponse)
        .toList();
  }
}
