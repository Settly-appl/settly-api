package pl.settly.settly_api.expenses.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import pl.settly.settly_api.expenses.repository.ExpenseRepository;
import pl.settly.settly_api.expenses.repository.ExpenseSplitRepository;

@Service
public class ExpenseAccessService {

  private final ExpenseRepository expenseRepository;
  private final ExpenseSplitRepository expenseSplitRepository;

  public ExpenseAccessService(
      ExpenseRepository expenseRepository, ExpenseSplitRepository expenseSplitRepository) {
    this.expenseRepository = expenseRepository;
    this.expenseSplitRepository = expenseSplitRepository;
  }

  public boolean hasNoAccessToExpense(UUID expenseId, UUID userId) {
    boolean isOwner = expenseRepository.findByIdAndUser_Id(expenseId, userId).isPresent();
    boolean isParticipant =
        !isOwner && expenseSplitRepository.existsByExpenseIdAndUserId(expenseId, userId);

    return !isOwner && !isParticipant;
  }
}
