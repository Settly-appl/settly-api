package pl.settly.settly_api.expenses.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import pl.settly.settly_api.expenses.model.Expense;
import pl.settly.settly_api.expenses.model.ExpenseItem;
import pl.settly.settly_api.expenses.model.ExpenseSplit;

@Mapper(componentModel = "spring")
public interface ExpenseMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "user", ignore = true)
  @Mapping(target = "project", ignore = true)
  @Mapping(target = "isScanned", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  Expense toExpense(CreateExpenseRequest request);

  @Mapping(source = "user.id", target = "userId")
  @Mapping(source = "project.id", target = "projectId")
  @Mapping(source = "date", target = "date")
  // Viewer-relative; filled in by ExpenseService.withSettlement (needs the caller's id).
  @Mapping(target = "splitCount", ignore = true)
  @Mapping(target = "settledCount", ignore = true)
  @Mapping(target = "settled", ignore = true)
  @Mapping(target = "canSettle", ignore = true)
  ExpenseResponse toExpenseResponse(Expense expense);

  @Mapping(source = "expense.id", target = "expenseId")
  @Mapping(source = "user.id", target = "userId")
  @Mapping(source = "user.displayName", target = "userDisplayName")
  @Mapping(source = "user.username", target = "userName")
  @Mapping(source = "expenseSplitType", target = "splitType")
  ExpenseSplitResponse toExpenseSplitResponse(ExpenseSplit expenseSplit);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "expense", ignore = true)
  ExpenseItem toExpenseItem(CreateExpenseItemRequest request);

  @Mapping(source = "expense.id", target = "expenseId")
  ExpenseItemResponse toExpenseItemResponse(ExpenseItem expenseItem);
}
