package pl.settly.settly_api.expenses.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.settly.settly_api.auth.user.model.User;
import pl.settly.settly_api.auth.user.repository.UserRepository;
import pl.settly.settly_api.common.exception.ResourceNotFoundException;
import pl.settly.settly_api.common.money.CurrencyConversionService;
import pl.settly.settly_api.common.exception.SettlementLockedException;
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
import pl.settly.settly_api.notifications.event.ExpensePaymentDeclaredEvent;
import pl.settly.settly_api.notifications.event.ExpenseSettlementChangedEvent;
import pl.settly.settly_api.notifications.event.ExpenseSplitCreatedEvent;

@Service
public class ExpenseSplitService {

  private final FriendshipService friendshipService;
  private final ExpenseSplitRepository expenseSplitRepository;
  private final ExpenseRepository expenseRepository;
  private final ExpenseItemRepository expenseItemRepository;
  private final ExpenseItemSplitRepository expenseItemSplitRepository;
  private final UserRepository userRepository;
  private final ExpenseAccessService expenseAccessService;
  private final ApplicationEventPublisher eventPublisher;
  private final CurrencyConversionService currencyConversionService;

  private final ExpenseMapper expenseMapper;

  public ExpenseSplitService(
      FriendshipService friendshipService,
      ExpenseSplitRepository expenseSplitRepository,
      ExpenseRepository expenseRepository,
      ExpenseItemRepository expenseItemRepository,
      ExpenseItemSplitRepository expenseItemSplitRepository,
      UserRepository userRepository,
      ExpenseAccessService expenseAccessService,
      ApplicationEventPublisher eventPublisher,
      CurrencyConversionService currencyConversionService,
      ExpenseMapper expenseMapper) {
    this.friendshipService = friendshipService;
    this.expenseSplitRepository = expenseSplitRepository;
    this.expenseRepository = expenseRepository;
    this.expenseItemRepository = expenseItemRepository;
    this.expenseItemSplitRepository = expenseItemSplitRepository;
    this.expenseMapper = expenseMapper;
    this.userRepository = userRepository;
    this.expenseAccessService = expenseAccessService;
    this.eventPublisher = eventPublisher;
    this.currencyConversionService = currencyConversionService;
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

    requireSharedBaseCurrency(expense, createExpenseSplitRequest);

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

        // The creator may owe exactly 0 (they paid for others and take no share),
        // but participants still cannot owe more than the total.
        if (ownerAmount.compareTo(BigDecimal.ZERO) < 0) {
          throw new IllegalArgumentException(
              "Participants' amounts cannot exceed the total expense amount");
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

          List<UUID> assignedUserIds = assignment.assignedUserIds();
          if (assignedUserIds.isEmpty()) {
            throw new IllegalArgumentException("Each item must be assigned to at least one person");
          }
          for (UUID assignedUserId : assignedUserIds) {
            if (!assignedUserId.equals(userId) && !participantIds.contains(assignedUserId)) {
              throw new IllegalArgumentException(
                  "User " + assignedUserId + " is not a participant in this split");
            }
          }

          BigDecimal itemTotal =
              item.getPrice().multiply(item.getQuantity()).setScale(2, RoundingMode.HALF_UP);

          // Either the caller gave each person's share explicitly (unequal split of the same
          // product), or we divide the item equally and absorb the rounding remainder in the
          // first share so the parts add up to the item total exactly.
          Map<UUID, BigDecimal> shareByUser =
              assignment.hasExplicitShares()
                  ? explicitShares(assignment, itemTotal, item)
                  : equalShares(assignedUserIds, itemTotal);

          for (Map.Entry<UUID, BigDecimal> share : shareByUser.entrySet()) {
            userTotals.merge(share.getKey(), share.getValue(), BigDecimal::add);

            itemSplits.add(
                ExpenseItemSplit.builder()
                    .expenseItem(item)
                    .user(userRepository.getReferenceById(share.getKey()))
                    .amount(share.getValue())
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

    applyBaseAmounts(expense, expenseSplits, userId);

    List<ExpenseSplit> savedSplits = expenseSplitRepository.saveAll(expenseSplits);

    List<UUID> participantIds =
        createExpenseSplitRequest.participants().stream().map(SplitParticipant::friendId).toList();
    if (!participantIds.isEmpty()) {
      eventPublisher.publishEvent(
          new ExpenseSplitCreatedEvent(userId, participantIds, expense.getId(), expense.getShop()));
    }

    return savedSplits.stream().map(expenseMapper::toExpenseSplitResponse).toList();
  }

  @Transactional(readOnly = true)
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
    return setSplitSettled(expenseId, splitId, userId, true);
  }

  // Must be transactional: the settlement event is published to an AFTER_COMMIT
  // listener, which is silently skipped when there is no transaction to commit.
  @Transactional
  public ExpenseSplitResponse unsettleSplit(UUID expenseId, UUID splitId, UUID userId) {
    return setSplitSettled(expenseId, splitId, userId, false);
  }

  /**
   * Marks one split settled/unsettled — but what that means depends on who asks. Settled is the
   * owner's word alone (the creditor confirming money arrived); the split's own user "settling"
   * only <em>declares</em> they paid, a claim surfaced to the owner as a suggestion to double-check
   * and confirm. Declarations do not affect balances. Idempotent — a stray double swipe doesn't
   * surface a failure.
   */
  private ExpenseSplitResponse setSplitSettled(
      UUID expenseId, UUID splitId, UUID userId, boolean settled) {
    Expense expense =
        expenseRepository
            .findById(expenseId)
            .orElseThrow(() -> new ResourceNotFoundException("Expense does not exist"));

    ExpenseSplit split =
        expenseSplitRepository
            .findById(splitId)
            .filter(s -> s.getExpense().getId().equals(expenseId))
            .orElseThrow(() -> new ResourceNotFoundException("Split does not exist"));

    UUID ownerId = expense.getUser().getId();

    // The owner's own row is their own share — it is settled by definition and never toggled.
    if (split.getUser().getId().equals(ownerId)) {
      throw new IllegalArgumentException("The owner's own share is not settleable");
    }

    boolean isOwner = ownerId.equals(userId);
    boolean isOwnSplit = split.getUser().getId().equals(userId);
    if (!isOwner && !isOwnSplit) {
      throw new ResourceNotFoundException("Split does not exist");
    }

    if (isOwner) {
      applySettled(split, settled);
      ExpenseSplitResponse response =
          expenseMapper.toExpenseSplitResponse(expenseSplitRepository.save(split));
      publishSettlementChanged(expense, userId, List.of(split.getUser().getId()), settled);
      return response;
    }

    // The participant: declare "I paid" / retract the declaration.
    boolean changed = applyDeclared(split, settled);
    ExpenseSplitResponse response =
        expenseMapper.toExpenseSplitResponse(expenseSplitRepository.save(split));
    // Only a real state change reaches the owner — a repeated swipe must not re-notify.
    if (changed) {
      publishPaymentDeclared(expense, userId, ownerId, settled);
    }
    return response;
  }

  private void publishSettlementChanged(
      Expense expense, UUID actorId, List<UUID> recipientIds, boolean settled) {
    if (recipientIds.isEmpty()) {
      return;
    }
    eventPublisher.publishEvent(
        new ExpenseSettlementChangedEvent(
            actorId, recipientIds, expense.getId(), expense.getShop(), settled));
  }

  private void publishPaymentDeclared(
      Expense expense, UUID actorId, UUID ownerId, boolean declared) {
    eventPublisher.publishEvent(
        new ExpensePaymentDeclaredEvent(
            actorId, ownerId, expense.getId(), expense.getShop(), declared));
  }

  /**
   * Settles (or unsettles) a whole expense in one go. The owner clears every participant's split; a
   * participant only <em>declares</em> their own share paid (see {@link #setSplitSettled}) — the
   * share stays unsettled until the owner confirms. Idempotent.
   */
  @Transactional
  public List<ExpenseSplitResponse> setExpenseSettled(
      UUID expenseId, UUID userId, boolean settled) {
    Expense expense =
        expenseRepository
            .findById(expenseId)
            .orElseThrow(() -> new ResourceNotFoundException("Expense does not exist"));

    if (expenseAccessService.hasNoAccessToExpense(expenseId, userId)) {
      throw new ResourceNotFoundException("Expense does not exist");
    }

    UUID ownerId = expense.getUser().getId();
    boolean isOwner = ownerId.equals(userId);

    List<ExpenseSplit> targets =
        expenseSplitRepository.findByExpenseId(expenseId).stream()
            // never the owner's own row; and a participant may only touch their own
            .filter(s -> !s.getUser().getId().equals(ownerId))
            .filter(s -> isOwner || s.getUser().getId().equals(userId))
            .toList();

    if (targets.isEmpty()) {
      throw new ResourceNotFoundException("Nothing to settle on this expense");
    }

    if (isOwner) {
      targets.forEach(s -> applySettled(s, settled));
      expenseSplitRepository.saveAll(targets);
      List<UUID> recipients = targets.stream().map(s -> s.getUser().getId()).distinct().toList();
      publishSettlementChanged(expense, userId, recipients, settled);
    } else {
      boolean changed = false;
      for (ExpenseSplit s : targets) {
        changed |= applyDeclared(s, settled);
      }
      expenseSplitRepository.saveAll(targets);
      if (changed) {
        publishPaymentDeclared(expense, userId, ownerId, settled);
      }
    }

    return targets.stream().map(expenseMapper::toExpenseSplitResponse).toList();
  }

  /**
   * Splits an item equally between its assignees. The first share absorbs the rounding remainder so
   * the parts always add up to the item total exactly.
   */
  private static Map<UUID, BigDecimal> equalShares(List<UUID> userIds, BigDecimal itemTotal) {
    int count = userIds.size();
    BigDecimal perPerson = itemTotal.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    BigDecimal remainder = itemTotal.subtract(perPerson.multiply(BigDecimal.valueOf(count)));

    Map<UUID, BigDecimal> shares = new LinkedHashMap<>();
    boolean first = true;
    for (UUID id : userIds) {
      shares.merge(id, first ? perPerson.add(remainder) : perPerson, BigDecimal::add);
      first = false;
    }
    return shares;
  }

  /**
   * Uses the caller's explicit per-person amounts for an item. They must add up to the item total —
   * otherwise the sum of everyone's shares would silently disagree with what the expense says the
   * product cost.
   */
  private static Map<UUID, BigDecimal> explicitShares(
      ItemSplitAssignment assignment, BigDecimal itemTotal, ExpenseItem item) {
    Map<UUID, BigDecimal> shares = new LinkedHashMap<>();
    for (ItemShare share : assignment.shares()) {
      shares.merge(share.userId(), share.amount(), BigDecimal::add);
    }

    BigDecimal sum =
        shares.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

    if (sum.compareTo(itemTotal) != 0) {
      throw new IllegalArgumentException(
          "Shares for item '"
              + item.getName()
              + "' add up to "
              + sum
              + " but the item costs "
              + itemTotal);
    }
    return shares;
  }

  private void applySettled(ExpenseSplit split, boolean settled) {
    // A split cleared by a bulk settle-up must not be unsettled on its own: the money really did
    // change hands, so resurrecting the balance here would contradict the recorded payment. The
    // whole settlement has to be reversed instead (DELETE /debts/{id}).
    if (!settled && split.getSettledByDebt() != null) {
      throw new SettlementLockedException(
          "This share was settled as part of a settle-up. Undo that settlement instead.");
    }
    split.setSettled(settled);
    split.setSettledAt(settled ? Instant.now() : null);
    if (settled) {
      // The owner confirming resolves the participant's "I paid" claim.
      split.setDeclaredPaid(false);
      split.setDeclaredAt(null);
    }
  }

  /**
   * Records or retracts the participant's "I paid" claim. A share the owner already settled is left
   * alone — the fact outranks the claim, and a participant can no longer flip it back. Returns
   * whether anything actually changed, so callers don't notify the owner about a repeated swipe.
   */
  private boolean applyDeclared(ExpenseSplit split, boolean declared) {
    if (Boolean.TRUE.equals(split.getSettled())) {
      return false;
    }
    if (declared == Boolean.TRUE.equals(split.getDeclaredPaid())) {
      return false;
    }
    split.setDeclaredPaid(declared);
    split.setDeclaredAt(declared ? Instant.now() : null);
    return true;
  }

  public List<ExpenseSplitResponse> getUnsettledSplits(UUID userId) {
    return expenseSplitRepository.findByUserIdAndSettledFalse(userId).stream()
        .map(expenseMapper::toExpenseSplitResponse)
        .toList();
  }

  /**
   * Values each share in the expense owner's base currency, which is what every balance query sums
   * -- adding a pound share to a zloty share would otherwise produce a number that is not money.
   *
   * <p>Converting each share on its own can drift a grosz from the expense's converted total, so
   * the shares are apportioned against it and the difference goes to the payer, exactly as the odd
   * grosz of the split itself already does.
   */
  private void applyBaseAmounts(Expense expense, List<ExpenseSplit> splits, UUID payerId) {
    int payerIndex = 0;
    for (int i = 0; i < splits.size(); i++) {
      if (splits.get(i).getUser().getId().equals(payerId)) {
        payerIndex = i;
        break;
      }
    }

    List<BigDecimal> baseAmounts =
        currencyConversionService.apportionToBase(
            splits.stream().map(ExpenseSplit::getAmount).toList(),
            expense.getRateToBase(),
            expense.getBaseAmount(),
            payerIndex);

    for (int i = 0; i < splits.size(); i++) {
      splits.get(i).setBaseAmount(baseAmounts.get(i));
    }
  }

  /**
   * Refuses a split between people who keep their books in different base currencies.
   *
   * <p>A share is stored converted into the expense owner's base, and balances net those shares
   * across everyone. If two people disagreed about what base means, the netting would add euro to
   * zloty and produce a figure that is not any amount of money -- and unlike a wrong rate, nothing
   * downstream could detect it. Converting between the two bases instead would need a second rate
   * nobody has supplied, so this fails loudly at the moment the shared obligation is created.
   */
  private void requireSharedBaseCurrency(Expense expense, CreateExpenseSplitRequest request) {
    String ownerBase = expense.getBaseCurrency();
    List<UUID> participantIds =
        request.participants().stream().map(SplitParticipant::friendId).toList();

    for (User participant : userRepository.findAllById(participantIds)) {
      String participantBase = participant.getBaseCurrency();
      if (participantBase != null && !participantBase.equals(ownerBase)) {
        throw new IllegalArgumentException(
            "Cannot split with "
                + participant.getUsername()
                + ": their base currency is "
                + participantBase
                + " and yours is "
                + ownerBase
                + ". Balances between you would not add up.");
      }
    }
  }
}
